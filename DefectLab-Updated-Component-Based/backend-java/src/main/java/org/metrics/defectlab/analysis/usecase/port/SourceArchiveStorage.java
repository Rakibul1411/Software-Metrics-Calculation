package org.metrics.defectlab.analysis.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

/** Output port: how use cases stage uploaded project archives and clean them up. */
public interface SourceArchiveStorage {

    Path storeUploadedFile(MultipartFile file) throws IOException;

    void delete(Path path);
}
