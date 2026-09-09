package org.metrics.defectlab.analysis.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.analysis.usecase.UploadedArchive;

/** Output port: how use cases stage uploaded project archives and clean them up. */
public interface SourceArchiveStorage {

    Path storeUploadedFile(UploadedArchive file) throws IOException;

    void delete(Path path);
}
