package org.metrics.defectlab.prediction.usecase;

import java.util.List;
import java.util.Map;

/** Input boundary: reads the stored per-file predictions for a run, optionally filtered. */
public interface GetPredictionRowsUseCase {

    List<Map<String, Object>> predictions(Long userId, Long runId, int limit, boolean buggyOnly);
}
