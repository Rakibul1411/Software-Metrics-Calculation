package org.metrics.defectlab.shared.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfReportWriterTableTest {

    @TempDir
    Path workspace;

    @Test
    void rendersMultiTableReportWithWrappingAndPagination() throws IOException {
        Path target = workspace.resolve("report.pdf");

        List<List<String>> metricRows = List.of(
                List.of("wmc", "10.3017", "10.5920", "10.4479", "10.3229", "-2.74%"),
                List.of("dit", "1.6897", "2.2800", "0.8450", "1.2750", "-25.89%"));

        // Enough rows to force at least one page break, plus one long
        // identifier that must wrap instead of overflowing its column.
        List<List<String>> fileRows = new ArrayList<>();
        fileRows.add(List.of(
                "org.apache.tools.ant.taskdefs.optional.ExtremelyLongClassNameThatMustWrapAcrossMultipleLines",
                "wmc", "15.0000", "17.0000", "MISMATCH"));
        for (int i = 0; i < 120; i++) {
            fileRows.add(List.of("org.apache.tools.ant.Sample" + i, "wmc", "1.0000", "1.0000", "EXACT_MATCH"));
        }

        List<PdfReportWriter.Table> tables = List.of(
                new PdfReportWriter.Table(
                        "Metric-wise mean & std (manual vs predefined)",
                        List.of("Metric", "Mean Manual", "Mean Predefined",
                                "Std Manual", "Std Predefined", "% Diff"),
                        metricRows),
                new PdfReportWriter.Table(
                        "File-wise comparison",
                        List.of("File / Identifier", "Metric", "Manual Value",
                                "Predefined Value", "Status"),
                        fileRows));

        PdfReportWriter.writeTables(target, "DefectLab Metric Comparison Report",
                List.of("Manual dataset: Ant 1.3", "Family: PROMISE"), tables);

        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.size(target) > 0);

        String text;
        try (PDDocument document = PDDocument.load(target.toFile())) {
            assertTrue(document.getNumberOfPages() > 1,
                    "120+ rows should page-break across more than one page");
            text = new PDFTextStripper().getText(document);
        }

        assertTrue(text.contains("DefectLab Metric Comparison Report"));
        assertTrue(text.contains("Metric-wise mean & std"));
        assertTrue(text.contains("Mean Manual"));
        assertTrue(text.contains("-25.89%"));
        assertTrue(text.contains("File-wise comparison"));
        assertTrue(text.contains("EXACT_MATCH"));
        // The long identifier wrapped across two lines within its column
        // rather than overflowing; text extraction keeps the line break, so
        // check both halves rather than the joined word.
        assertTrue(text.contains("ExtremelyLongClassNameThatMustWrapAcrossMultipleLine"));
        assertTrue(text.contains("MISMATCH"));
    }

    @Test
    void plainLineReportStillWorks() throws IOException {
        Path target = workspace.resolve("plain.pdf");
        PdfReportWriter.write(target, "DefectLab Prediction Report",
                List.of("Model: KNN", "", "Accuracy: 0.87"));

        assertTrue(Files.isRegularFile(target));
        try (PDDocument document = PDDocument.load(target.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("DefectLab Prediction Report"));
            assertTrue(text.contains("Accuracy: 0.87"));
        }
    }
}
