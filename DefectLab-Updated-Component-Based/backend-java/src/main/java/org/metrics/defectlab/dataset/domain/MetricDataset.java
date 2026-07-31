package org.metrics.defectlab.dataset.domain;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "metric_datasets")
public class MetricDataset {

    public enum Family { PROMISE, AEEEM }

    public enum Type { MANUAL, PREDEFINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dataset_family", nullable = false, length = 20)
    private Family datasetFamily;

    @Column(name = "project_name", nullable = false, length = 150)
    private String projectName;

    @Column(name = "project_version", nullable = false, length = 50)
    private String projectVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "dataset_type", nullable = false, length = 20)
    private Type datasetType;

    @Column(name = "has_actual_label", nullable = false)
    private boolean hasActualLabel;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "total_metrics", nullable = false)
    private int totalMetrics;

    @Column(name = "metrics_file_path", nullable = false, columnDefinition = "text")
    private String metricsFilePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MetricDataset() {
    }

    public MetricDataset(Long userId, Family datasetFamily, String projectName,
                         String projectVersion, Type datasetType,
                         boolean hasActualLabel, int totalFiles, int totalMetrics,
                         String metricsFilePath) {
        this.userId = userId;
        this.datasetFamily = datasetFamily;
        this.projectName = projectName;
        this.projectVersion = projectVersion;
        this.datasetType = datasetType;
        this.hasActualLabel = hasActualLabel;
        this.totalFiles = totalFiles;
        this.totalMetrics = totalMetrics;
        this.metricsFilePath = metricsFilePath;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Family getDatasetFamily() { return datasetFamily; }
    public String getProjectName() { return projectName; }
    public String getProjectVersion() { return projectVersion; }
    public Type getDatasetType() { return datasetType; }
    public boolean hasActualLabel() { return hasActualLabel; }
    public int getTotalFiles() { return totalFiles; }
    public int getTotalMetrics() { return totalMetrics; }
    public String getMetricsFilePath() { return metricsFilePath; }
    public Instant getCreatedAt() { return createdAt; }

    public String getDisplayName() {
        return projectVersion == null || projectVersion.isBlank()
                ? projectName : projectName + " " + projectVersion;
    }
}
