package org.metrics.defectlab.analysis.promise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

/**
 * Builds a tiny Java project on disk and runs the real PROMISE pipeline over it,
 * compilation included. Expected values in the tests are derived by hand from
 * the documented rules, never from another metric tool.
 */
public final class PromiseFixtureProject {

    private final Path root;

    private PromiseFixtureProject(Path root) {
        this.root = root;
    }

    public static PromiseFixtureProject in(Path directory) throws IOException {
        Path source = directory.resolve("src");
        Files.createDirectories(source);
        return new PromiseFixtureProject(source);
    }

    public PromiseFixtureProject write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /** Runs extraction and returns the rows keyed by fully qualified name. */
    public Map<String, PromiseMetricResult> analyze() throws IOException {
        List<PromiseMetricResult> results =
                new PromiseProjectAnalyzer().analyze(List.of(root));
        return results.stream().collect(Collectors.toMap(
                PromiseMetricResult::getFullyQualifiedName, Function.identity()));
    }
}
