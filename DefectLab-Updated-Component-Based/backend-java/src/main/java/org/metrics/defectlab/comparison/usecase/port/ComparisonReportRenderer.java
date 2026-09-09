package org.metrics.defectlab.comparison.usecase.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Output port: renders a downloadable metric-comparison report, free of any PDF-library detail. */
public interface ComparisonReportRenderer {

    void writeTables(Path target, String title, List<String> introLines, List<Table> tables)
            throws IOException;

    /** One table section: an optional heading, column headers, and rows. */
    record Table(String heading, List<String> headers, List<List<String>> rows) {
    }
}
