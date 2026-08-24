package org.metrics.defectlab.analysis.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

/** Output port: how use cases unpack a project archive into a source directory. */
public interface SourceArchiveExtractor {

    Path extractArchiveFile(Path archivePath) throws IOException;
}
