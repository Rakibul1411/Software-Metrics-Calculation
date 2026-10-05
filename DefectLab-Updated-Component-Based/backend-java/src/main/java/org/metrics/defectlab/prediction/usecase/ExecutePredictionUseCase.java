package org.metrics.defectlab.prediction.usecase;

import java.io.IOException;
import java.util.Map;

/**
 * Executes a defect prediction workflow for given source and target datasets.
 */
public interface ExecutePredictionUseCase {

    Map<String, Object> execute(Long userId, Map<String, Object> body) throws IOException;
}
