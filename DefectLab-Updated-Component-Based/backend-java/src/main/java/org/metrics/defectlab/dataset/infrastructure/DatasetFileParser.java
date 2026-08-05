package org.metrics.defectlab.dataset.infrastructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.MetricHeaderNormalizer;

/**
 * Reads CSV and ARFF datasets into a {@link DatasetTable}.
 *
 * <p>Headers are trimmed and lowercased so family detection matches by canonical
 * name rather than by column position, as the SRS requires.</p>
 */
public final class DatasetFileParser {

    private DatasetFileParser() {
    }

    public static DatasetTable parse(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".arff")) {
            return parseArff(file);
        }
        if (name.endsWith(".csv")) {
            return parseCsv(file);
        }
        throw new IllegalArgumentException("A dataset must be a .csv or .arff file.");
    }

    private static DatasetTable parseCsv(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader()
                     .withIgnoreSurroundingSpaces(true)
                     .withIgnoreEmptyLines(true)
                     .parse(reader)) {
            List<String> headers = new ArrayList<>();
            for (String header : parser.getHeaderNames()) {
                headers.add(normalizeHeader(header));
            }
            List<List<String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>(headers.size());
                for (int index = 0; index < headers.size(); index++) {
                    row.add(index < record.size() ? record.get(index).trim() : "");
                }
                rows.add(row);
            }
            return new DatasetTable(headers, rows);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "The CSV header could not be read: " + exception.getMessage());
        }
    }

    private static DatasetTable parseArff(Path file) throws IOException {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        boolean inData = false;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // '%' is the ARFF comment marker; the published AEEEM files also
                // carry a '###' column-legend line inside the @data section.
                if (trimmed.isEmpty() || trimmed.startsWith("%") || trimmed.startsWith("#")) {
                    continue;
                }
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (!inData) {
                    if (lower.startsWith("@attribute")) {
                        headers.add(normalizeHeader(attributeName(trimmed)));
                    } else if (lower.startsWith("@data")) {
                        inData = true;
                    }
                    continue;
                }
                List<String> row = splitArffData(trimmed, headers.size());
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
        }
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("The ARFF file declares no @attribute columns.");
        }
        return new DatasetTable(headers, rows);
    }

    /** Handles {@code @attribute 'name' string} and {@code @attribute wmc numeric}. */
    private static String attributeName(String line) {
        String remainder = line.substring("@attribute".length()).trim();
        if (remainder.startsWith("'") || remainder.startsWith("\"")) {
            char quote = remainder.charAt(0);
            StringBuilder name = new StringBuilder();
            for (int index = 1; index < remainder.length(); index++) {
                char current = remainder.charAt(index);
                if (current == '\\' && index + 1 < remainder.length()) {
                    name.append(remainder.charAt(++index));
                    continue;
                }
                if (current == quote) {
                    break;
                }
                name.append(current);
            }
            return name.toString();
        }
        int separator = remainder.indexOf(' ');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private static List<String> splitArffData(String line, int expectedColumns) {
        List<String> values = new ArrayList<>(expectedColumns);
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quote != 0) {
                if (character == '\\' && index + 1 < line.length()) {
                    current.append(line.charAt(++index));
                } else if (character == quote) {
                    quote = 0;
                } else {
                    current.append(character);
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == ',') {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString().trim());
        while (values.size() < expectedColumns) {
            values.add("");
        }
        return values;
    }

    public static String normalizeHeader(String header) {
        return MetricHeaderNormalizer.normalize(header);
    }
}
