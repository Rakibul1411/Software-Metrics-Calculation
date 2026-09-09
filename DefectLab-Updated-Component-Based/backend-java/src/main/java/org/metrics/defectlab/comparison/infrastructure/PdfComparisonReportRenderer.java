package org.metrics.defectlab.comparison.infrastructure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.metrics.defectlab.comparison.usecase.port.ComparisonReportRenderer;
import org.metrics.defectlab.shared.report.PdfReportWriter;
import org.springframework.stereotype.Component;

/** Gateway: fulfils the {@link ComparisonReportRenderer} port using {@link PdfReportWriter}. */
@Component
public class PdfComparisonReportRenderer implements ComparisonReportRenderer {

    @Override
    public void writeTables(Path target, String title, List<String> introLines, List<Table> tables)
            throws IOException {
        List<PdfReportWriter.Table> converted = tables.stream()
                .map(table -> new PdfReportWriter.Table(table.heading(), table.headers(), table.rows()))
                .toList();
        PdfReportWriter.writeTables(target, title, introLines, converted);
    }
}
