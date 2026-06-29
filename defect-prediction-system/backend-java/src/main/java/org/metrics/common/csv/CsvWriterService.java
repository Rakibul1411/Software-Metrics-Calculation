package org.metrics.common.csv;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

@Service
public class CsvWriterService {
    public void writeCsv(Path path, List<String> headers, List<List<Object>> records) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csvPrinter.printRecord(headers);
            for (List<Object> record : records) {
                csvPrinter.printRecord(record);
            }
        }
    }
}
