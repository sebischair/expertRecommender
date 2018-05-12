package recommender;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.amelie.Issue;
import play.libs.Json;
import util.StaticFunctions;

import java.util.*;

public class Time {

   static int MAX_ISSUES_SAME_TIME = 5;

    public static ArrayNode getAssigneeTime(String projectKey, LinkedHashMap<String, Double> recommendationResult) {
        Issue issueModel = new Issue();
        List<String> assigneeList = new ArrayList<>();
        assigneeList.addAll(recommendationResult.keySet());
        ArrayNode issues = issueModel.getOpenIssuesByAssignee(projectKey, assigneeList);

        HashMap<String, Integer> assigneesIssues = new HashMap<>();
        issues.forEach(issue -> {
            if (issue.has(StaticFunctions.ASSIGNEE) && issue.get(StaticFunctions.ASSIGNEE) != null) {
                String assignee = issue.get(StaticFunctions.ASSIGNEE).asText("");
                if (assigneesIssues.containsKey(assignee)) {
                    assigneesIssues.put(assignee, assigneesIssues.get(assignee) + 1);
                } else {
                    assigneesIssues.put(assignee, 1);
                }
            }
        });

        ArrayNode recommendationResultWithTime = Json.newArray();
        for (String assignee : recommendationResult.keySet()) {
            ObjectNode obj = Json.newObject();
            int assignedIssues = assigneesIssues.containsKey(assignee) ? assigneesIssues.get(assignee) : 0;
            obj.put("name", assignee);
            obj.put("result", recommendationResult.get(assignee));
            obj.put("time", assignedIssues >= MAX_ISSUES_SAME_TIME ? false : true);
            obj.put("openIssues", assignedIssues);
            recommendationResultWithTime.add(obj);
        }

        return recommendationResultWithTime;
    }
}
