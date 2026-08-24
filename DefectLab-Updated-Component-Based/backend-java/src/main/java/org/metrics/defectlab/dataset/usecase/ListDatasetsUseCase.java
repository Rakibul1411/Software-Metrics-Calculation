package org.metrics.defectlab.dataset.usecase;

import java.util.List;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Input boundary: lists every dataset visible to a user. */
public interface ListDatasetsUseCase {

    List<MetricDataset> list(Long userId);
}
