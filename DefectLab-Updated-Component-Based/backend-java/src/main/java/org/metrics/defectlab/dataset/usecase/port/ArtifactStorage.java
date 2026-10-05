package org.metrics.defectlab.dataset.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

/** Resolves and ensures existence of dataset artifact storage directories. */
public interface ArtifactStorage {

    Path rootFor(String category) throws IOException;
}
