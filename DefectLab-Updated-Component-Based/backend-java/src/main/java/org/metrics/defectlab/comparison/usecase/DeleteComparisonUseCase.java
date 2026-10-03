package org.metrics.defectlab.comparison.usecase;

import java.io.IOException;

/**
 * Deletes a metric comparison and its generated report artifacts.
 */
public interface DeleteComparisonUseCase {

    void delete(Long userId, Long comparisonId) throws IOException;
}
