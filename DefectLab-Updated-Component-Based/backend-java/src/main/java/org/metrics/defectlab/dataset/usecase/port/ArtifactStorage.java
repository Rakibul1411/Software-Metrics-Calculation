package org.metrics.defectlab.dataset.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

/** Output port: resolves (and ensures the existence of) this component's on-disk storage root. */
public interface ArtifactStorage {

    Path rootFor(String category) throws IOException;
}
