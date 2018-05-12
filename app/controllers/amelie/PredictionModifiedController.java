package controllers.amelie;

import com.fasterxml.jackson.databind.node.ArrayNode;
import play.mvc.Controller;
import play.mvc.Result;
import recommender.Predict;

/**
 * Created by Manoj on 7/7/2017.
 */
public class PredictionModifiedController extends Controller {

    static int TRAINING_SIZE = 90;

    public Result predictAssignee(String projectKey, String algorithm, int num, String scope) {
        boolean uAI = scope.contains("all") ? true : false;

        Predict predict = new Predict(projectKey, num, algorithm, uAI, TRAINING_SIZE);

        ArrayNode results = predict.predictRun();

        return ok(results);
    }
}
