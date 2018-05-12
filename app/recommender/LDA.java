package recommender;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.*;
import cc.mallet.util.FeatureCountTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.DoubleStream;

import static util.StaticFunctions.sortByValues;

public class LDA extends Recommender {

    String SAVE_LOCATION_MODELS = "./data/models/LDA/";
    double ALPHA = 0.1;
    double BETA = 0.1;
    static public int NGRAM_SIZE = 2;
    public static boolean STEMMING = false;

    InstanceList instanceList;
    InstanceList instanceListPruned;
    ParallelTopicModel model;
    List<double[]> topicProbabilityList = new ArrayList<>();
    double[] lastTopicDistribution;
    HashMap<String, Integer> topicKeyToId = new HashMap<>();

    public LDA(String projectKey, String scope, int trainingSize, int numTopics) {
        super(projectKey, scope, trainingSize, numTopics);
        createInstanceList();
        // minIDF Value tells in how many documents a word is allowed to appear
        // prune(0, 0, 0, 8);
        instanceListPruned = instanceList;
        createModel();
        createAssigneeTopicMatrix();
    }

    private void createInstanceList() {

        // Begin by importing documents from text to feature sequences
        ArrayList<Pipe> pipes = new ArrayList<Pipe>();

        pipes.add(new CharSequence2TokenSequence());
        pipes.add(new TokenSequenceLowercase());
        pipes.add(new TokenSequenceRemoveStopwords());
        int[] ngrams = new int[NGRAM_SIZE];
        for (int i = 1; i <= NGRAM_SIZE; i++) {
            ngrams[i - 1] = i;
        }
        pipes.add(new TokenSequenceNGrams(ngrams));
        pipes.add(new TokenSequence2FeatureSequence());

        InstanceList instanceList = new InstanceList(new SerialPipes(pipes));
        if (STEMMING) {
            trainingTextList = stemmerMethod(trainingTextList);
        }
        instanceList.addThruPipe(new ArrayIterator(trainingTextList));
        this.instanceList = instanceList;
    }

