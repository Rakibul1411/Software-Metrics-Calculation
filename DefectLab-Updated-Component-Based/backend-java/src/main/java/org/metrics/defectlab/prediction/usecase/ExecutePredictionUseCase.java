package org.metrics.defectlab.prediction.usecase;

import java.io.IOException;
import java.util.Map;

/** Input boundary: runs a KNN prediction workflow against one or two targets. */
public interface ExecutePredictionUseCase {

    Map<String, Object> execute(Long userId, Map<String, Object> body) throws IOException;
}
