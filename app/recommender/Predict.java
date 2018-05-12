package recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import util.StaticFunctions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static util.StaticFunctions.writeToFile;

/*
Calculates based on various parameter accuracy of recommendations for different recommendation amounts
 */

public class Predict {

    String projectKey;
    int num;
    String type;
    boolean uAI;
    int trainingSize = 90;
    String runNameAddition = "";

    public Predict(String projectKey, int num, String type, boolean uAI, int trainingSize, String runNameAddition) {
        this(projectKey, num, type, uAI, trainingSize);
        this.runNameAddition = runNameAddition;
    }

    public Predict(String projectKey, int num, String type, boolean uAI, int trainingSize) {
        this.projectKey = projectKey;
        this.num = num;
        this.uAI = uAI;
        this.trainingSize = trainingSize;
        this.type = type;
    }


    public ArrayNode predictRun() {
        System.out.println(projectKey);

        ArrayNode ja = Json.newArray();
        ArrayNode results = Json.newArray();
        List<String> conceptList = new ArrayList<>();
        ObjectNode summaryResult = Json.newObject();
        ArrayNode testingData = Json.newArray();
        Set<String> allExpertsInDataSet = new HashSet<>();

        String scope = uAI ? "all" : "dd";

        List<LinkedHashMap<String, Double>> resultList = new ArrayList<>();
        Recommender recommender = null;
        List<String> testingTextList;
        try {
            recommender = RecommenderFactory.createRecommender(projectKey, scope, trainingSize, num, type);
            recommender.createTestData(testingData);
            testingTextList = recommender.testTextList;
            resultList = recommender.runSearch(testingTextList);
        } catch (Exception e) {
            e.printStackTrace();
        }

        summaryResult.put("Training dataset size", recommender.trainingTextList.size());

        allExpertsInDataSet.addAll(recommender.trainingAssigneeList);
        summaryResult.put("Testing dataset size", testingData.size());

        StaticFunctions.removeItemsFromJSONArray(ja, StaticFunctions.getItemsToRemove(ja));
        ArrayNode pcvja = Json.newArray();
        ja.forEach(jo -> {
            ObjectNode pcvjo = Json.newObject();
            pcvjo.put(StaticFunctions.PERSONNAME, jo.get(StaticFunctions.PERSONNAME).asText(""));
            ArrayNode pcvList = Json.newArray();
            for (int j = 0; j < conceptList.size(); j++) {
                pcvList.insert(j, personConceptValue(conceptList.get(j), jo));
            }
            pcvjo.set("pcvList", pcvList);
            pcvja.add(pcvjo);
        });

        ArrayNode decisionsToPredict = getRandomConceptVectors(conceptList, recommender.testIssues);
        decisionsToPredict = matching(pcvja, decisionsToPredict, conceptList.size());
        decisionsToPredict = ordering(decisionsToPredict);

        int correctMatch = 0;
        for (int n = 0; n < decisionsToPredict.size(); n++) {
            JsonNode dtp = decisionsToPredict.get(n);
            ArrayNode pa = Json.newArray();
            ArrayNode qa = Json.newArray();
            ObjectNode r = Json.newObject();
            String assignee = dtp.get(StaticFunctions.ASSIGNEE).asText("").toLowerCase();
            r.put("text", dtp.get(StaticFunctions.SUMMARY).asText("") + " " + dtp.get(StaticFunctions.DESCRIPTION).asText(""));
            r.put(StaticFunctions.ASSIGNEE, assignee);
            r.put("resolved", dtp.get("resolved").asText(""));
            ObjectNode jo;

            for (String personName : resultList.get(n).keySet()) {
                jo = Json.newObject();
                double score = resultList.get(n).get(personName);
                jo.put(StaticFunctions.PERSONNAME, personName.toLowerCase());

                jo.put("score", score);
                if (pa.size() < 30) {
                    pa.add(jo);
                }
                if (personName.toLowerCase().equals(assignee.toLowerCase())) correctMatch += 1;
            }


            summaryResult.put("correctMatch", correctMatch);
            r.set("predictions", pa);
            r.set("notime", qa);
            results.add(r);
        }

        System.out.println(allExpertsInDataSet.size());

        ArrayNode correctMatches = Json.newArray();
        ArrayNode catalogCoverages = Json.newArray();
        Set<String> allRecommendedExpertsInTestingSet = new HashSet<>();
        for (int k = 1; k < 15; k++) {
            correctMatches.add(computeCorrectMatch(results, k));
            catalogCoverages.add(allRecommendedExpertsInTestingSet.size() / allExpertsInDataSet.size());
            allRecommendedExpertsInTestingSet = new HashSet<>();
        }
        correctMatches.add(computeCorrectMatch(results, 0));
        catalogCoverages.add(allRecommendedExpertsInTestingSet.size() / allExpertsInDataSet.size());

        summaryResult.set("catalogCoverages", catalogCoverages);

        int testingDataSetForWhichPredictionsWereMade = 0;
        for (int n = 0; n < decisionsToPredict.size(); n++) {
            JsonNode dtp = decisionsToPredict.get(n);
            JsonNode dtp_pa = dtp.get("predictionArray");
            if (dtp_pa.size() > 0) {
                testingDataSetForWhichPredictionsWereMade += 1;
            }
        }
        summaryResult.put("predictionCoverage", testingDataSetForWhichPredictionsWereMade / testingData.size());
        summaryResult.set("correctMatch", correctMatches);
        summaryResult.put("percentageMatches", calcPercentage(correctMatches, testingData.size()));

        summaryResult.put("modelName", projectKey + "_" + scope + "_" + num + "_" + type + "_" + trainingSize + "%" + runNameAddition);
        results.add(summaryResult);

        writeToFile(summaryResult, "data/results/results.csv");
        return results;
    }

