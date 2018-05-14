package recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.amelie.Issue;
import play.libs.Json;
import util.StaticFunctions;
import weka.core.stemmers.SnowballStemmer;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class Recommender {

    public abstract LinkedHashMap<String, Double> getSingleResult(String searchText);

    public abstract List<LinkedHashMap<String, Integer>> getAssigneeRank(LinkedHashMap<String, Double> assignees, ArrayNode topics);

    public abstract ArrayNode getLastDetails();

    public abstract List<LinkedHashMap<String, Double>> runSearch(List<String> searchTexts);

    static int DEFAULT_TRAINING_SIZE = 60;
    static String DEFAULT_SCOPE = "dd";

    protected int kOrNumTopics;
    int trainingSize;
    String modelName;
    String projectKey;
    String scope;
    List<String> trainingTextList = new ArrayList<>();
    List<String> trainingAssigneeList = new ArrayList<>();
    List<JsonNode> trainingConceptList = new ArrayList<>();
    List<LocalDate> trainingDatesList = new ArrayList<>();
    List<String> testTextList = new ArrayList<>();
    List<ObjectNode> testIssues = new ArrayList<>();
    Set<String> uniqueConcepts = new HashSet();
    HashMap<String, LinkedHashMap<String, Double>> assigneeConceptOrTopicMatrix = new HashMap<>();

    public Recommender(String projectKey, int kOrNumTopics) {
        this(projectKey, DEFAULT_SCOPE, DEFAULT_TRAINING_SIZE, kOrNumTopics);
    }

    public Recommender(String projectKey, String scope, int trainingSize, int kOrNumTopics) {
        Issue issueModel = new Issue();
        ArrayNode issues = issueModel.findAllDesignDecisionsForPredictionInAProject(projectKey);
        List<ObjectNode> orderedIssues = issueModel.orderIssuesByResolutionDate(issues);
        int trainingDataSetSize = (int) Math.floor(issues.size() * (trainingSize / 100.));

        List<ObjectNode> trainingIssues;
        if (scope.equals("all")) {
            ObjectNode lastTrainingIssue = orderedIssues.get(trainingDataSetSize);
            issues = issueModel.findAllIssuesInAProject(projectKey);
            orderedIssues = issueModel.orderIssuesByResolutionDate(issues);
            int index = -1;
            for (ObjectNode issue : orderedIssues) {
                index++;
                if (issue.has("name") && issue.get("name").asText().equals(lastTrainingIssue.get("name").asText())) {
                    break;
                }
            }
            trainingIssues = orderedIssues.subList(0, index);
        } else {
            trainingIssues = orderedIssues.subList(0, trainingDataSetSize);
        }

        trainingIssues.forEach(issue -> {
            if (issue.has(StaticFunctions.ASSIGNEE) && issue.has(StaticFunctions.SUMMARY)
                    && issue.has(StaticFunctions.DESCRIPTION)
                    && issue.has(StaticFunctions.RESOLUTION_DATE)
                    && issue.get(StaticFunctions.ASSIGNEE) != null && issue.get(StaticFunctions.SUMMARY) != null
                    && issue.get(StaticFunctions.DESCRIPTION) != null
                    && issue.get(StaticFunctions.RESOLUTION_DATE) != null && issue.get(StaticFunctions.RESOLUTION_DATE).asText() != "") {
                trainingTextList.add(issue.get(StaticFunctions.SUMMARY).asText("") + " " + issue.get(StaticFunctions.DESCRIPTION).asText(""));
                trainingAssigneeList.add(issue.get(StaticFunctions.ASSIGNEE).asText(""));
                trainingDatesList.add(LocalDate.parse(issue.get(StaticFunctions.RESOLUTION_DATE).asText(""), StaticFunctions.DATE_FORMAT));

                ArrayNode empty_trainingConceptList = Json.newArray();
                empty_trainingConceptList.add("");
                if (issue.has(StaticFunctions.CONCEPTS) && issue.get(StaticFunctions.CONCEPTS) != null) {
                    trainingConceptList.add(issue.get(StaticFunctions.CONCEPTS));
                } else {
                    trainingConceptList.add(empty_trainingConceptList);
                }
            }
        });


        // Unique trainingConceptList
        for (JsonNode conceptNode : trainingConceptList) {
            for (int i = 0; i < conceptNode.size(); i++) {
                String concept = conceptNode.get(i).asText("").replaceAll("s$", "").toLowerCase();
                uniqueConcepts.add(concept);
            }
        }

        this.projectKey = projectKey;
        this.trainingSize = trainingSize;
        this.scope = scope;
        this.kOrNumTopics = kOrNumTopics;
        this.modelName = projectKey + "_" + scope + "_" + trainingSize + "_" + kOrNumTopics;
        System.out.println(this.modelName);
    }

    // create training and test data for DD
    public ArrayNode createTestData(ArrayNode testingData) {

        Issue issueModel = new Issue();

        ArrayNode issues = issueModel.findAllDesignDecisionsForPredictionInAProject(projectKey);
        List<ObjectNode> orderedIssues = issueModel.orderIssuesByResolutionDate(issues);

        int trainingDataSetSize = (int) Math.floor(issues.size() * (trainingSize / 100.));

        List<ObjectNode> testIssuesAll = orderedIssues.subList(trainingDataSetSize, orderedIssues.size());

        // just for DD
        testIssuesAll.forEach(issue -> {
            if (issue.has("designDecision") && issue.has(StaticFunctions.ASSIGNEE) &&
                    issue.get("designDecision").asBoolean(false)) {
                testIssues.add(issue);
                String assignee = issue.get(StaticFunctions.ASSIGNEE).asText("").toLowerCase();
                String summary = issue.get(StaticFunctions.SUMMARY).asText("").toLowerCase().trim().replaceAll(" +", " ");
                String description = issue.get(StaticFunctions.DESCRIPTION).asText("").toLowerCase().trim().replaceAll(" +", " ");
                JsonNode trainingConceptList = issue.get(StaticFunctions.CONCEPTS);

                testTextList.add(summary + description);

                if (assignee != "" && assignee != "unassigned" && summary + description != "" && trainingConceptList.size() > 0) {
                    ObjectNode jo = Json.newObject();
                    jo.put(StaticFunctions.ASSIGNEE, assignee.toLowerCase());
                    jo.put(StaticFunctions.CONCEPTS, issue.get(StaticFunctions.CONCEPTS));
                    jo.put(StaticFunctions.SUMMARY, summary.toLowerCase());
                    jo.put(StaticFunctions.DESCRIPTION, description.toLowerCase());
                    jo.put("resolved", issue.get("resolved").asText(""));
                    testingData.add(jo);
                }
            }
        });

        return testingData;
    }

    List<String> stemmerMethod(List<String> textList) {
        List<String> processedTraining = new ArrayList<>();
        for (String text : textList) {
            processedTraining.add(stemmerMethod(text));
        }
        return processedTraining;
    }

    String stemmerMethod(String text) {
        SnowballStemmer stem = new SnowballStemmer("porter");
        return stem.stem(text);
    }

    static void compressGZIP(File input, File output) throws IOException {
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(output))) {
            try (FileInputStream in = new FileInputStream(input)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        }
    }

    static void decompressGzip(File input, File output) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new FileInputStream(input))) {
            try (FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        }
    }

    static void write(Object obj, File f) {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(f))) {
            outputStream.writeObject(obj);
            compressGZIP(f, new File(f.getPath() + ".gzip"));
        } catch (IOException ee) {
            ee.printStackTrace();
        }
        f.delete();
    }

    static double[] read1DDoubleArray(File f) {
        double[] array = null;
        try {
            decompressGzip(new File(f.getPath() + ".gzip"), f);
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(f));
            array = (double[]) inputStream.readObject();
            inputStream.close();
        } catch (IOException ee) {
            ee.printStackTrace();
        } catch (ClassNotFoundException ee) {
            ee.printStackTrace();
        }
        f.delete();
        return array;
    }

    static double[][] read2DDoubleArray(File f) {
        double[][] array = null;
        try {
            decompressGzip(new File(f.getPath() + ".gzip"), f);
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(f));
            array = (double[][]) inputStream.readObject();
            inputStream.close();
        } catch (IOException ee) {
            ee.printStackTrace();
        } catch (ClassNotFoundException ee) {
            ee.printStackTrace();
        }
        f.delete();
        return array;
    }

    static String[] read1DStringArray(File f) {
        String[] array = null;
        try {
            decompressGzip(new File(f.getPath() + ".gzip"), f);
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(f));
            array = (String[]) inputStream.readObject();
            inputStream.close();
        } catch (IOException ee) {
            ee.printStackTrace();
        } catch (ClassNotFoundException ee) {
            ee.printStackTrace();
        }
        f.delete();
        return array;
    }
}
