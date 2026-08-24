package org.metrics.defectlab.comparison.usecase;

import java.util.List;
import java.util.Map;

/** Input boundary: finds MANUAL/PREDEFINED dataset pairs eligible for comparison. */
public interface GetEligiblePairsUseCase {

    List<Map<String, Object>> eligiblePairs(Long userId);
}
