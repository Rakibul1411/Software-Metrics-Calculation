package org.metrics.defectlab.dataset.usecase;

import java.io.InputStream;

/** Encapsulates an uploaded dataset file stream and metadata. */
public record UploadedFile(InputStream content, String originalFilename, long size) {
}