    private static int computeCorrectMatch(ArrayNode results, int run) {
        int correctMatch = 0;
        for (int n = 0; n < results.size(); n++) {
            JsonNode dtp = results.get(n);
            String assignee = dtp.get(StaticFunctions.ASSIGNEE).asText("").toLowerCase();
            JsonNode dtp_pa = dtp.get("predictions");
            int max = dtp_pa.size();
            if (run != 0) {
                max = dtp_pa.size() > (run * 2) ? (run * 2) : dtp_pa.size();
                //max = dtp_pa.size() > (run * 5) ? (run * 5) : dtp_pa.size();
            }
            for (int j = 0; j < max; j++) {
                JsonNode dtp_jo = dtp_pa.get(j);
                String personName = dtp_jo.get(StaticFunctions.PERSONNAME).asText("").toLowerCase();
                if (personName.toLowerCase().equals(assignee.toLowerCase())) correctMatch += 1;
            }
        }
        return correctMatch;
    }

    private static ArrayNode calcPercentage(ArrayNode correctMatches, int testSize) {
        ArrayNode percentageMatches = Json.newArray();
        for (JsonNode correctMatch : correctMatches) {
            percentageMatches.add(correctMatch.asDouble() / testSize);
        }

        return percentageMatches;
    }

    private static ArrayNode ordering(ArrayNode decisionsToPredict) {
        decisionsToPredict.forEach(decisionsToPredictItr -> {
            JsonNode predArray = decisionsToPredictItr.get("predictionArray");
            for (int j = 0; j < predArray.size(); j++) {
                double score = 0;
                JsonNode pcvList = predArray.get(j).get("pcvList");
                for (int k = 0; k < pcvList.size(); k++) {
                    score += pcvList.get(k).asDouble(0);
                }
                ((ObjectNode) predArray.get(j)).put("score", score);
            }

            ArrayNode newPredArray = sort(predArray);
            ((ObjectNode) decisionsToPredictItr).set("predictionArray", newPredArray);
        });

        return decisionsToPredict;
    }

    private static ArrayNode sort(JsonNode predArray) {
        List<JsonNode> jsonValues = new ArrayList<>();
        predArray.forEach(jsonValues::add);

        Collections.sort(jsonValues, new Comparator<JsonNode>() {
            private static final String KEY_NAME = "score";

            @Override
            public int compare(JsonNode a, JsonNode b) {
                Double valA = a.get(KEY_NAME).asDouble(0);
                Double valB = b.get(KEY_NAME).asDouble(0);

                return -valA.compareTo(valB);
            }
        });

        return Json.newArray().addAll(jsonValues);
    }

