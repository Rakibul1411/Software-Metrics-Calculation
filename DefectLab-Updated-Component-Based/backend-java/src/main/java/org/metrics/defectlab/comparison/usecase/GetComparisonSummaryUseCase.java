package org.metrics.defectlab.comparison.usecase;

import java.util.Map;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/** Input boundary: builds display summaries for a metric comparison. */
public interface GetComparisonSummaryUseCase {

    Map<String, Object> summary(Long userId, MetricComparison comparison);

    Map<String, Object> detail(Long userId, MetricComparison comparison);
}
