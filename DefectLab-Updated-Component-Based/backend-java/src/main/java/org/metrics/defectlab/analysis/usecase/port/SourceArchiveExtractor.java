package org.metrics.defectlab.analysis.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

/** Extracts compressed project archives into working directories. */
public interface SourceArchiveExtractor {

    Path extractArchiveFile(Path archivePath) throws IOException;
}
