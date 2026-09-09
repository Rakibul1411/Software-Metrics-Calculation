package org.metrics.defectlab.prediction.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Enterprise Business Rule: a completed prediction run, free of any
 * persistence or framework annotations.
 */
public class PredictionRun {

    private final Long id;
    private final Long userId;
    private final UUID comparisonGroupId;
    private final Long sourceDatasetId;
    private final Long targetDatasetId;
    private final String modelConfig;
    private final String predictionFilePath;
    private final String reportFilePath;
    private final Instant createdAt;

    public PredictionRun(Long id, Long userId, UUID comparisonGroupId, Long sourceDatasetId,
                         Long targetDatasetId, String modelConfig,
                         String predictionFilePath, String reportFilePath, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.comparisonGroupId = comparisonGroupId;
        this.sourceDatasetId = sourceDatasetId;
        this.targetDatasetId = targetDatasetId;
        this.modelConfig = modelConfig;
        this.predictionFilePath = predictionFilePath;
        this.reportFilePath = reportFilePath;
        this.createdAt = createdAt;
    }

    /** A brand-new run; the id is assigned once the repository persists it. */
    public static PredictionRun newRun(Long userId, UUID comparisonGroupId, Long sourceDatasetId,
                         Long targetDatasetId, String modelConfig,
                         String predictionFilePath, String reportFilePath) {
        return new PredictionRun(null, userId, comparisonGroupId, sourceDatasetId, targetDatasetId,
                modelConfig, predictionFilePath, reportFilePath, Instant.now());
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public UUID getComparisonGroupId() { return comparisonGroupId; }
    public Long getSourceDatasetId() { return sourceDatasetId; }
    public Long getTargetDatasetId() { return targetDatasetId; }
    public String getModelConfig() { return modelConfig; }
    public String getPredictionFilePath() { return predictionFilePath; }
    public String getReportFilePath() { return reportFilePath; }
    public Instant getCreatedAt() { return createdAt; }
}
