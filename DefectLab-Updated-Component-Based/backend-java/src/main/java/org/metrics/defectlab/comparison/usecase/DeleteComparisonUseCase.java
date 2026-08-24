package org.metrics.defectlab.comparison.usecase;

import java.io.IOException;

/** Input boundary: deletes a saved metric comparison and its stored artifacts. */
public interface DeleteComparisonUseCase {

    void delete(Long userId, Long comparisonId) throws IOException;
}
