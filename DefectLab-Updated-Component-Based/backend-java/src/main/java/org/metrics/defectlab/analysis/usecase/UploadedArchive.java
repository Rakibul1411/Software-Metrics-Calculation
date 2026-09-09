package org.metrics.defectlab.analysis.usecase;

import java.io.InputStream;

/** Input boundary value: a framework-free view of an uploaded project archive. */
public record UploadedArchive(InputStream content, String originalFilename, long size) {

    public boolean hasContent() {
        return size > 0;
    }
}
