package org.metrics.defectlab.dataset.infrastructure;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.defectlab.dataset.domain.DatasetTable;

/**
 * Renders a parsed {@link DatasetTable} back out as CSV or ARFF text, so a
 * dataset stored in one format can still be downloaded in the other.
 */
public final class DatasetFileWriter {

    private DatasetFileWriter() {
    }

    public static String toCsv(DatasetTable table) throws IOException {
        StringWriter buffer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(buffer, CSVFormat.DEFAULT)) {
            printer.printRecord(table.getHeaders());
            for (List<String> row : table.getRows()) {
                printer.printRecord(row);
            }
        }
        return buffer.toString();
    }

    public static String toArff(DatasetTable table, String relationName) {
        List<String> headers = table.getHeaders();
        boolean[] numeric = new boolean[headers.size()];
        for (int column = 0; column < headers.size(); column++) {
            numeric[column] = isNumericColumn(table, column);
        }

        StringBuilder arff = new StringBuilder();
        arff.append("@relation ").append(quoteIfNeeded(relationName)).append('\n').append('\n');
        for (int column = 0; column < headers.size(); column++) {
            arff.append("@attribute ").append(quoteIfNeeded(headers.get(column)))
                    .append(' ').append(numeric[column] ? "numeric" : "string").append('\n');
        }
        arff.append('\n').append("@data").append('\n');
        for (List<String> row : table.getRows()) {
            for (int column = 0; column < headers.size(); column++) {
                if (column > 0) {
                    arff.append(',');
                }
                String value = column < row.size() ? row.get(column) : "";
                arff.append(arffValue(value, numeric[column]));
            }
            arff.append('\n');
        }
        return arff.toString();
    }

    private static boolean isNumericColumn(DatasetTable table, int column) {
        boolean sawValue = false;
        for (List<String> row : table.getRows()) {
            String value = column < row.size() ? row.get(column).trim() : "";
            if (value.isEmpty() || "?".equals(value)) {
                continue;
            }
            try {
                Double.parseDouble(value);
                sawValue = true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return sawValue;
    }

    private static String arffValue(String rawValue, boolean numeric) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return "?";
        }
        return numeric ? value : quoteIfNeeded(value);
    }

    /** Quotes any value ARFF can't read bare: commas, whitespace, quotes, or '%' comments. */
    private static String quoteIfNeeded(String value) {
        boolean needsQuoting = value.isEmpty();
        for (int index = 0; !needsQuoting && index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || character == ',' || character == '\''
                    || character == '"' || character == '%' || character == '{' || character == '}') {
                needsQuoting = true;
            }
        }
        if (!needsQuoting) {
            return value;
        }
        String escaped = value.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    public static String sanitizedRelationName(String projectName, String projectVersion) {
        String base = (projectName + "_" + (projectVersion == null ? "" : projectVersion))
                .trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return base.isEmpty() ? "dataset" : base.toLowerCase(Locale.ROOT);
    }
}
