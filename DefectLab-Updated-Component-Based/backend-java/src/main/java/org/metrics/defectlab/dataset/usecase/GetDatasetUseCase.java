package org.metrics.defectlab.dataset.usecase;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Retrieves a single metric dataset record.
 */
public interface GetDatasetUseCase {

    MetricDataset require(Long userId, Long datasetId);
}
