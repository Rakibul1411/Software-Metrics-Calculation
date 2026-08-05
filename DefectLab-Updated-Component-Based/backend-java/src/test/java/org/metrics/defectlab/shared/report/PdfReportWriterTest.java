package org.metrics.defectlab.shared.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfReportWriterTest {

    @TempDir
    Path directory;

    @Test
    void writesAReadablePdfReport() throws Exception {
        Path report = directory.resolve("report.pdf");

        PdfReportWriter.write(report, "DefectLab Test Report",
                List.of("Source: demo", "", "1 | demo.Class | 0.91 | 1"));

        assertTrue(Files.size(report) > 100);
        try (PDDocument document = PDDocument.load(report.toFile())) {
            assertEquals(1, document.getNumberOfPages());
        }
    }
}
