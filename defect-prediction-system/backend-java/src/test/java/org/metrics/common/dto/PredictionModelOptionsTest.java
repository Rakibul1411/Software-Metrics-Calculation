package org.metrics.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metrics.common.enums.PredictionClassifier;

class PredictionModelOptionsTest {

    @Test
    void createsKnnOptions() {
        PredictionModelOptions options = PredictionModelOptions.create(
                "knn", 7, true, 1.0, false, null, 2.0);

        assertEquals(PredictionClassifier.KNN, options.getClassifier());
        assertEquals(7, options.getKnnValue());
        assertTrue(options.isAutoTuneK());
        assertFalse(options.isAutoTuneSvmC());
    }

    @Test
    void createsSvmOptions() {
        PredictionModelOptions options = PredictionModelOptions.create(
                "SVM", 5, false, 0.1, true, 0.35, 2.0);

        assertEquals(PredictionClassifier.SVM, options.getClassifier());
        assertEquals(0.1, options.getSvmC());
        assertTrue(options.isAutoTuneSvmC());
        assertEquals(0.35, options.getDecisionThreshold());
    }

    @Test
    void rejectsInvalidClassifierParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> PredictionModelOptions.create("random-forest", 5, false, 1, false, null, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PredictionModelOptions.create("knn", 0, false, 1, false, null, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PredictionModelOptions.create("svm", 5, false, 0, false, null, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PredictionModelOptions.create("svm", 5, false, 1, false, 1.1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PredictionModelOptions.create("svm", 5, false, 1, false, null, 0));
    }
}
