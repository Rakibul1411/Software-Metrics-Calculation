package org.metrics.defectlab.comparison.usecase;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/**
 * Retrieves a single metric comparison record.
 */
public interface GetComparisonUseCase {

    MetricComparison require(Long userId, Long comparisonId);
}
