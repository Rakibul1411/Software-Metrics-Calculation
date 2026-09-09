package org.metrics.defectlab.prediction.usecase;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/** Input boundary: resolves a single prediction run owned by a user. */
public interface GetPredictionRunUseCase {

    PredictionRun require(Long userId, Long runId);
}
