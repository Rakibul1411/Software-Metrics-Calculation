package org.metrics.defectlab.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatasetFileFormatTest {

    @Test
    void recognizesCsvAndArffExtensions() {
        assertEquals(DatasetFileFormat.CSV, DatasetFileFormat.fromFileName("ant-1.7.CSV"));
        assertEquals(DatasetFileFormat.ARFF, DatasetFileFormat.fromFileName("LC.arff"));
    }

    @Test
    void rejectsUnsupportedDatasetExtensions() {
        assertThrows(IllegalArgumentException.class,
                () -> DatasetFileFormat.fromFileName("metrics.txt"));
    }
}
