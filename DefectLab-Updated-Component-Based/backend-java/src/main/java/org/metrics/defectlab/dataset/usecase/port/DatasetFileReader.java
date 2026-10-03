package org.metrics.defectlab.dataset.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.DatasetTable;

/** Reads and parses stored CSV and ARFF dataset files from the filesystem. */
public interface DatasetFileReader {

    DatasetTable parse(Path file) throws IOException;
}
