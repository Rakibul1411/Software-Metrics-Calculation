package org.metrics.defectlab.shared.model;

import java.util.Locale;

public enum DatasetFileFormat {
    CSV("csv", "text/csv"),
    ARFF("arff", "text/plain");

    private final String extension;
    private final String mediaType;

    DatasetFileFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String getExtension() {
        return extension;
    }

    public String getMediaType() {
        return mediaType;
    }

    public static DatasetFileFormat fromFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        for (DatasetFileFormat format : values()) {
            if (normalized.endsWith("." + format.extension)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Dataset files must use the .csv or .arff extension.");
    }

    public static DatasetFileFormat fromExtension(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        for (DatasetFileFormat format : values()) {
            if (format.extension.equals(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Dataset format must be CSV or ARFF.");
    }
}
