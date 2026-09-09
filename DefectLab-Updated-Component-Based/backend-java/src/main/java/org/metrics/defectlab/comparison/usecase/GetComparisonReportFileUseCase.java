package org.metrics.defectlab.comparison.usecase;

import java.nio.file.Path;

/** Input boundary: locates the stored downloadable report for a comparison. */
public interface GetComparisonReportFileUseCase {

    Path reportFile(Long userId, Long comparisonId);
}
