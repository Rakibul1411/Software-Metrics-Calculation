package org.metrics.defectlab.analysis.promise.compile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of compiling a PROMISE release to bytecode.
 *
 * <p>PROMISE metrics are derived from compiled classes, so a class whose source
 * did not compile cleanly must never produce a metric row: the compiler replaces
 * the body of an uncompilable method with {@code throw new Error(...)}, which
 * would silently yield fabricated instruction counts, complexity and coupling.
 * {@link #getFailedSourceFiles()} records those sources so the analyzer can drop
 * exactly the affected classes and report them.
 */
public final class PromiseCompilationOutcome {

    private final List<Path> classDirectories;
    private final Set<Path> failedSourceFiles;
    private final List<String> diagnostics;
    private final PromiseCompilationMetadata metadata;
    private final List<Path> classpathJars;

    PromiseCompilationOutcome(
            List<Path> classDirectories,
            Set<Path> failedSourceFiles,
            List<String> diagnostics,
            PromiseCompilationMetadata metadata,
            List<Path> classpathJars) {
        this.classDirectories = List.copyOf(classDirectories);
        this.failedSourceFiles = Collections.unmodifiableSet(
                new LinkedHashSet<>(failedSourceFiles));
        this.diagnostics = List.copyOf(diagnostics);
        this.metadata = metadata;
        this.classpathJars = List.copyOf(classpathJars);
    }

    public List<Path> getClassDirectories() {
        return classDirectories;
    }

    /**
     * Jars that were on the compile classpath (dependency jars found in the
     * upload, plus any Maven-fetched or bundled legacy-API jars). Used to
     * resolve ancestor classes that live outside the analysed release itself.
     */
    public List<Path> getClasspathJars() {
        return classpathJars;
    }

    /** Source files that produced at least one compile error. */
    public Set<Path> getFailedSourceFiles() {
        return failedSourceFiles;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    public PromiseCompilationMetadata getMetadata() {
        return metadata;
    }
}
