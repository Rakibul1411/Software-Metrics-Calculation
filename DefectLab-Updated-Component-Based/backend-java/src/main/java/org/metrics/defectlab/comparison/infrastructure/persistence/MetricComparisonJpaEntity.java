package org.metrics.defectlab.comparison.infrastructure.persistence;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

/** Frameworks & Drivers: the JPA-mapped row shape, kept separate from the {@code MetricComparison} entity. */
@Entity
@Table(name = "metric_comparisons")
class MetricComparisonJpaEntity {

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
    private Instant createdAt;

    protected MetricComparisonJpaEntity() {
    }

    MetricComparisonJpaEntity(Long id, Long userId, Long manualDatasetId, Long predefinedDatasetId,
                            String comparisonConfig, String comparisonReportFilePath,
                            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.manualDatasetId = manualDatasetId;
        this.predefinedDatasetId = predefinedDatasetId;
        this.comparisonConfig = comparisonConfig;
        this.comparisonReportFilePath = comparisonReportFilePath;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    Long getUserId() { return userId; }
    Long getManualDatasetId() { return manualDatasetId; }
    Long getPredefinedDatasetId() { return predefinedDatasetId; }
    String getComparisonConfig() { return comparisonConfig; }
    String getComparisonReportFilePath() { return comparisonReportFilePath; }
    Instant getCreatedAt() { return createdAt; }
}
