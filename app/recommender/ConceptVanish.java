package recommender;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class ConceptVanish extends Concept {

    public ConceptVanish(String projectKey, String scope, int trainingSize) {
        super(projectKey, scope, trainingSize);
    }

    static double getTimeFactor(LocalDate issueDate, LocalDate minDate) {
        return calculateTimeFactor(issueDate, minDate);
    }

    // Implementation: (date - min/0) / (max(first date - date now) - min(0))
    // min 0 = amount of days difference
    private static double calculateTimeFactor(LocalDate issueDate, LocalDate minDate) {
        double maxDifference = ChronoUnit.DAYS.between(minDate, LocalDate.now());
        double minDifference = 0;
        try {
            double differenceDateMin = ChronoUnit.DAYS.between(issueDate, LocalDate.now());
            return 1 - (differenceDateMin / (maxDifference - minDifference));
        } catch (DateTimeParseException ee) {
            return 0;
        }
    }
}
