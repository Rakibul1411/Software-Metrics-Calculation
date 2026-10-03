package org.metrics.defectlab.comparison.usecase;

import java.util.List;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/**
 * Lists all metric comparisons created by a user.
 */
public interface ListComparisonsUseCase {

    List<MetricComparison> list(Long userId);
}
