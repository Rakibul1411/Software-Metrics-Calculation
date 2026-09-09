package org.metrics.defectlab.prediction.usecase;

import java.util.List;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/** Input boundary: lists every prediction run owned by a user. */
public interface ListPredictionRunsUseCase {

    List<PredictionRun> list(Long userId);
}
