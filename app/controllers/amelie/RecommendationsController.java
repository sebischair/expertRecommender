package controllers.amelie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import recommender.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/*
Classifies a text and responds with appropriate reommendations
 */

public class RecommendationsController extends Controller {

    int EXPERT_SIZE = 20;
    int NOVICE_SIZE = 15;

    public Result getRecommendations() {
        System.out.println(request().body().asJson());
        String projectKey = request().body().asJson().get("projectKey").asText();
        String textToClassify = request().body().asJson().get("textToClassify").asText();
        String algorithm = request().body().asJson().get("algorithm").asText();
        JsonNode alreadyAssignedExperts = request().body().asJson().get("alreadyAssignedNovices");

        Recommender recommender;
        List<LinkedHashMap<String, Integer>> assigneeSkills;
        ArrayNode explanationRecommendation;
        LinkedHashMap<String, Double> recommendationResult;
        try {
            recommender = RecommenderFactory.createRecommender(projectKey, algorithm);
            recommendationResult = recommender.getSingleResult(textToClassify);
            explanationRecommendation = recommender.getLastDetails();
            assigneeSkills = recommender.getAssigneeRank(recommendationResult, explanationRecommendation);
        } catch (Exception e) {
            return ok("Please select an algorithm");
        }

        ObjectNode result = Json.newObject();

        result.put("expertRecommendation", Time.getAssigneeTime(projectKey, recommendationResult));
        result.put("expertRecommendation", addSkills(result.get("expertRecommendation"), assigneeSkills));
        result.put("expertRecommendation", getRank(result.get("expertRecommendation")));
        result.put("noviceRecommendation", getNovices(result.get("expertRecommendation"), alreadyAssignedExperts));
        result.put("explanationRecommendation", explanationRecommendation);

        return ok(Json.toJson(result));
    }

    private ArrayNode addSkills(JsonNode result, List<LinkedHashMap<String, Integer>> skills) {
        ArrayNode results = Json.newArray();

        for (int i = 0; i < result.size(); i++) {
            ObjectNode resultJoined = (ObjectNode) result.get(i);
            if (skills.size() == result.size()) {
                ArrayNode resultSkills = Json.newArray();
                for (String conceptRank : skills.get(i).keySet()) {
                    ObjectNode resultSkill = Json.newObject();
                    resultSkill.put(conceptRank, skills.get(i).get(conceptRank));
                    resultSkills.add(resultSkill);
                }
                resultJoined.put("skill", resultSkills);
            }
            results.add(resultJoined);
        }

        return results;
    }

    public Result createKNNModels() {
        recommender.KNN.createKNNModels();
        return ok("done");
    }

    private ArrayNode getRank(JsonNode result) {
        ArrayNode resultRank = Json.newArray();
        for (int i = 0; i < result.size(); i++) {
            ObjectNode r = (ObjectNode) result.get(i);
            r.put("rank", i + 1);
            resultRank.add(r);
        }
        return resultRank;
    }

    private ArrayNode getNovices(JsonNode result, JsonNode alreadyAssignedExperts) {
        ArrayNode resultNovices = Json.newArray();

        Set<Integer> previousRandomNum = new HashSet();

        int actual_novice_size = EXPERT_SIZE + NOVICE_SIZE > result.size() + 1 ? result.size() - EXPERT_SIZE : NOVICE_SIZE;

        if (alreadyAssignedExperts.size() > 0) {
            alreadyAssignedExperts.forEach(expert -> {
                String expertName = expert.get("name").asText();
                for (JsonNode new_expert : result) {
                    if (new_expert.get("name").asText().equals(expertName)) {
                        resultNovices.add(new_expert);
                        break;
                    }
                }
            });
            actual_novice_size--;
        }

        if (result.size() > EXPERT_SIZE) {
            for (int i = 0; i < actual_novice_size; i++) {
                int randomNum = ThreadLocalRandom.current().nextInt(EXPERT_SIZE, result.size() + 1);
                if (!previousRandomNum.contains(randomNum)) {
                    resultNovices.add(result.get(randomNum));
                    previousRandomNum.add(randomNum);
                } else {
                    i--;
                }
            }
        }

        return resultNovices;
    }
}
