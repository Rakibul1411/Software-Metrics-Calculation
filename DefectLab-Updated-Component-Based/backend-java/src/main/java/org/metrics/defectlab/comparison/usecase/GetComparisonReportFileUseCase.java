package org.metrics.defectlab.comparison.usecase;

import java.nio.file.Path;

/**
 * Locates the generated PDF report file for a metric comparison.
 */
public interface GetComparisonReportFileUseCase {

    Path reportFile(Long userId, Long comparisonId);
}
