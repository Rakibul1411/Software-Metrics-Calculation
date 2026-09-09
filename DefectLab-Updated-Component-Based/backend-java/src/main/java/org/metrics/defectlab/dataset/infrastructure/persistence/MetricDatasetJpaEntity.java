package org.metrics.defectlab.dataset.infrastructure.persistence;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Frameworks & Drivers: the JPA-mapped row shape, kept separate from the {@code MetricDataset} entity. */
@Entity
@Table(name = "metric_datasets")
class MetricDatasetJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dataset_family", nullable = false, length = 20)
    private MetricDataset.Family datasetFamily;

    @Column(name = "project_name", nullable = false, length = 150)
    private String projectName;

    @Column(name = "project_version", nullable = false, length = 50)
    private String projectVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "dataset_type", nullable = false, length = 20)
    private MetricDataset.Type datasetType;

    @Column(name = "has_actual_label", nullable = false)
    private boolean hasActualLabel;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "total_metrics", nullable = false)
    private int totalMetrics;

    @Column(name = "metrics_file_path", nullable = false, columnDefinition = "text")
    private String metricsFilePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MetricDatasetJpaEntity() {
    }

    MetricDatasetJpaEntity(Long id, Long userId, MetricDataset.Family datasetFamily, String projectName,
                         String projectVersion, MetricDataset.Type datasetType,
                         boolean hasActualLabel, int totalFiles, int totalMetrics,
                         String metricsFilePath, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.datasetFamily = datasetFamily;
        this.projectName = projectName;
        this.projectVersion = projectVersion;
        this.datasetType = datasetType;
        this.hasActualLabel = hasActualLabel;
        this.totalFiles = totalFiles;
        this.totalMetrics = totalMetrics;
        this.metricsFilePath = metricsFilePath;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    Long getUserId() { return userId; }
    MetricDataset.Family getDatasetFamily() { return datasetFamily; }
    String getProjectName() { return projectName; }
    String getProjectVersion() { return projectVersion; }
    MetricDataset.Type getDatasetType() { return datasetType; }
    boolean isHasActualLabel() { return hasActualLabel; }
    int getTotalFiles() { return totalFiles; }
    int getTotalMetrics() { return totalMetrics; }
    String getMetricsFilePath() { return metricsFilePath; }
    Instant getCreatedAt() { return createdAt; }
}
