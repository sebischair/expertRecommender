package recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import util.StaticFunctions;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.matrix.Matrix;
import weka.core.matrix.SingularValueDecomposition;
import weka.core.stemmers.SnowballStemmer;
import weka.core.stopwords.MultiStopwords;
import weka.core.stopwords.Rainbow;
import weka.core.stopwords.StopwordsHandler;
import weka.core.tokenizers.NGramTokenizer;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.File;
import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static util.StaticFunctions.sortByValues;

public class LSA extends Recommender {

    String SAVE_LOCATION_MODELS = "./data/models/LSA/";
    static public int NGRAM_SIZE = 2;
    int WORDS_TO_KEEP = 1000;
    static public boolean STEMMING = false;

    String[] terms;
    double[] scales;
    double[][] termVectors;
    double[][] docVectors;

    public LSA(String projectKey, String scope, int trainingSize, int numTopics) {
        super(projectKey, scope, trainingSize, numTopics);
        runLSA();
        createAssigneeConceptOrTopicMatrix();
        this.modelName = projectKey + "_" + scope + "_" + trainingSize;
    }

    private void runLSA() {
        String filename = SAVE_LOCATION_MODELS + modelName
                + "_ngram_" + NGRAM_SIZE
                + "_wordstokeep_" + WORDS_TO_KEEP
                + "_stemming_" + STEMMING;
        File fTerms = new File(filename + "_terms.txt");
        File fScales = new File(filename + "_scales.txt");
        File fTermVectors = new File(filename + "_termVectors.txt");
        File fDocVectors = new File(filename + "_docVectors.txt");

        if (fScales.exists() && !fScales.isDirectory()) {
            terms = read1DStringArray(fTerms);
            scales = read1DDoubleArray(fScales);
            termVectors = read2DDoubleArray(fTermVectors);
            docVectors = read2DDoubleArray(fDocVectors);
        } else {
            ArrayList<Attribute> attributesList = new ArrayList<>();

            Attribute attributeConceptsPerIssue = new Attribute("content", (ArrayList<String>) null);

            attributesList.add(attributeConceptsPerIssue);

            Instances data = new Instances("Issues", attributesList, 1);

            for (String text : trainingTextList) {
                DenseInstance instance = new DenseInstance(1);
                instance.setValue(attributesList.get(0), text);
                data.add(instance);
            }

            // Set the tokenizer
            NGramTokenizer tokenizer = new NGramTokenizer();
            tokenizer.setNGramMinSize(1);
            tokenizer.setNGramMaxSize(NGRAM_SIZE);
            tokenizer.setDelimiters("\\W");

            SnowballStemmer stemmer = new SnowballStemmer();
            stemmer.setStemmer("porter");

            StringToWordVector stringToWordVector = new StringToWordVector();
            stringToWordVector.setTokenizer(tokenizer);
            StopwordsHandler st = new Rainbow();
            stringToWordVector.setStopwordsHandler(st);
            if (STEMMING) {
                stringToWordVector.setStemmer(stemmer);
            }
            stringToWordVector.setWordsToKeep(WORDS_TO_KEEP);
            stringToWordVector.setDoNotOperateOnPerClassBasis(true);
            stringToWordVector.setLowerCaseTokens(true);
            stringToWordVector.setIDFTransform(true);
            stringToWordVector.setTFTransform(false);

            Instances newData = null;
            try {
                stringToWordVector.setInputFormat(data);
                newData = Filter.useFilter(data, stringToWordVector);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
            System.out.println("Old attributes size:" + newData.numAttributes());
            RemoveUseless ru = new RemoveUseless();
            ru.setMaximumVariancePercentageAllowed(10);
            try {
                ru.setInputFormat(newData);
                newData = Filter.useFilter(newData, ru);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("New attributes size:" + newData.numAttributes());
            */

            double[][] term_matrix = new double[newData.size()][newData.numAttributes()];
            terms = new String[newData.numAttributes()];

            for (int i = 0; i < newData.size(); i++) {
                for (int j = 0; j < newData.numAttributes(); j++) {
                    term_matrix[i][j] = newData.get(i).value(j);
                }
            }

            for (int i = 0; i < newData.numAttributes(); i++) {
                terms[i] = newData.attribute(i).name();
            }

            Matrix svdMatrix = new Matrix(term_matrix);

            SingularValueDecomposition svd = new SingularValueDecomposition(svdMatrix);

            scales = svd.getSingularValues();
            termVectors = svd.getV().getArray();
            docVectors = svd.getU().getArray();

            printResults(terms, scales, termVectors, kOrNumTopics);
            write(terms, fTerms);
            write(scales, fScales);
            write(termVectors, fTermVectors);
            write(docVectors, fDocVectors);
        }
    }

    void createAssigneeConceptOrTopicMatrix() {
        for (int i = 0; i < docVectors.length; i++) {
            String assignee = trainingAssigneeList.get(i);

            double[] documentTopicScores = dotProductPerTopic(docVectors[i], scales, kOrNumTopics);
            // documentScores[j] = cosine(queryVector,docVectors[j],scales);

            for (int j = 0; j < documentTopicScores.length; j++) {
                LinkedHashMap<String, Double> assigneeMatrix = new LinkedHashMap<>();
                if (assigneeConceptOrTopicMatrix.containsKey(Integer.toString(j))) {
                    assigneeMatrix = assigneeConceptOrTopicMatrix.get(Integer.toString(j));
                }
                double score;
                if (assigneeMatrix.containsKey(assignee)) {
                    score = assigneeMatrix.get(assignee) + documentTopicScores[j];
                } else {
                    score = documentTopicScores[j];
                }
                assigneeMatrix.put(assignee, score);
                assigneeConceptOrTopicMatrix.put(Integer.toString(j), assigneeMatrix);
            }
        }
    }

    @Override
    public List<LinkedHashMap<String, Double>> runSearch(List<String> searchTexts) {

        List<LinkedHashMap<String, Double>> resultAssigneeList = new ArrayList<>();
        for (int i = 0; i < searchTexts.size(); i++) {
            resultAssigneeList.add(getSingleResult(searchTexts.get(i)));
        }

        return resultAssigneeList;
    }

    public LinkedHashMap<String, Double> getSingleResult(String searchTermsString) {
        if (STEMMING) {
            searchTermsString = stemmerMethod(searchTermsString);
        }
        List<String> searchTerms = new ArrayList<>();
        for (int i = 1; i <= NGRAM_SIZE; i++) {
            searchTerms.addAll(ngrams(i, searchTermsString));
        }

        double[] queryVector = new double[kOrNumTopics];
        Arrays.fill(queryVector, 0.0);

        for (String searchTerm : searchTerms) {
            addTermVector(searchTerm, termVectors, queryVector, terms, kOrNumTopics);
        }

        LinkedHashMap<String, Double> assigneeToIssue = new LinkedHashMap<>();
        Set<String> uniqueAssignees = new HashSet(trainingAssigneeList);
        for (String assignee : uniqueAssignees) {
            for (int j = 0; j < queryVector.length; j++) {
                double score;
                if (assigneeToIssue.containsKey(assignee)) {
                    score = assigneeToIssue.get(assignee) + assigneeConceptOrTopicMatrix.get(Integer.toString(j)).get(assignee) * queryVector[j];
                } else {
                    score = assigneeConceptOrTopicMatrix.get(Integer.toString(j)).get(assignee) * queryVector[j];
                }
                assigneeToIssue.put(assignee, score);
            }
        }

        assigneeToIssue = StaticFunctions.sortByValues(assigneeToIssue);

        return assigneeToIssue;
    }

    @Override
    public List<LinkedHashMap<String, Integer>> getAssigneeRank(LinkedHashMap<String, Double> assignees, ArrayNode topics) {
        return null;
    }

    @Override
    public ArrayNode getLastDetails() {
        return null;
    }

    private static void addTermVector(String term, double[][] termVectors, double[] queryVector, String[] terms, int numTopics) {
        for (int i = 0; i < terms.length; ++i) {
            if (terms[i].equals(term)) {
                for (int j = 0; j < numTopics; ++j) {
                    queryVector[j] += termVectors[i][j];
                }
                return;
            }
        }
    }

    private static double[] dotProductPerTopic(double[] ys, double[] scales, int k) {
        double[] documentTopicScores = new double[k];
        for (int i = 0; i < k; i++) {
            documentTopicScores[i] += ys[i] * scales[i];
        }
        return documentTopicScores;
    }

    private static double cosine(double[] xs, double[] ys, double[] scales) {
        double product = 0.0;
        double xsLengthSquared = 0.0;
        double ysLengthSquared = 0.0;
        for (int k = 0; k < xs.length; ++k) {
            double sqrtScale = Math.sqrt(scales[k]);
            double scaledXs = sqrtScale * xs[k];
            double scaledYs = sqrtScale * ys[k];
            xsLengthSquared += scaledXs * scaledXs;
            ysLengthSquared += scaledYs * scaledYs;
            product += scaledXs * scaledYs;
        }
        return product / Math.sqrt(xsLengthSquared * ysLengthSquared);
    }

    private static void printResults(String[] terms, double[] scales, double[][] termVectors, int k) {
        double[] k_scales = new double[k];
        int i = 0;
        while (i < k) {
            k_scales[i] = scales[i];
            i++;
        }

        double sum_scales = DoubleStream.of(k_scales).sum();
        for (i = 0; i < k_scales.length; i++) {
            double strengthTopic = scales[i] / sum_scales;
            LinkedHashMap<String, Double> wordsToTopic = new LinkedHashMap<>();
            for (int j = 0; j < terms.length; j++) {
                wordsToTopic.put(terms[j], termVectors[i][j]);
            }
            wordsToTopic = StaticFunctions.sortByValues(wordsToTopic);

            Formatter out = new Formatter(new StringBuilder(), Locale.US);
            out.format("%.8f\t", strengthTopic);
            int print = 0;
            for (String word : wordsToTopic.keySet()) {
                out.format(" %s,", word);
                print++;
                if (print > 20) {
                    break;
                }
            }
            System.out.println(out);
        }
    }

    private static List<String> ngrams(int n, String str) {
        List<String> ngrams = new ArrayList<>();
        String[] words = str.toLowerCase().split(" |,"); // space or comma separated
        for (int i = 0; i < words.length - n + 1; i++)
            ngrams.add(concat(words, i, i + n));
        return ngrams;
    }

    private static String concat(String[] words, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++)
            sb.append((i > start ? " " : "") + words[i]);
        return sb.toString();
    }
}