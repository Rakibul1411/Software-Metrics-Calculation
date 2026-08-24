package org.metrics.defectlab.dataset.domain;

import java.time.Instant;

/**
 * Enterprise Business Rule: a registered metric dataset, free of any
 * persistence or framework annotations.
 */
public class MetricDataset {

    public enum Family { PROMISE, AEEEM }

    public enum Type { MANUAL, PREDEFINED }

    private final Long id;
    private final Long userId;
    private final Family datasetFamily;
    private final String projectName;
    private final String projectVersion;
    private final Type datasetType;
    private final boolean hasActualLabel;
    private final int totalFiles;
    private final int totalMetrics;
    private final String metricsFilePath;
    private final Instant createdAt;

    public MetricDataset(Long id, Long userId, Family datasetFamily, String projectName,
                         String projectVersion, Type datasetType,
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

    /** A brand-new registration; the id is assigned once the repository persists it. */
    public static MetricDataset newRegistration(Long userId, Family datasetFamily, String projectName,
                         String projectVersion, Type datasetType,
                         boolean hasActualLabel, int totalFiles, int totalMetrics,
                         String metricsFilePath) {
        return new MetricDataset(null, userId, datasetFamily, projectName, projectVersion,
                datasetType, hasActualLabel, totalFiles, totalMetrics, metricsFilePath, Instant.now());
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
