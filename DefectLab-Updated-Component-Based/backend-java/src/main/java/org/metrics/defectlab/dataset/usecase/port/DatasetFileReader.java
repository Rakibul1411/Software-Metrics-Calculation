package org.metrics.defectlab.dataset.usecase.port;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.DatasetTable;

/** Output port: how use cases read a stored CSV/ARFF dataset file from disk. */
public interface DatasetFileReader {

    DatasetTable parse(Path file) throws IOException;
}
