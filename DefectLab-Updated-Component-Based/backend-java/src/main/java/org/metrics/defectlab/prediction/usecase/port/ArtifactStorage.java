package org.metrics.defectlab.prediction.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

/** Resolves and ensures existence of prediction artifact storage directories. */
public interface ArtifactStorage {

    Path rootFor(String category) throws IOException;
}
