package org.metrics.defectlab.comparison.usecase;

import java.util.List;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/** Input boundary: lists every metric comparison saved by a user. */
public interface ListComparisonsUseCase {

    List<MetricComparison> list(Long userId);
}
