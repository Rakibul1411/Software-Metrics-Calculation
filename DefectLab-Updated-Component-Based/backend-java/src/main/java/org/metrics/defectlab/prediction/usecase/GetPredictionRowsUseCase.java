package org.metrics.defectlab.prediction.usecase;

import java.util.List;
import java.util.Map;

/**
 * Retrieves individual prediction rows and risk ranks for a prediction run.
 */
public interface GetPredictionRowsUseCase {

    List<Map<String, Object>> predictions(Long userId, Long runId, int limit, boolean buggyOnly);
}
