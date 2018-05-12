package recommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import util.StaticFunctions;

import java.time.LocalDate;
import java.util.*;

import static util.StaticFunctions.sortByValues;

public class Concept extends Recommender {
    LocalDate minDate;

    HashMap<String, Integer> lastTextAmountConceptMap = new HashMap<>();

    public Concept(String projectKey, String scope, int trainingSize) {
        super(projectKey, scope, trainingSize, 0);
        getMinDate();
        createAssigneeConceptMatrix();
    }

    void getMinDate() {
        minDate = Collections.min(trainingDatesList);
    }

    public ArrayNode getLastDetails() {
        ArrayNode resultConcepts = Json.newArray();
        for (String concept : lastTextAmountConceptMap.keySet()){
            ObjectNode resultConcept = Json.newObject();
            resultConcept.put(concept, lastTextAmountConceptMap.get(concept));
            resultConcepts.add(resultConcept);
        }
        return resultConcepts;
    }

    // Creates the assignee matrix
    void createAssigneeConceptMatrix() {
        for (int i = 0; i < trainingConceptList.size(); i++) {
            // Select time factor depending on Object
            double timeFactor = 1;
            if (this instanceof ConceptVanish) {
                timeFactor = ConceptVanish.getTimeFactor(trainingDatesList.get(i), minDate);
            }

            String assignee = trainingAssigneeList.get(i);

            JsonNode concepts = trainingConceptList.get(i);

            for (JsonNode concept : concepts) {
                String c = concept.asText("").replaceAll("s$", "").toLowerCase();

                LinkedHashMap<String, Double> assigneeMatrix = new LinkedHashMap<>();
                if (assigneeConceptOrTopicMatrix.containsKey(c)) {
                    assigneeMatrix = assigneeConceptOrTopicMatrix.get(c);
                }
                double score = timeFactor;
                if (assigneeMatrix.containsKey(assignee)) {
                    score += assigneeMatrix.get(assignee);
                }
                assigneeMatrix.put(assignee, score);

                assigneeConceptOrTopicMatrix.put(c, assigneeMatrix);
            }
        }
    }

    // Runs search for a list of issues
    public List<LinkedHashMap<String, Double>> runSearch(List<String> searchTexts) {
        List<LinkedHashMap<String, Double>> resultTrainingAssigneeList = new ArrayList<>();
        for (int i = 0; i < searchTexts.size(); i++) {
            resultTrainingAssigneeList.add(getSingleResult(searchTexts.get(i), testIssues.get(i).get(StaticFunctions.CONCEPTS)));
        }

        return resultTrainingAssigneeList;
    }

    // Result for a single search
    public LinkedHashMap<String, Double> getSingleResult(String searchText, JsonNode concepts) {

        String text = searchText.trim().replaceAll(" +", " ").toLowerCase();
        HashMap<String, Integer> amountConceptMap = new HashMap<>();
        for (JsonNode concept : concepts) {
            String c = concept.asText("").replaceAll("s$", "").toLowerCase();
            if(uniqueConcepts.contains(c)) {
                int amountContained = StaticFunctions.getConceptValue(c, text);
                if (amountContained > 0) {
                    amountConceptMap.put(c, amountContained);
                }
            }
        }

        return createAssigneeList(amountConceptMap);
    }

    // Result for a single search
    public LinkedHashMap<String, Double> getSingleResult(String searchText) {
        String text = searchText.trim().replaceAll(" +", " ").toLowerCase();
        HashMap<String, Integer> amountConceptMap = new HashMap<>();
        for (String concept : uniqueConcepts) {
            int amountContained = StaticFunctions.getConceptValue(concept, text);
            if (amountContained > 0) {
                amountConceptMap.put(concept, amountContained);
            }
        }

        return createAssigneeList(amountConceptMap);
    }

    private LinkedHashMap<String, Double> createAssigneeList(HashMap<String, Integer> amountConceptMap) {
        lastTextAmountConceptMap = amountConceptMap;

        LinkedHashMap<String, Double> resultAssignee = new LinkedHashMap<>();

        for (String concept : amountConceptMap.keySet()) {
            HashMap<String, Double> assigneesConceptSkillMap = assigneeConceptOrTopicMatrix.get(concept);
            for (String assignee : assigneesConceptSkillMap.keySet()) {
                assigneesConceptSkillMap.get(assignee);
                double score = amountConceptMap.get(concept) * assigneesConceptSkillMap.get(assignee);
                if (resultAssignee.containsKey(assignee)) {
                    score += resultAssignee.get(assignee);
                }
                resultAssignee.put(assignee, score);
            }
        }

        for (String assignee : trainingAssigneeList) {
            if (!resultAssignee.containsKey(assignee)) {
                resultAssignee.put(assignee, 0.);
            }
        }

        return sortByValues(resultAssignee);
    }

    public List<LinkedHashMap<String, Integer>> getAssigneeRank(LinkedHashMap<String, Double> recommendationResult, ArrayNode trainingConceptList) {
        List<LinkedHashMap<String, Integer>> resultConceptRank = new ArrayList<>();

        for (String assignee : recommendationResult.keySet()) {
            LinkedHashMap<String, Integer> conceptRank = new LinkedHashMap<>();

            for (JsonNode conceptNode : trainingConceptList) {
                Iterator<Map.Entry<String, JsonNode>> nodes = conceptNode.fields();
                while (nodes.hasNext()) {
                    Map.Entry<String, JsonNode> entry = nodes.next();
                    String concept = entry.getKey();
                    int rank = -1;
                    if (assigneeConceptOrTopicMatrix.containsKey(concept) && assigneeConceptOrTopicMatrix.get(concept).containsKey(assignee)) {
                        rank = (new ArrayList<>(assigneeConceptOrTopicMatrix.get(concept).keySet())).indexOf(assignee) + 1;
                    }
                    conceptRank.put(concept, rank);
                }
            }

            resultConceptRank.add(conceptRank);
        }

        return resultConceptRank;
    }
}