    private void createModel() {
        String filename = SAVE_LOCATION_MODELS + modelName + "_ngram_" + NGRAM_SIZE + "_stemming_" + STEMMING;
        File f = new File(filename);
        File fzip = new File(filename + ".gzip");
        ParallelTopicModel model = null;

        if (false) {
            try {
                decompressGzip(fzip, f);
                model = ParallelTopicModel.read(f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {

            //  Note that the alpha parameter is passed as the sum over topics, while
            //  beta is the parameter for a single dimension of the Dirichlet prior.

            model = new ParallelTopicModel(kOrNumTopics, ALPHA, BETA);
            model.addInstances(instanceListPruned);

            // Use two parallel samplers, which each look at one half the corpus and combine
            //  statistics after every iteration.
            model.setNumThreads(1);

            // Run the model for 50 iterations and stop (this is for testing only,
            //  for real applications, use 1000 to 2000 iterations)
            model.setNumIterations(1000);

            try {
                model.estimate();
                f.createNewFile();
                model.write(f);
                compressGZIP(f, fzip);
            } catch (IOException e) {
                e.printStackTrace();
            }


            // Show the words and topics in the first instance

            // The data alphabet maps word IDs to strings
            Alphabet dataAlphabet = instanceList.getDataAlphabet();

            FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
            LabelSequence topics = model.getData().get(0).topicSequence;

            Formatter out = new Formatter(new StringBuilder(), Locale.US);
            for (int position = 0; position < tokens.getLength(); position++) {
                out.format("%s-%d ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
            }
            System.out.println(out);

            // Get Topic probabilities
            for (int i = 0; i < trainingTextList.size(); i++) {
                double[] topicDistribution = model.getTopicProbabilities(i);
                topicProbabilityList.add(topicDistribution);
            }

            double[] topicDistribution = new double[kOrNumTopics];
            Arrays.fill(topicDistribution, 0.);
            for (double[] topicProbs : topicProbabilityList) {
                for (int i = 0; i < topicProbs.length; i++) {
                    topicDistribution[i] += topicProbs[i];
                }
            }

            double totalSumTopics = DoubleStream.of(topicDistribution).sum();
            for (int i = 0; i < topicDistribution.length; i++) {
                topicDistribution[i] = topicDistribution[i] / totalSumTopics;
            }

            // Show top 5 words in topics with proportions for the first document
            // Get an array of sorted sets of word ID/count pairs
            ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();

            for (int topic = 0; topic < kOrNumTopics; topic++) {
                Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();

                out = new Formatter(new StringBuilder(), Locale.US);
                out.format("%d\t%.3f\t", topic, topicDistribution[topic]);
                int rank = 0;
                while (iterator.hasNext() && rank < 20) {
                    IDSorter idCountPair = iterator.next();
                    out.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
                    rank++;
                }
                System.out.println(out);

            }

        }
        f.delete();

        this.model = model;
    }

    private void createAssigneeTopicMatrix() {
        for (int i = 0; i < trainingAssigneeList.size(); i++) {
            double[] topicProbability = topicProbabilityList.get(i);
            String assignee = trainingAssigneeList.get(i);

            for (int j = 0; j < topicProbability.length; j++) {
                LinkedHashMap<String, Double> newValue = new LinkedHashMap<>();
                if (assigneeConceptOrTopicMatrix.containsKey(Integer.toString(j))) {
                    newValue = assigneeConceptOrTopicMatrix.get(Integer.toString(j));
                    newValue.put(assignee, newValue.containsKey(assignee) ? newValue.get(assignee) + topicProbability[j] : topicProbability[j]);
                } else {
                    newValue.put(assignee, topicProbability[j]);
                }
                assigneeConceptOrTopicMatrix.put(Integer.toString(j), newValue);
            }
        }

        for (int i = 0; i < assigneeConceptOrTopicMatrix.size(); i++) {
            assigneeConceptOrTopicMatrix.put(Integer.toString(i), sortByValues(assigneeConceptOrTopicMatrix.get(Integer.toString(i))));
        }
    }

    public List<LinkedHashMap<String, Double>> runSearch(List<String> searchTexts) {
        List<LinkedHashMap<String, Double>> resultAssigneeList = new ArrayList<>();

        if (STEMMING) {
            searchTexts = stemmerMethod(searchTexts);
        }
        List<double[]> topicProbabilityList = getTopicProbability(searchTexts);
        for (double[] topicProbability : topicProbabilityList) {
            LinkedHashMap<String, Double> resultCategoryAssignee = new LinkedHashMap<>();
            for (int j = 0; j < topicProbability.length; j++) {
                LinkedHashMap<String, Double> personTopics = assigneeConceptOrTopicMatrix.get(Integer.toString(j));
                for (String personName : personTopics.keySet()) {
                    double newValue;
                    if (resultCategoryAssignee.containsKey(personName)) {
                        newValue = resultCategoryAssignee.get(personName) + personTopics.get(personName) * topicProbability[j];
                    } else {
                        newValue = personTopics.get(personName) * topicProbability[j];
                    }
                    resultCategoryAssignee.put(personName, newValue);
                }
            }

            resultCategoryAssignee = sortByValues(resultCategoryAssignee);
            resultAssigneeList.add(resultCategoryAssignee);
        }
        return resultAssigneeList;
    }

    public LinkedHashMap<String, Double> getSingleResult(String searchText) {
        List<String> searchTexts = new ArrayList<>();
        searchTexts.add(searchText);
        return runSearch(searchTexts).get(0);
    }

    public ArrayNode getLastDetails() {
        LinkedHashMap<String, Double> topicProbabilities = new LinkedHashMap<>();
        Alphabet alphabet = model.getAlphabet();
        int topicNum = 0;
        for (TreeSet<IDSorter> set : model.getSortedWords()) {
            double proba = lastTopicDistribution[topicNum];
            int i = 0;
            String[] stringTopic = new String[6];
            for (IDSorter s : set) {
                stringTopic[i] = " " + alphabet.lookupObject(s.getID()).toString();
                i++;
                if (i >= 5) {
                    stringTopic[5] = "...";
                    break;
                }
            }
            String topic = "Topic #" + topicNum + ":" + String.join(",", stringTopic);
            topicKeyToId.put(topic, topicNum);
            topicProbabilities.put(topic, proba);
            topicNum++;
        }

        topicProbabilities = sortByValues(topicProbabilities);

        ArrayNode resultTopicProbabilities = Json.newArray();
        for (String key : topicProbabilities.keySet()) {
            ObjectNode on = Json.newObject();
            on.put(key, topicProbabilities.get(key));
            resultTopicProbabilities.add(on);
        }

        return resultTopicProbabilities;
    }

    public List<LinkedHashMap<String, Integer>> getAssigneeRank(LinkedHashMap<String, Double> recommendationResult, ArrayNode topics) {
        List<LinkedHashMap<String, Integer>> resultTopicRank = new ArrayList<>();

        for (String assignee : recommendationResult.keySet()) {
            LinkedHashMap<String, Integer> topicRank = new LinkedHashMap<>();

            int i = 0;
            for (JsonNode topic : topics) {
                Iterator<String> it = topic.fieldNames();
                while (it.hasNext()) {
                    String topicKey = it.next();
                    int rank = -1;
                    int id = topicKeyToId.get(topicKey);
                    if (assigneeConceptOrTopicMatrix.containsKey(Integer.toString(id)) && assigneeConceptOrTopicMatrix.get(Integer.toString(id)).containsKey(assignee)) {
                        rank = (new ArrayList<>(assigneeConceptOrTopicMatrix.get(Integer.toString(id)).keySet())).indexOf(assignee) + 1;
                    }
                    topicRank.put("Topic " + id, rank);
                }
                i++;
                if (i >= 5) {
                    break;
                }
            }

            resultTopicRank.add(topicRank);
        }

        return resultTopicRank;
    }

    // for test get topics per issue
    private List<double[]> getTopicProbability(List<String> searchTexts) {
        List<double[]> topicProbabilityList = new ArrayList<>();

        // Create a new instance named "test instance" with empty target and source fields.
        InstanceList apply = new InstanceList(instanceList.getPipe());
        apply.addThruPipe(new ArrayIterator(searchTexts));

        TopicInferencer inferencer = model.getInferencer();

        for (int i = 0; i < apply.size(); i++) {
            double[] resultProbabilities = inferencer.getSampledDistribution(apply.get(i), 30, 1, 5);
            topicProbabilityList.add(resultProbabilities);
        }
        lastTopicDistribution = topicProbabilityList.get(topicProbabilityList.size() - 1);

        return topicProbabilityList;
    }

    private void prune(int pruneCountValue, int pruneDocFreqValue, double maxIDFValue, double minIDFValue) {
        boolean pruneCountInvoked = pruneCountValue != 0;
        boolean pruneDocFreqInvoked = pruneDocFreqValue != 0;
        boolean maxIDFInvoked = maxIDFValue != 0;
        boolean minIDFInvoked = minIDFValue != 0;
        FeatureCountTool counter = new FeatureCountTool(instanceList);
        counter.count();
        counter.printCounts();

        int minDocs = 0;
        int maxDocs = Integer.MAX_VALUE;
        int minCount = 0;
        int maxCount = Integer.MAX_VALUE;

        if (pruneCountInvoked) {
            minCount = pruneCountValue;
        }
        if (pruneDocFreqInvoked) {
            minDocs = pruneDocFreqValue;
            System.out.println("min docs: " + minDocs);
        }
        if (maxIDFInvoked) {
            minDocs = (int) Math.floor(instanceList.size() * Math.exp(-maxIDFValue));
        }
        if (minIDFInvoked) {
            maxDocs = (int) Math.ceil(instanceList.size() * Math.exp(-minIDFValue));
            System.out.println("max docs: " + maxDocs);
        }

        Alphabet oldAlphabet = instanceList.getDataAlphabet();
        Alphabet newAlphabet = counter.getPrunedAlphabet(minDocs, maxDocs, minCount, maxCount);

        // Check which type of data element the instances contain
        Instance firstInstance = instanceList.get(0);
        if (firstInstance.getData() instanceof FeatureSequence) {
            // Version for feature sequences

            // It's necessary to create a new instance list in
            //  order to make sure that the data alphabet is correct.
            Noop newPipe = new Noop(newAlphabet, instanceList.getTargetAlphabet());
            InstanceList newInstanceList = new InstanceList(newPipe);

            // Iterate over the instances in the old list, adding
            //  up occurrences of features.
            int numFeatures = oldAlphabet.size();
            double[] counts = new double[numFeatures];
            for (int ii = 0; ii < instanceList.size(); ii++) {
                Instance instance = instanceList.get(ii);
                FeatureSequence fs = (FeatureSequence) instance.getData();

                fs.addFeatureWeightsTo(counts);
            }

            Instance instance, newInstance;

            // Next, iterate over the same list again, adding
            //  each instance to the new list after pruning.
            while (instanceList.size() > 0) {
                instance = instanceList.get(0);
                FeatureSequence fs = (FeatureSequence) instance.getData();

                fs.prune(newAlphabet);

                newInstanceList.add(newPipe.instanceFrom(new Instance(fs, instance.getTarget(),
                        instance.getName(),
                        instance.getSource())));
                instanceList.remove(0);
            }

            System.out.println("features: " + oldAlphabet.size() + " -> " + newAlphabet.size());

            // Make the new list the official list.
            this.instanceListPruned = newInstanceList;

        } else if (firstInstance.getData() instanceof FeatureVector) {
            // Version for FeatureVector

            Alphabet alpha2 = new Alphabet();
            Noop pipe2 = new Noop(alpha2, instanceList.getTargetAlphabet());
            InstanceList instances2 = new InstanceList(pipe2);
            int numFeatures = oldAlphabet.size();
            double[] counts = new double[numFeatures];

            BitSet bs = new BitSet(numFeatures);

            for (int feature = 0; feature < numFeatures; feature++) {
                if (newAlphabet.contains(oldAlphabet.lookupObject(feature))) {
                    bs.set(feature);
                }
            }

            System.out.println("Pruning " + (numFeatures - bs.cardinality()) + " features out of " + numFeatures
                    + "; leaving " + (bs.cardinality()) + " features.");

            FeatureSelection fs = new FeatureSelection(oldAlphabet, bs);

            for (int ii = 0; ii < instanceList.size(); ii++) {

                Instance instance = instanceList.get(ii);
                FeatureVector fv = (FeatureVector) instance.getData();
                FeatureVector fv2 = FeatureVector.newFeatureVector(fv, alpha2, fs);

                instances2.add(new Instance(fv2, instance.getTarget(), instance.getName(), instance.getSource()),
                        instanceList.getInstanceWeight(ii));
                instance.unLock();
                instance.setData(null); // So it can be freed by the garbage collector
            }
            this.instanceListPruned = instances2;
        } else {
            throw new UnsupportedOperationException("Pruning features from " +
                    firstInstance.getClass().getName() +
                    " is not currently supported");
        }
    }
}
