package org.metrics.defectlab.prediction.usecase;

import java.io.IOException;

/** Input boundary: deletes a prediction run and its stored artifacts. */
public interface DeletePredictionRunUseCase {

    void delete(Long userId, Long runId) throws IOException;
}
