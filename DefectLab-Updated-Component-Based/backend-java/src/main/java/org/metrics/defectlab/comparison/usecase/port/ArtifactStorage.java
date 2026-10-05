package org.metrics.defectlab.comparison.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

/** Resolves and ensures existence of comparison artifact storage directories. */
public interface ArtifactStorage {

    Path rootFor(String category) throws IOException;
}
