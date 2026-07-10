package org.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetricsExtractionServiceTest {

    @TempDir
    Path projectDirectory;

    @Test
    void extractsLabelFreePromiseAndAeeemTargets() throws Exception {
        Path source = projectDirectory.resolve("src/main/java/demo/PaymentService.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ("package demo;\n" +
                "public class PaymentService {\n" +
                "  private int attempts;\n" +
                "  public boolean pay(int amount) { if (amount > 0) return true; return false; }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));

        MetricsExtractionService service = new MetricsExtractionService();
        MetricsExtractionService.ExtractionResult promise = service.extractMetrics(
                projectDirectory.toString(), "promise", null);
        MetricsExtractionService.ExtractionResult aeeem = service.extractMetrics(
                projectDirectory.toString(), "aeeem", null);
        try {
            assertEquals(1, promise.getRowCount());
            assertEquals(1, aeeem.getRowCount());
            assertTrue(promise.getExtractedColumns().contains("wmc"));
            assertTrue(aeeem.getExtractedColumns().contains("ck_oo_wmc"));
            assertEquals("name,wmc,dit,noc,cbo,rfc,lcom,ca,ce,npm,lcom3,loc,dam,moa,mfa,cam,ic,cbm,amc,max_cc,avg_cc",
                    promise.getCsvPreview().get(0));
            assertFalse(promise.getExtractedColumns().contains("bug"));
            assertFalse(promise.getCsvPreview().get(0).contains("bug"));
            assertFalse(aeeem.getExtractedColumns().contains("class"));
            assertFalse(aeeem.getExtractedColumns().stream().anyMatch(column -> column.startsWith("Cvs")));
            assertEquals(promise.getRowCount() + 1, promise.getCsvPreview().size());
            assertEquals(aeeem.getRowCount() + 1, aeeem.getCsvPreview().size());
            assertTrue(aeeem.getCsvPreview().get(1).startsWith("demo.PaymentService,"));
        } finally {
            Files.deleteIfExists(service.getDatasetPath(promise.getTargetDatasetId()));
            Files.deleteIfExists(service.getDatasetPath(aeeem.getTargetDatasetId()));
        }
    }
}