    private static ArrayNode matching(ArrayNode pcvja, ArrayNode decisionsToPredict, int conceptSize) {
        decisionsToPredict.forEach(decisionsToPredictItr -> {
            ArrayNode predictionArray = Json.newArray();
            JsonNode cv = decisionsToPredictItr.get("conceptVector");
            pcvja.forEach(pcvjo -> {
                boolean isApplicable = false;
                ObjectNode newpcvjo = Json.newObject();
                newpcvjo.put("personName", pcvjo.get("personName").asText(""));
                JsonNode pcvList = pcvjo.get("pcvList");
                ArrayNode newpcvList = Json.newArray();
                for (int k = 0; k < conceptSize; k++) {
                    newpcvList.insert(k, 0);
                    if (cv.get(k).asDouble(0) > 0 && pcvList.get(k).asDouble(0) > 0) {
                        newpcvList.insert(k, (cv.get(k).asDouble(0) * pcvList.get(k).asDouble(0)));
                        isApplicable = true;
                    }
                }
                if (isApplicable) {
                    newpcvjo.set("pcvList", newpcvList);
                    predictionArray.add(newpcvjo);
                }
            });

            ((ObjectNode) decisionsToPredictItr).set("predictionArray", predictionArray);
        });
        return decisionsToPredict;
    }

    private static ArrayNode getRandomConceptVectors(List<String> conceptList, List<ObjectNode> testingData) {
        ArrayNode conceptVectorJSONArray = Json.newArray();
        testingData.forEach(issue -> {
            ObjectNode conceptVectorJSONObject = Json.newObject();
            conceptVectorJSONObject.put(StaticFunctions.SUMMARY, issue.get(StaticFunctions.SUMMARY).asText(""));
            conceptVectorJSONObject.put(StaticFunctions.DESCRIPTION, issue.get(StaticFunctions.DESCRIPTION).asText(""));
            conceptVectorJSONObject.put(StaticFunctions.ASSIGNEE, issue.get(StaticFunctions.ASSIGNEE).asText(""));
            conceptVectorJSONObject.put("resolved", issue.get("resolved").asText(""));

            ArrayNode conceptVector = Json.newArray();
            for (int k = 0; k < conceptList.size(); k++) {
                conceptVector.insert(k, 0);
            }
            JsonNode concepts = issue.get(StaticFunctions.CONCEPTS);
            conceptVectorJSONObject.set(StaticFunctions.CONCEPTS, concepts);
            concepts.forEach(concept -> {
                String c = concept.asText("").replaceAll("s$", "").toLowerCase();
                if (conceptList.contains(c)) {
                    int value = getConceptValue(c, issue.get(StaticFunctions.SUMMARY).asText("") + " " + issue.get(StaticFunctions.DESCRIPTION).asText(""));
                    conceptVector.insert(conceptList.indexOf(concept), value);
                }
            });

            conceptVectorJSONObject.set("conceptVector", conceptVector);
            conceptVectorJSONArray.add(conceptVectorJSONObject);
        });
        return conceptVectorJSONArray;
    }

    private static int getConceptValue(String concept, String s) {
        s = s.replaceAll("\\(", "").replaceAll("\\)", "");
        int i = 0;
        Pattern p = Pattern.compile(concept.toLowerCase());
        Matcher m = p.matcher(s.toLowerCase());
        while (m.find()) {
            i++;
        }
        return i;
    }

    private static double personConceptValue(String concept, JsonNode jo) {
        JsonNode co;
        JsonNode ca = jo.get(StaticFunctions.CONCEPTS);
        for (int k = 0; k < ca.size(); k++) {
            co = ca.get(k);
            if (co.get("conceptName").asText("").equalsIgnoreCase(concept)) {
                return co.get("value").asDouble();
            }
        }
        return 0;
    }
}
