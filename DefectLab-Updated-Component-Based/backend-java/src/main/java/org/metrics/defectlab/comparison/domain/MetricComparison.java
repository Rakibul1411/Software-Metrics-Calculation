package org.metrics.defectlab.comparison.domain;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "metric_comparisons")
public class MetricComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "manual_dataset_id", nullable = false)
    private Long manualDatasetId;

    @Column(name = "predefined_dataset_id", nullable = false)
    private Long predefinedDatasetId;

    @Column(name = "comparison_config", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(read = "comparison_config::text", write = "?::jsonb")
    private String comparisonConfig;

    @Column(name = "comparison_report_file_path", nullable = false, columnDefinition = "text")
    private String comparisonReportFilePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MetricComparison() {
    }

    public MetricComparison(Long userId, Long manualDatasetId, Long predefinedDatasetId,
                            String comparisonConfig, String comparisonReportFilePath) {
        this.userId = userId;
        this.manualDatasetId = manualDatasetId;
        this.predefinedDatasetId = predefinedDatasetId;
        this.comparisonConfig = comparisonConfig;
        this.comparisonReportFilePath = comparisonReportFilePath;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getManualDatasetId() { return manualDatasetId; }
    public Long getPredefinedDatasetId() { return predefinedDatasetId; }
    public String getComparisonConfig() { return comparisonConfig; }
    public String getComparisonReportFilePath() { return comparisonReportFilePath; }
    public Instant getCreatedAt() { return createdAt; }
}
