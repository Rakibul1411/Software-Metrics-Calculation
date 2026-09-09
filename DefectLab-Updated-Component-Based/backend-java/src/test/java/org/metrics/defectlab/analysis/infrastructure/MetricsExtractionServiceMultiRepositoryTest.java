package org.metrics.defectlab.analysis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.shared.model.DatasetFileFormat;

/**
 * Covers the Mylyn-style case: a benchmark whose history is split across
 * several component repositories, one of which (releng/build infrastructure)
 * legitimately contains no production classes.
 */
class MetricsExtractionServiceMultiRepositoryTest {

    @TempDir
    Path root;

    @Test
    void combinesProductionClassesAcrossComponentsAndSkipsAnEmptyOne() throws Exception {
        Path emptyRepository = root.resolve("top-level");
        Path componentRepository = root.resolve("component");
        Files.createDirectories(emptyRepository);
        Files.createDirectories(componentRepository);

        initRepository(emptyRepository);
        Path testOnly = emptyRepository.resolve("tests/DemoTest.java");
        Files.createDirectories(testOnly.getParent());
        Files.write(testOnly, "class DemoTest {}".getBytes(StandardCharsets.UTF_8));
        commit(emptyRepository, "2005-01-01T00:00:00Z", "history start");
        Files.write(testOnly, "class DemoTest { int probe; }".getBytes(StandardCharsets.UTF_8));
        commit(emptyRepository, "2005-01-20T00:00:00Z", "still no production code");

        initRepository(componentRepository);
        Path source = componentRepository.resolve("src/demo/Service.java");
        Files.createDirectories(source.getParent());
        Files.write(source, "class Service {}".getBytes(StandardCharsets.UTF_8));
        commit(componentRepository, "2005-01-01T00:00:00Z", "history start");
        Files.write(source, "class Service { int value; }".getBytes(StandardCharsets.UTF_8));
        commit(componentRepository, "2005-01-20T00:00:00Z", "add field");

        MetricsExtractionService service = new MetricsExtractionService();
        MetricsExtractionService.ExtractionResult combined = service.extractMetrics(
                emptyRepository + "," + componentRepository, "aeeem", null);
        try {
            assertEquals(1, combined.getRowCount());
        } finally {
            cleanup(service, combined);
        }
    }

    @Test
    void aSingleEmptyRepositoryStillFailsLoudly() {
        Path emptyRepository = root.resolve("top-level-only");
        assertThrows(IllegalArgumentException.class, () -> {
            Files.createDirectories(emptyRepository);
            initRepository(emptyRepository);
            Path testOnly = emptyRepository.resolve("tests/DemoTest.java");
            Files.createDirectories(testOnly.getParent());
            Files.write(testOnly, "class DemoTest {}".getBytes(StandardCharsets.UTF_8));
            commit(emptyRepository, "2005-01-01T00:00:00Z", "history start");
            Files.write(testOnly, "class DemoTest { int probe; }".getBytes(StandardCharsets.UTF_8));
            commit(emptyRepository, "2005-01-20T00:00:00Z", "still no production code");

            new MetricsExtractionService().extractMetrics(
                    emptyRepository.toString(), "aeeem", null);
        });
    }

    private void initRepository(Path repository) throws Exception {
        git(repository, null, "init");
        git(repository, null, "config", "user.email", "metrics@example.com");
        git(repository, null, "config", "user.name", "Metrics Test");
    }

    private void commit(Path repository, String date, String message) throws Exception {
        git(repository, null, "add", ".");
        git(repository, date, "commit", "-m", message);
    }

    private void cleanup(
            MetricsExtractionService service,
            MetricsExtractionService.ExtractionResult result) throws IOException {
        Files.deleteIfExists(service.getDatasetPath(
                result.getTargetDatasetId(), DatasetFileFormat.CSV));
        Files.deleteIfExists(service.getDatasetPath(
                result.getTargetDatasetId(), DatasetFileFormat.ARFF));
    }

    private void git(Path repository, String date, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(repository.toFile())
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
