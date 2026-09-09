package org.metrics.defectlab.dataset.usecase;

import java.io.InputStream;

/** Input boundary value: a framework-free view of an uploaded file. */
public record UploadedFile(InputStream content, String originalFilename, long size) {
}
