package org.metrics.common.dto;

import org.metrics.common.enums.PredictionClassifier;

public final class PredictionModelOptions {

    private final PredictionClassifier classifier;
    private final int knnValue;
    private final boolean autoTuneK;
    private final double svmC;
    private final boolean autoTuneSvmC;
    private final Double decisionThreshold;
    private final double thresholdBeta;

    private PredictionModelOptions(PredictionClassifier classifier,
                                   int knnValue,
                                   boolean autoTuneK,
                                   double svmC,
                                   boolean autoTuneSvmC,
                                   Double decisionThreshold,
                                   double thresholdBeta) {
        this.classifier = classifier;
        this.knnValue = knnValue;
        this.autoTuneK = autoTuneK;
        this.svmC = svmC;
        this.autoTuneSvmC = autoTuneSvmC;
        this.decisionThreshold = decisionThreshold;
        this.thresholdBeta = thresholdBeta;
    }

    public static PredictionModelOptions create(String classifierType,
                                                int knnValue,
                                                boolean autoTuneK,
                                                double svmC,
                                                boolean autoTuneSvmC,
                                                Double decisionThreshold,
                                                double thresholdBeta) {
        PredictionClassifier classifier = PredictionClassifier.fromApiValue(classifierType);
        if (classifier == PredictionClassifier.KNN && knnValue < 1) {
            throw new IllegalArgumentException("K neighbors must be at least 1.");
        }
        if (classifier == PredictionClassifier.SVM && svmC <= 0) {
            throw new IllegalArgumentException("SVM C must be greater than zero.");
        }
        if (decisionThreshold != null && (decisionThreshold < 0 || decisionThreshold > 1)) {
            throw new IllegalArgumentException("Decision threshold must be between 0 and 1.");
        }
        if (thresholdBeta <= 0) {
            throw new IllegalArgumentException("Threshold beta must be greater than zero.");
        }
        return new PredictionModelOptions(classifier, knnValue, autoTuneK, svmC,
                autoTuneSvmC, decisionThreshold, thresholdBeta);
    }

    public PredictionClassifier getClassifier() {
        return classifier;
    }

    public int getKnnValue() {
        return knnValue;
    }

    public boolean isAutoTuneK() {
        return autoTuneK;
    }

    public double getSvmC() {
        return svmC;
    }

    public boolean isAutoTuneSvmC() {
        return autoTuneSvmC;
    }

    public Double getDecisionThreshold() {
        return decisionThreshold;
    }

    public double getThresholdBeta() {
        return thresholdBeta;
    }
}
