package org.metrics.defectlab.prediction.usecase;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/**
 * Retrieves a single prediction run record.
 */
public interface GetPredictionRunUseCase {

    PredictionRun require(Long userId, Long runId);
}
