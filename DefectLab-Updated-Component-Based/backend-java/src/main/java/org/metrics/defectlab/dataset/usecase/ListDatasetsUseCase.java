package org.metrics.defectlab.dataset.usecase;

import java.util.List;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Lists all datasets accessible to the specified user.
 */
public interface ListDatasetsUseCase {

    List<MetricDataset> list(Long userId);
}
