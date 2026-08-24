package org.metrics.defectlab.comparison.usecase;

import java.io.IOException;
import java.util.Map;

/** Input boundary: runs (or returns a cached) metric comparison between two datasets. */
public interface ExecuteComparisonUseCase {

    Map<String, Object> execute(Long userId, Map<String, Object> body) throws IOException;
}
