package org.metrics.defectlab.prediction.usecase;

import java.nio.file.Path;

/** Input boundary: locates the stored downloadable artifacts for a run. */
public interface GetPredictionArtifactUseCase {

    Path predictionFile(Long userId, Long runId);

    Path reportFile(Long userId, Long runId);
}
