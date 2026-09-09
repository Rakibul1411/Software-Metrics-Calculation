package org.metrics.defectlab.prediction.infrastructure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.metrics.defectlab.prediction.usecase.port.PredictionReportRenderer;
import org.metrics.defectlab.shared.report.PdfReportWriter;
import org.springframework.stereotype.Component;

/** Gateway: fulfils the {@link PredictionReportRenderer} port using {@link PdfReportWriter}. */
@Component
public class PdfPredictionReportRenderer implements PredictionReportRenderer {

    @Override
    public void write(Path target, String title, List<String> lines) throws IOException {
        PdfReportWriter.write(target, title, lines);
    }

    @Override
    public void writeTables(Path target, String title, List<String> introLines, List<Table> tables)
            throws IOException {
        List<PdfReportWriter.Table> converted = tables.stream()
                .map(table -> new PdfReportWriter.Table(
                        table.heading(), table.headers(), table.rows(), table.columnWeights()))
                .toList();
        PdfReportWriter.writeTables(target, title, introLines, converted);
    }
}
