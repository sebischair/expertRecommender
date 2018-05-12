package recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.utils.Obj;
import model.amelie.Issue;
import org.json.JSONArray;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Result;
import util.StaticFunctions;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static play.mvc.Results.ok;

public class KNN extends Recommender {

    ArrayNode lastNeighborIssues = Json.newArray();

    public KNN(String projectKey, String scope, int trainingSize, int k) {
        super(projectKey, scope, trainingSize, k);
    }

    public List<LinkedHashMap<String, Double>> runSearch(List<String> textList) {
        List<LinkedHashMap<String, Double>> kNNResult = new ArrayList<>();

        for (String text : textList) {
            kNNResult.add(getSingleResult(text));
        }

        return kNNResult;
    }

    public LinkedHashMap<String, Double> getSingleResult(String issueDescription) {
        JsonNode jsonBody = Json.newObject()
                .put("pipelineName", modelName)
                .put("textToClassify", issueDescription);

        CompletionStage<JsonNode> jsonPromise = WS.url("http://localhost:9001/clustering/pipeline/predict")
                .setRequestTimeout(600000) //ms
                .setContentType("application/x-www-form-urlencoded")
                .post(jsonBody)
                .thenApplyAsync(response -> response.asJson());

        JsonNode jsonNode = null;
        try {
            jsonNode = jsonPromise.toCompletableFuture().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        List<String> documentIds = new ArrayList<>();
        if (jsonNode.has("result") && jsonNode.get("result") != null) {
            jsonNode.get("result").forEach(json -> {
                documentIds.add(json.get("_c0").asText(""));
            });
        }

        Issue issueModel = new Issue();
        ArrayNode neighborIssues = documentIds.size() > 0 ? issueModel.getDesignDecisionsByIds(projectKey, documentIds) : Json.newArray();
        LinkedHashMap<String, Double> assigneesNeighbor = new LinkedHashMap<>();
        for (JsonNode nI : neighborIssues) {
            if (nI.has(StaticFunctions.ASSIGNEE)) {
                String assignee = nI.get(StaticFunctions.ASSIGNEE).asText("");
                if (assigneesNeighbor.containsKey(assignee)) {
                    assigneesNeighbor.put(assignee, assigneesNeighbor.get(assignee) + 1);
                } else {
                    assigneesNeighbor.put(assignee, 1.);
                }
            }
        }
        lastNeighborIssues = neighborIssues;
        for (String assignee : trainingAssigneeList) {
            if (!assigneesNeighbor.containsKey(assignee)) {
                assigneesNeighbor.put(assignee, 0.);
            }
        }

        assigneesNeighbor = StaticFunctions.sortByValues(assigneesNeighbor);

        return (assigneesNeighbor);
    }

    public List<LinkedHashMap<String, Integer>> getAssigneeRank(LinkedHashMap<String, Double> recommendationResult, ArrayNode explanation) {
        List<LinkedHashMap<String, Integer>> resultSimilarRank = new ArrayList<>();

        for (String assignee : recommendationResult.keySet()) {
            LinkedHashMap<String, Integer> similarRank = new LinkedHashMap<>();

            similarRank.put("Similar issues solved", recommendationResult.get(assignee).intValue());
            resultSimilarRank.add(similarRank);
        }

        return resultSimilarRank;
    }

    public ArrayNode getLastDetails() {
        ArrayNode resultSimilar = Json.newArray();
        lastNeighborIssues.forEach(issue -> {
            ObjectNode result = Json.newObject();
            result.put(issue.get("name").asText() ,issue.get("summary").asText());
            resultSimilar.add(result);
        });

        return resultSimilar;
    }

    public static Result createKNNModels() {
        String[] mongoProjectKeys = new String[]{"HADOOP"};
        String[] optionScopes = new String[]{"dd"};

        String libraryName = "Apache Spark";
        String algorithmName = "KMeans";
        String algorithmId = "spark-kmeans";
        int libraryId = 1;
        int optionIterations = 10;

        for (String mongoProjectKey : mongoProjectKeys) {
            for (String optionScope : optionScopes) {
                for (int optionTraining = 40; optionTraining <= 90; optionTraining = optionTraining + 10) {
                    for (int optionK = 23; optionK <= 30; optionK++) {
                        if (optionScope.equals("all")) {
                           optionIterations = 30;
                        }
                        ObjectNode jsonBody = Json.newObject();
                        ObjectNode pipeline = Json.newObject();
                        ObjectNode library = Json.newObject();
                        ObjectNode scData = Json.newObject();
                        ObjectNode algorithm = Json.newObject();
                        ArrayNode algorithmOptions = Json.newArray();
                        ObjectNode optionKNode = Json.newObject();
                        ObjectNode optionIterationsNode = Json.newObject();
                        ObjectNode optionTrainingNode = Json.newObject();
                        ObjectNode optionScopeNode = Json.newObject();
                        ObjectNode transformer = Json.newObject();
                        String modelName = mongoProjectKey + "_" + optionScope + "_" + optionTraining + "_" + optionK;

                        library.put("name", libraryName);
                        pipeline.put("href", "/spark/train/pipeline/" + modelName);
                        pipeline.put("name", modelName);
                        library.put("name", libraryName);
                        library.put("id", libraryId);
                        pipeline.put("library", library);
                        pipeline.put("scLink", false);
                        scData.put("miningAttributes", Json.newArray());
                        pipeline.put("scData", scData);
                        pipeline.put("dataset", modelName);
                        algorithm.put("name", algorithmName);
                        algorithm.put("id", algorithmId);
                        optionKNode.put("name", "K-value");
                        optionKNode.put("value", optionK);
                        optionIterationsNode.put("name", "iterations");
                        optionIterationsNode.put("value", optionIterations);
                        optionTrainingNode.put("name", "training");
                        optionTrainingNode.put("value", optionTraining);
                        optionScopeNode.put("name", "scope");
                        optionScopeNode.put("value", optionScope);
                        algorithmOptions.add(optionKNode);
                        algorithmOptions.add(optionIterationsNode);
                        algorithmOptions.add(optionTrainingNode);
                        algorithmOptions.add(optionScopeNode);
                        algorithm.put("options", algorithmOptions);
                        pipeline.put("algorithm", algorithm);
                        pipeline.put("mongoProjectKey", mongoProjectKey);
                        transformer.put("id", "spark-word2vec");
                        pipeline.put("transformer", transformer);
                        jsonBody.put("pipeline", pipeline);

                        CompletionStage<JsonNode> jsonPromise = WS.url("http://localhost:9001/clustering/pipeline/create")
                                .setContentType("application/x-www-form-urlencoded")
                                .post(jsonBody)
                                .thenApplyAsync(response -> response.asJson());

                        try {
                            TimeUnit.SECONDS.sleep(240);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }

        return ok("KNN Models created");
    }
}
