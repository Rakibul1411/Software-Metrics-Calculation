package org.metrics.defectlab.prediction.usecase;

import java.util.List;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/**
 * Lists all prediction runs executed by a user.
 */
public interface ListPredictionRunsUseCase {

    List<PredictionRun> list(Long userId);
}
