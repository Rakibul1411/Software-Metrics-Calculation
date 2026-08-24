package org.metrics.defectlab.comparison.domain;

import java.time.Instant;

/**
 * Enterprise Business Rule: a saved metric comparison, free of any
 * persistence or framework annotations.
 */
public class MetricComparison {

    private final Long id;
    private final Long userId;
    private final Long manualDatasetId;
    private final Long predefinedDatasetId;
    private final String comparisonConfig;
    private final String comparisonReportFilePath;
    private final Instant createdAt;

    public MetricComparison(Long id, Long userId, Long manualDatasetId, Long predefinedDatasetId,
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

    /** A brand-new comparison; the id is assigned once the repository persists it. */
    public static MetricComparison newComparison(Long userId, Long manualDatasetId,
            Long predefinedDatasetId, String comparisonConfig, String comparisonReportFilePath) {
        return new MetricComparison(null, userId, manualDatasetId, predefinedDatasetId,
                comparisonConfig, comparisonReportFilePath, Instant.now());
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getManualDatasetId() { return manualDatasetId; }
    public Long getPredefinedDatasetId() { return predefinedDatasetId; }
    public String getComparisonConfig() { return comparisonConfig; }
    public String getComparisonReportFilePath() { return comparisonReportFilePath; }
    public Instant getCreatedAt() { return createdAt; }
}
