package org.metrics.defectlab.prediction.usecase;

import java.io.IOException;

/**
 * Deletes a prediction run and cleans up associated artifact files.
 */
public interface DeletePredictionRunUseCase {

    void delete(Long userId, Long runId) throws IOException;
}
