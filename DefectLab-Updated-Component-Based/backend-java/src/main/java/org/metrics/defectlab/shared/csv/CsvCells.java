package org.metrics.defectlab.shared.csv;

/** Quoting for exported CSV cells. */
public final class CsvCells {

    /** Leading characters a spreadsheet would evaluate as a formula. */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvCells() {
    }

    /**
     * Escapes one cell.
     *
     * <p>A value starting with {@code =}, {@code +}, {@code -} or {@code @} is
     * prefixed with an apostrophe so a spreadsheet treats it as text instead of
     * executing it as a formula.</p>
     */
    public static String escape(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && FORMULA_TRIGGERS.indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n")
                ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
