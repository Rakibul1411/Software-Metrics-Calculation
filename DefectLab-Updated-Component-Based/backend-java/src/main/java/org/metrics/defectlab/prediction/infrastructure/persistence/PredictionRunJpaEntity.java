package org.metrics.defectlab.prediction.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

/** Frameworks & Drivers: the JPA-mapped row shape, kept separate from the {@code PredictionRun} entity. */
@Entity
@Table(name = "prediction_runs")
class PredictionRunJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "comparison_group_id")
    private UUID comparisonGroupId;

    @Column(name = "source_dataset_id", nullable = false)
    private Long sourceDatasetId;

    @Column(name = "target_dataset_id", nullable = false)
    private Long targetDatasetId;

    @Column(name = "model_config", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(read = "model_config::text", write = "?::jsonb")
    private String modelConfig;

    @Column(name = "prediction_file_path", columnDefinition = "text")
    private String predictionFilePath;

    @Column(name = "report_file_path", nullable = false, columnDefinition = "text")
    private String reportFilePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PredictionRunJpaEntity() {
    }

    PredictionRunJpaEntity(Long id, Long userId, UUID comparisonGroupId, Long sourceDatasetId,
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

    Long getId() { return id; }
    Long getUserId() { return userId; }
    UUID getComparisonGroupId() { return comparisonGroupId; }
    Long getSourceDatasetId() { return sourceDatasetId; }
    Long getTargetDatasetId() { return targetDatasetId; }
    String getModelConfig() { return modelConfig; }
    String getPredictionFilePath() { return predictionFilePath; }
    String getReportFilePath() { return reportFilePath; }
    Instant getCreatedAt() { return createdAt; }
}
