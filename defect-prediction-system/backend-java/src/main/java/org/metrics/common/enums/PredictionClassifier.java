package org.metrics.common.enums;

import java.util.Locale;

public enum PredictionClassifier {
    KNN("knn"),
    SVM("svm");

    private final String apiValue;

    PredictionClassifier(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }

    public static PredictionClassifier fromApiValue(String value) {
        if (value == null) {
            return KNN;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PredictionClassifier classifier : values()) {
            if (classifier.apiValue.equals(normalized)) {
                return classifier;
            }
        }
        throw new IllegalArgumentException("Classifier must be either 'knn' or 'svm'.");
    }
}
