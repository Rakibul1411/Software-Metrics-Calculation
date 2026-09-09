package org.metrics.defectlab.comparison.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.metrics.defectlab.comparison.usecase.port.ComparisonReportRenderer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonPdfRenderingTest {

    @Test
    void rendersComparisonPdfWithProperAlignment() throws Exception {
        Path metaPath = Paths.get("storage/comparison-reports/1/dcb92f82-7e23-4f2f-9d25-d7c080e588c0-metric-comparison.pdf.json");
        Path pdfPath = Paths.get("storage/comparison-reports/1/dcb92f82-7e23-4f2f-9d25-d7c080e588c0-metric-comparison.pdf");

        if (Files.isRegularFile(metaPath)) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> meta = mapper.readValue(metaPath.toFile(), new TypeReference<Map<String, Object>>() {});
            List<?> metrics = (List<?>) meta.get("metrics");

            List<List<String>> rows = new java.util.ArrayList<>();
            for (Object obj : metrics) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) obj;
                @SuppressWarnings("unchecked")
                Map<String, Object> manual = (Map<String, Object>) row.get("manual");
                @SuppressWarnings("unchecked")
                Map<String, Object> predefined = (Map<String, Object>) row.get("predefined");
                rows.add(List.of(
                        String.valueOf(row.get("metric")),
                        String.format(java.util.Locale.ROOT, "%.4f", ((Number) manual.get("mean")).doubleValue()),
                        String.format(java.util.Locale.ROOT, "%.4f", ((Number) predefined.get("mean")).doubleValue()),
                        String.format(java.util.Locale.ROOT, "%.4f", ((Number) manual.get("standardDeviation")).doubleValue()),
                        String.format(java.util.Locale.ROOT, "%.4f", ((Number) predefined.get("standardDeviation")).doubleValue()),
                        String.format(java.util.Locale.ROOT, "%.2f%%", ((Number) row.get("percentageDifference")).doubleValue())
                ));
            }

            ComparisonReportRenderer.Table table = new ComparisonReportRenderer.Table(
                    "Metric-wise mean & std (manual vs predefined)",
                    List.of("Metric", "Mean Manual", "Mean Predefined", "Std Manual", "Std Predefined", "% Diff"),
                    rows,
                    new float[]{ 2.6f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f }
            );

            List<String> intro = List.of(
                    "Manual dataset: ml 3.1",
                    "Predefined dataset: ml 3.1",
                    "Family: AEEEM",
                    "Comparison mode: AGGREGATE",
                    "Tolerance: Absolute = 0.0001, Relative = 0.01",
                    "Common numeric metrics: " + metrics.size()
            );

            PdfComparisonReportRenderer renderer = new PdfComparisonReportRenderer();
            renderer.writeTables(pdfPath, "DefectLab Metric Comparison Report", intro, List.of(table));

            assertTrue(Files.isRegularFile(pdfPath));
            assertTrue(Files.size(pdfPath) > 5000);

            try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
                String text = new PDFTextStripper().getText(doc);
                assertTrue(text.contains("ck_oo_numberofpublicattributes"));
                assertTrue(text.contains("ck_oo_numberofprivateattributes"));
                // Ensure no single characters stranded like in the old screenshot
                assertFalse(text.contains("ck_oo_numberofpublicattribute 2.1371"));
            }
        }
    }
}
