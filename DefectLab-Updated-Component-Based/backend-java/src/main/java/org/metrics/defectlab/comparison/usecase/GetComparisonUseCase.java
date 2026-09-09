package org.metrics.defectlab.comparison.usecase;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/** Input boundary: resolves a single metric comparison owned by a user. */
public interface GetComparisonUseCase {

    MetricComparison require(Long userId, Long comparisonId);
}
