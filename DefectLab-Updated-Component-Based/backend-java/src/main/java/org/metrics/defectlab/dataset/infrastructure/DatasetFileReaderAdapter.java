package org.metrics.defectlab.dataset.infrastructure;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.usecase.port.DatasetFileReader;
import org.springframework.stereotype.Component;

/** Gateway: fulfils the {@link DatasetFileReader} port using {@link DatasetFileParser}. */
@Component
public class DatasetFileReaderAdapter implements DatasetFileReader {

    @Override
    public DatasetTable parse(Path file) throws IOException {
        return DatasetFileParser.parse(file);
    }
}
