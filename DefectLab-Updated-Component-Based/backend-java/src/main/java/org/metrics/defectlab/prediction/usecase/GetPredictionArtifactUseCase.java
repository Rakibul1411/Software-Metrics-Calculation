package org.metrics.defectlab.prediction.usecase;

import java.io.IOException;
import java.nio.file.Path;

/** Input boundary: locates the stored downloadable artifacts for a run. */
public interface GetPredictionArtifactUseCase {

    Path predictionFile(Long userId, Long runId);

    byte[] predictionArff(Long userId, Long runId) throws IOException;

    Path reportFile(Long userId, Long runId);
}
