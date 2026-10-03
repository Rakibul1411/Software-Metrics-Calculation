package org.metrics.defectlab.comparison.usecase;

import java.util.List;
import java.util.Map;

/**
 * Finds eligible dataset pairs available for metric comparison.
 */
public interface GetEligiblePairsUseCase {

    List<Map<String, Object>> eligiblePairs(Long userId);
}
