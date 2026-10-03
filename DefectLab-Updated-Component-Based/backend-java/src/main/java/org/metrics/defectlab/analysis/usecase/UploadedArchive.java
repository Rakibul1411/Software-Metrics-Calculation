package org.metrics.defectlab.analysis.usecase;

import java.io.InputStream;

/** Encapsulates an uploaded project archive stream and file metadata. */
public record UploadedArchive(InputStream content, String originalFilename, long size) {

    public boolean hasContent() {
        return size > 0;
    }
}
