package controllers.amelie;

import play.mvc.Result;
import recommender.LDA;
import recommender.LSA;
import recommender.Predict;

import static play.mvc.Results.ok;

public class PredictionRunController {

    public Result startPredictionRun() {
        String[] projectKeys = new String[]{"HADOOP"}; // "SPARK",
        boolean[] allIssues = new boolean[]{false};
        String[] algorithms = new String[]{"lsa"}; // "concept", "vanish", "knn", "lda"

        for (String algorithm : algorithms) {
            for (String projectKey : projectKeys) {
                for (boolean issues : allIssues) {
                    for (int kOrTopics = 191; kOrTopics <= 200; kOrTopics++) {
                        for (int trainingSize = 90; trainingSize < 100; trainingSize = trainingSize + 10) {
                            if (algorithm.equals("concept") || algorithm.equals("vanish")) {
                                kOrTopics = 200;
                            }
                            Predict predict = new Predict(projectKey, kOrTopics, algorithm, issues, trainingSize);
                            predict.predictRun();
                        }
                    }
                }
            }

        }

        return ok("Done");
    }

}
