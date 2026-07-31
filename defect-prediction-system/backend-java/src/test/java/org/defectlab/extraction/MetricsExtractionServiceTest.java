package org.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.common.enums.DatasetFileFormat;

class MetricsExtractionServiceTest {

    @TempDir
    Path projectDirectory;

    @Test
    void extractsLabelFreePromiseAndAeeemTargets() throws Exception {
        Path source = projectDirectory.resolve("src/main/java/demo/PaymentService.java");
        Path helper = projectDirectory.resolve("src/main/java/demo/PaymentValidator.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ("package demo;\n" +
                "public class PaymentService {\n" +
                "  private int attempts;\n" +
                "  public boolean pay(int amount) { return amount > 0; }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));
        Files.write(helper, ("package demo;\n"
                + "public class PaymentValidator {\n"
                + "  public boolean valid(int amount) { return amount > 0; }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));
        git("2000-01-01T00:00:00Z", "init");
        git(null, "config", "user.email", "metrics@example.com");
        git(null, "config", "user.name", "Metrics Test");
        git(null, "add", ".");
        git("2000-01-01T00:00:00Z", "commit", "-m", "initial");
        Files.write(source, ("package demo;\n" +
                "public class PaymentService {\n" +
                "  private int attempts;\n" +
                "  public boolean pay(int amount) { if (amount > 0) { attempts++; return true; } return false; }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));
        Files.write(helper, ("package demo;\n"
                + "public class PaymentValidator {\n"
                + "  private int checks;\n"
                + "  public boolean valid(int amount) { checks++; return amount > 0; }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));
        git(null, "add", ".");
        git("2000-01-16T00:00:00Z", "commit", "-m", "change payment logic");

        MetricsExtractionService service = new MetricsExtractionService();
        MetricsExtractionService.ExtractionResult promise = service.extractMetrics(
                projectDirectory.toString(), "promise", null);
        MetricsExtractionService.ExtractionResult aeeem = service.extractMetrics(
                projectDirectory.toString(), "aeeem", null);
        try {
            assertEquals(2, promise.getRowCount());
            assertEquals(2, aeeem.getRowCount());
            assertTrue(promise.getExtractedColumns().contains("wmc"));
            assertTrue(aeeem.getExtractedColumns().contains("ck_oo_wmc"));
            assertEquals("name,wmc,dit,noc,cbo,rfc,lcom,ca,ce,npm,lcom3,loc,dam,moa,mfa,cam,ic,cbm,amc,max_cc,avg_cc",
                    promise.getCsvPreview().get(0));
            assertFalse(promise.getExtractedColumns().contains("bug"));
            assertFalse(promise.getCsvPreview().get(0).contains("bug"));
            assertFalse(aeeem.getExtractedColumns().contains("class"));
            assertEquals(57, aeeem.getExtractedColumns().size());
            assertTrue(aeeem.getExtractedColumns().contains("CvsWEntropy"));
            assertTrue(aeeem.getExtractedColumns().contains("CvsEntropy"));
            assertTrue(aeeem.getExtractedColumns().contains("CvsLogEntropy"));
            assertTrue(aeeem.getExtractedColumns().contains("CvsLinEntropy"));
            assertTrue(aeeem.getExtractedColumns().contains("CvsExpEntropy"));
            assertEquals(promise.getRowCount() + 1, promise.getCsvPreview().size());
            assertEquals(aeeem.getRowCount() + 1, aeeem.getCsvPreview().size());
            assertTrue(aeeem.getCsvPreview().get(1).startsWith("demo.PaymentService,"));
            assertTrue(aeeem.getCsvPreview().stream()
                    .anyMatch(row -> row.startsWith("demo.PaymentValidator,")));
            assertTrue(promise.getCsvDownloadUrl().endsWith("/csv"));
            assertTrue(promise.getArffDownloadUrl().endsWith("/arff"));
            Path promiseArff = service.getDatasetPath(promise.getTargetDatasetId(), DatasetFileFormat.ARFF);
            Path aeeemArff = service.getDatasetPath(aeeem.getTargetDatasetId(), DatasetFileFormat.ARFF);
            assertTrue(Files.exists(promiseArff));
            assertTrue(Files.exists(aeeemArff));
            String promiseArffText = new String(Files.readAllBytes(promiseArff), StandardCharsets.UTF_8);
            String aeeemArffText = new String(Files.readAllBytes(aeeemArff), StandardCharsets.UTF_8);
            assertTrue(promiseArffText.contains("@attribute 'wmc' numeric"));
            assertTrue(promiseArffText.contains("'demo.PaymentService'"));
            assertTrue(aeeemArffText.contains("@attribute 'ck_oo_wmc' numeric"));
            assertFalse(aeeemArffText.contains("@attribute 'class'"));
        } finally {
            Files.deleteIfExists(service.getDatasetPath(promise.getTargetDatasetId(), DatasetFileFormat.CSV));
            Files.deleteIfExists(service.getDatasetPath(aeeem.getTargetDatasetId(), DatasetFileFormat.CSV));
            Files.deleteIfExists(service.getDatasetPath(promise.getTargetDatasetId(), DatasetFileFormat.ARFF));
            Files.deleteIfExists(service.getDatasetPath(aeeem.getTargetDatasetId(), DatasetFileFormat.ARFF));
        }
    }

    private void git(String date, String... arguments) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(projectDirectory.toFile())
                .redirectErrorStream(true);
        if (date != null) {
            Map<String, String> environment = builder.environment();
            environment.put("GIT_AUTHOR_DATE", date);
            environment.put("GIT_COMMITTER_DATE", date);
        }
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IllegalStateException(new String(output, StandardCharsets.UTF_8));
        }
    }
}
