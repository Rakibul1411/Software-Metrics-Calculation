package org.metrics.defectlab.shared.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ArffDatasetExporter {

    private ArffDatasetExporter() {
    }

    public static void export(String relation, List<String> columns, List<List<Object>> rows,
                              Path outputPath) throws IOException {
        if (columns.isEmpty() || !"name".equalsIgnoreCase(columns.get(0))) {
            throw new IllegalArgumentException("The first extracted dataset column must be the class identifier 'name'.");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("@relation " + quote(relation));
            writer.newLine();
            writer.newLine();
            for (int index = 0; index < columns.size(); index++) {
                writer.write("@attribute " + quote(columns.get(index)) + (index == 0 ? " string" : " numeric"));
                writer.newLine();
            }
            writer.newLine();
            writer.write("@data");
            writer.newLine();
            for (List<Object> row : rows) {
                if (row.size() != columns.size()) {
                    throw new IllegalArgumentException("ARFF row width does not match the extracted feature schema.");
                }
                for (int index = 0; index < row.size(); index++) {
                    if (index > 0) {
                        writer.write(',');
                    }
                    Object value = row.get(index);
                    writer.write(index == 0 ? quote(String.valueOf(value)) : numericValue(value));
                }
                writer.newLine();
            }
        }
    }

    private static String numericValue(Object value) {
        if (value == null) {
            return "?";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "?" : text;
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
