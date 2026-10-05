package org.metrics.defectlab.dataset.usecase.port;

/**
 * Guard interface to verify whether a dataset is actively referenced by
 * predictions or comparisons before allowing deletion.
 */
public interface DatasetUsageGuard {

    boolean isReferencedElsewhere(Long datasetId);
}
