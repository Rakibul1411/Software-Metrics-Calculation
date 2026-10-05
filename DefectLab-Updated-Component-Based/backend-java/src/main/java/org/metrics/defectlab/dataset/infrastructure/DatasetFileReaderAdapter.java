package org.metrics.defectlab.dataset.infrastructure;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.usecase.port.DatasetFileReader;
import org.springframework.stereotype.Component;

/**
 * Adapter that parses metric datasets from disk into tabular domain models.
 */
@Component
public class DatasetFileReaderAdapter implements DatasetFileReader {

    @Override
    public DatasetTable parse(Path file) throws IOException {
        return DatasetFileParser.parse(file);
    }
}
