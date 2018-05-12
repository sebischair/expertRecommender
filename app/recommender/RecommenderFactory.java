package recommender;

public class RecommenderFactory {

    static int DEFAULT_K = 20;
    static int DEFAULT_NUM_TOPICS = 92;
    static String DEFAULT_SCOPE = "dd";
    static int DEFAULT_TRAINING_SIZE = 90;

    public static Recommender createRecommender(String projectKey, String scope, int trainingSize, int kOrNumTopics, String type) throws Exception {
        switch (type) {
            case "lda":
                return new LDA(projectKey, scope, trainingSize, kOrNumTopics);
            case "lsa":
                return new LSA(projectKey, scope, trainingSize, kOrNumTopics);
            case "concept":
                return new Concept(projectKey, scope, trainingSize);
            case "vanish":
                return new ConceptVanish(projectKey, scope, trainingSize);
            case "knn":
                return new KNN(projectKey, scope, trainingSize, kOrNumTopics);
            default:
                throw new Exception();
        }
    }

    public static Recommender createRecommender(String projectKey, String type) throws Exception {
        int kOrNumTopics = type.equals("knn") ? DEFAULT_K : DEFAULT_NUM_TOPICS;
        return createRecommender(projectKey, DEFAULT_SCOPE, DEFAULT_TRAINING_SIZE, kOrNumTopics, type);
    }

}
