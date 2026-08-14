package org.metrics.defectlab.analysis.promise.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.metrics.defectlab.analysis.javaparser.JavaLanguageConfiguration;
import org.metrics.defectlab.analysis.javaparser.JavaParserConfigurationResolver;
import org.metrics.defectlab.analysis.javaparser.ResolvedJavaProject;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeProjectAnalyzer;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeProjectModel;
import org.metrics.defectlab.analysis.promise.calculator.PromiseMetricCalculator;
import org.metrics.defectlab.analysis.promise.compile.PromiseCompilationException;
import org.metrics.defectlab.analysis.promise.compile.PromiseCompilationOutcome;
import org.metrics.defectlab.analysis.promise.compile.PromiseCompilationService;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

/**
 * Extracts the 20 PROMISE features from a Java release.
 *
 * <p>The pipeline is source in, bytecode out:
 *
 * <pre>
 *   production sources -> ECJ compilation -> .class files
 *                      -> BytecodeProjectModel -> metric calculators -> rows
 * </pre>
 *
 * <p>PROMISE metrics such as LOC, AMC and the cyclomatic complexities are
 * defined on compiled binary code, so bytecode is the authoritative source for
 * every emitted value. The user still uploads source only; compilation is an
 * internal step.
 *
 * <p>When a source file does not compile the compiler leaves a class behind
 * whose method bodies just throw, which would read as real but meaningless
 * metrics. Those classes are therefore dropped from the output and reported,
 * rather than being measured.
 */
public final class PromiseProjectAnalyzer {

    private final List<String> diagnostics = new ArrayList<>();

    public static List<PromiseMetricResult> analyzeDirectories(
            Collection<Path> sourceDirectories) throws IOException {
        return new PromiseProjectAnalyzer().analyze(sourceDirectories);
    }

    public List<String> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public List<PromiseMetricResult> analyze(
            Collection<Path> sourceDirectories) throws IOException {

        List<Path> javaFiles = collectJavaFiles(sourceDirectories);
        if (javaFiles.isEmpty()) {
            return List.of();
        }

        Path projectRoot = commonProjectRoot(sourceDirectories);
        JavaLanguageConfiguration configuration =
                resolveConfiguration(projectRoot, javaFiles);

        Path workRoot = Files.createTempDirectory("defectlab-promise-");
        try {
            PromiseCompilationOutcome compilation =
                    new PromiseCompilationService(workRoot)
                            .compile(projectRoot, javaFiles, configuration);
            diagnostics.addAll(compilation.getDiagnostics());
            diagnostics.add("PROMISE compilation: "
                    + compilation.getMetadata().describe());

            PromiseSourceIndex sourceIndex =
                    PromiseSourceIndex.build(javaFiles, configuration);
            Set<String> rowClasses = sourceIndex.rowEligibleClassNames(
                    compilation.getFailedSourceFiles());
            if (rowClasses.isEmpty()) {
                throw new PromiseCompilationException(
                        "No class of this release compiled cleanly, so no PROMISE metric "
                        + "can be measured. This usually means the release needs dependency "
                        + "JARs that are not bundled with the upload.");
            }

            List<Path> classFiles = PromiseCompilationService.collectClassFiles(
                    compilation.getClassDirectories());
            BytecodeProjectAnalyzer bytecodeAnalyzer = new BytecodeProjectAnalyzer();
            BytecodeProjectModel model = bytecodeAnalyzer.analyze(
                    classFiles, rowClasses, compilation.getClasspathJars());
            diagnostics.addAll(bytecodeAnalyzer.getDiagnostics());

            List<PromiseMetricResult> results = PromiseMetricCalculator.calculate(model);
            reportDiagnostics();
            return results;
        } finally {
            deleteRecursively(workRoot);
        }
    }

    private JavaLanguageConfiguration resolveConfiguration(
            Path projectRoot, List<Path> javaFiles) throws IOException {
        ResolvedJavaProject resolved =
                JavaParserConfigurationResolver.resolve(projectRoot, javaFiles, null);
        resolved.getDiagnostics().forEach(diagnostics::add);
        return resolved.getFilesByConfiguration().keySet().stream()
                .findFirst()
                .orElseGet(() -> JavaLanguageConfiguration.uniform("1.8", "default"));
    }

    private List<Path> collectJavaFiles(Collection<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        ProductionSourceSelector selector = new ProductionSourceSelector();
        for (Path root : roots) {
            if (root != null && Files.isDirectory(root)) {
                files.addAll(selector.select(root));
            }
        }
        files.sort(Path::compareTo);
        return files;
    }

    private Path commonProjectRoot(Collection<Path> requestedRoots) {
        Path result = null;
        for (Path requestedRoot : requestedRoots) {
            if (requestedRoot == null || !Files.isDirectory(requestedRoot)) {
                continue;
            }
            Path normalized = requestedRoot.toAbsolutePath().normalize();
            if (result == null) {
                result = normalized;
                continue;
            }
            while (result != null && !normalized.startsWith(result)) {
                result = result.getParent();
            }
        }
        if (result == null) {
            throw new IllegalArgumentException(
                    "At least one PROMISE source directory is required.");
        }
        return result;
    }

    private void reportDiagnostics() {
        diagnostics.forEach(message ->
                System.err.println("PROMISE metric warning: " + message));
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A leftover temp file must not fail an otherwise good run.
                }
            });
        } catch (IOException ignored) {
            // Same reasoning: cleanup is best effort.
        }
    }
}
