package org.metrics.defectlab.dataset.usecase;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Input boundary: resolves a single dataset visible to a user. */
public interface GetDatasetUseCase {

    MetricDataset require(Long userId, Long datasetId);
}
