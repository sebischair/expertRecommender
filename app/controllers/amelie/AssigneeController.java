package controllers.amelie;

import com.fasterxml.jackson.databind.node.ArrayNode;
import model.amelie.Issue;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import util.StaticFunctions;

import java.util.HashMap;

public class AssigneeController extends Controller {
    public Result getAssignees() {
        System.out.println(request().body().asJson());
        String projectKey = request().body().asJson().get("projectKey").asText();

        HashMap<String, String> assignees = new HashMap();

        Issue issueModel = new Issue();
        ArrayNode issues = issueModel.findAllDesignDecisionsInAProject(projectKey);

        issues.forEach(issue -> {
            if (issue.has(StaticFunctions.ASSIGNEE) && issue.get(StaticFunctions.ASSIGNEE) != null) {
                String assignee = issue.get(StaticFunctions.ASSIGNEE).asText("");
                if (!assignees.containsKey(assignee)) {
                    assignees.put(assignee, null);
                }
            }
        });

        return ok(Json.toJson(assignees));
    }
}
