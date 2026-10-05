package org.metrics.defectlab.comparison.usecase;

import java.util.Map;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/**
 * Generates display summaries for metric comparisons.
 */
public interface GetComparisonSummaryUseCase {

    Map<String, Object> summary(Long userId, MetricComparison comparison);

    Map<String, Object> detail(Long userId, MetricComparison comparison);
}
