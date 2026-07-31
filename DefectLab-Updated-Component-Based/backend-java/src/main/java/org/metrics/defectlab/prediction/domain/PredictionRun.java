package org.metrics.defectlab.prediction.domain;

import java.time.Instant;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "prediction_runs")
public class PredictionRun {

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
    private Instant createdAt = Instant.now();

    protected PredictionRun() {
    }

    public PredictionRun(Long userId, UUID comparisonGroupId, Long sourceDatasetId,
                         Long targetDatasetId, String modelConfig,
                         String predictionFilePath, String reportFilePath) {
        this.userId = userId;
        this.comparisonGroupId = comparisonGroupId;
        this.sourceDatasetId = sourceDatasetId;
        this.targetDatasetId = targetDatasetId;
        this.modelConfig = modelConfig;
        this.predictionFilePath = predictionFilePath;
        this.reportFilePath = reportFilePath;
        this.createdAt = Instant.now();
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
