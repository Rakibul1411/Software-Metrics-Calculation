package org.metrics.defectlab.analysis.promise.compile;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.metrics.defectlab.analysis.javaparser.JavaLanguageConfiguration;
import org.metrics.defectlab.analysis.javaparser.JdtProjectEnvironment;

/**
 * Compiles an extracted PROMISE release to {@code .class} files so the metric
 * calculators can work on bytecode.
 *
 * <p>The Eclipse batch compiler (ECJ) is used in-process rather than the
 * project's own Maven/Gradle/Ant build or {@code javac}. Three properties of the
 * PROMISE corpus drive that decision:
 *
 * <ul>
 *   <li>Classic releases target Java 1.1&ndash;1.4. A modern {@code javac}
 *       refuses those levels outright, while ECJ still accepts them.</li>
 *   <li>Running the project's own build would require that build's toolchain
 *       (Ant is frequently absent) and would execute untrusted build scripts.</li>
 *   <li>ECJ reports errors per source file, which is what lets us drop exactly
 *       the classes that failed instead of emitting fabricated metrics.</li>
 * </ul>
 *
 * <p>Compilation runs with {@code -proceedOnError} so that one unresolvable
 * class does not sink the whole release. That flag makes ECJ replace the body of
 * an affected method with {@code throw new Error(...)}, which would silently
 * destroy instruction counts, complexity and coupling, so every source file that
 * reported an error is recorded in
 * {@link PromiseCompilationOutcome#getFailedSourceFiles()} and its classes are
 * excluded from the metric output.
 */
public final class PromiseCompilationService {

    /** {@code 1 . path : line : message} — the ECJ textual problem header. */
    private static final Pattern PROBLEM_LOCATION =
            Pattern.compile("^\\d+\\.\\s+(ERROR|WARNING) in (.+?)\\s*(\\(at line \\d+\\))?$");

    private final Path workRoot;

    public PromiseCompilationService(Path workRoot) {
        this.workRoot = workRoot;
    }

    public PromiseCompilationOutcome compile(
            Path projectRoot,
            List<Path> javaFiles,
            JavaLanguageConfiguration configuration) throws IOException {

        if (javaFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "PROMISE extraction needs at least one production Java source file.");
        }

        Path classesDirectory = Files.createDirectories(workRoot.resolve("classes"));
        Path argumentFile = workRoot.resolve("sources.txt");
        Files.write(argumentFile, javaFiles.stream()
                .map(file -> file.toAbsolutePath().normalize().toString())
                .toList(), StandardCharsets.UTF_8);

        Path mavenDependencyDirectory = workRoot.resolve("maven-deps");
        List<String> mavenDiagnostics =
                MavenDependencyResolver.resolve(projectRoot, mavenDependencyDirectory);

        List<String> classpath = new ArrayList<>(List.of(
                JdtProjectEnvironment.collectJarClassPath(projectRoot, ignored -> true)));
        if (Files.isDirectory(mavenDependencyDirectory)) {
            classpath.addAll(List.of(JdtProjectEnvironment.collectJarClassPath(
                    mavenDependencyDirectory, ignored -> true)));
        }
        classpath.addAll(legacyJdkApiClasspath());
        List<String> arguments = buildArguments(
                configuration, classesDirectory, classpath, argumentFile);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        BatchCompiler.compile(
                arguments.toArray(new String[0]),
                new PrintWriter(out),
                new PrintWriter(err),
                null);

        String report = out + System.lineSeparator() + err;
        Set<Path> failedSources = parseFailedSources(report);
        List<String> diagnostics = new ArrayList<>(mavenDiagnostics);
        diagnostics.addAll(summarise(failedSources, javaFiles.size()));

        PromiseCompilationMetadata metadata = new PromiseCompilationMetadata(
                "Eclipse JDT batch compiler (ECJ)",
                System.getProperty("java.version"),
                configuration.getSource(),
                configuration.getTarget(),
                javaFiles.size(),
                classpath.size());

        if (!hasClassFiles(classesDirectory)) {
            throw new PromiseCompilationException(
                    "Strict PROMISE extraction could not compile any class from this release. "
                    + "PROMISE metrics are defined on compiled bytecode, so no approximate "
                    + "source-only values are produced. " + metadata.describe()
                    + firstProblems(report));
        }

        return new PromiseCompilationOutcome(
                List.of(classesDirectory), failedSources, diagnostics, metadata,
                classpath.stream().map(Path::of).toList());
    }

    private List<String> buildArguments(
            JavaLanguageConfiguration configuration,
            Path classesDirectory,
            List<String> classpath,
            Path argumentFile) {

        List<String> arguments = new ArrayList<>();
        arguments.add("-source");
        arguments.add(configuration.getSource());
        arguments.add("-target");
        arguments.add(configuration.getTarget());
        arguments.add("-encoding");
        arguments.add(configuration.getCharset().name());
        // Metrics need real bodies: keep every local/line attribute the
        // compiler can emit and never let it optimise instructions away.
        arguments.add("-preserveAllLocals");
        arguments.add("-g");
        arguments.add("-nowarn");
        arguments.add("-proceedOnError");
        arguments.add("-noExit");
        if (!classpath.isEmpty()) {
            arguments.add("-classpath");
            arguments.add(String.join(java.io.File.pathSeparator, classpath));
        }
        arguments.add("-d");
        arguments.add(classesDirectory.toAbsolutePath().normalize().toString());
        arguments.add("@" + argumentFile.toAbsolutePath().normalize());
        return arguments;
    }

    /**
     * JARs for APIs that shipped inside the JDK through Java 6&ndash;10 and were
     * removed in Java 11+ (JAXB, JAF).
     *
     * <p>PROMISE/AEEEM releases predate Java 11 and routinely reference these
     * packages assuming the JRE provides them, since at the time it did. This
     * service always compiles with whatever JDK hosts the running application
     * (17+), so without this, every such reference is an unresolved-import error
     * and the whole class is dropped as uncompilable &mdash; not because of a
     * real missing dependency, but because of a JDK version mismatch between the
     * release's era and ours.
     *
     * <p>The jars are located by asking a class from each artifact where its own
     * code came from, which works whether this application is running from a
     * development classpath or a repackaged Spring Boot fat jar.
     */
    private List<String> legacyJdkApiClasspath() {
        List<String> jars = new ArrayList<>();
        addJarOf(jars, javax.xml.bind.JAXBException.class);
        addJarOf(jars, javax.activation.DataHandler.class);
        return jars;
    }

    private void addJarOf(List<String> jars, Class<?> marker) {
        try {
            Path jar = Path.of(marker.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(jar)) {
                jars.add(jar.toAbsolutePath().normalize().toString());
            }
        } catch (RuntimeException | java.net.URISyntaxException exception) {
            // Best effort: if the jar cannot be located, that legacy API simply
            // is not added to the classpath and affected classes are excluded
            // as usual, rather than failing the whole extraction.
        }
    }

    private Set<Path> parseFailedSources(String report) {
        Set<Path> failed = new LinkedHashSet<>();
        for (String line : report.split("\\R")) {
            Matcher matcher = PROBLEM_LOCATION.matcher(line.trim());
            if (matcher.matches() && "ERROR".equals(matcher.group(1))) {
                failed.add(SourcePaths.canonical(Path.of(matcher.group(2).trim())));
            }
        }
        return failed;
    }

    private List<String> summarise(Set<Path> failedSources, int totalSources) {
        List<String> diagnostics = new ArrayList<>();
        if (failedSources.isEmpty()) {
            return diagnostics;
        }
        diagnostics.add(String.format(Locale.ROOT,
                "%d of %d source files did not compile cleanly. Their classes are excluded "
                + "from the PROMISE output because bytecode metrics cannot be measured on "
                + "an uncompilable method body (usually a missing dependency JAR).",
                failedSources.size(), totalSources));
        failedSources.stream().limit(25).forEach(file ->
                diagnostics.add("Excluded from PROMISE output: " + file));
        if (failedSources.size() > 25) {
            diagnostics.add("... and " + (failedSources.size() - 25) + " more.");
        }
        return diagnostics;
    }

    private String firstProblems(String report) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (String line : report.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("ERROR in ") && shown++ < 5) {
                builder.append(System.lineSeparator()).append("  ").append(trimmed);
            }
        }
        return builder.toString();
    }

    private boolean hasClassFiles(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(path -> path.toString().endsWith(".class"));
        }
    }

    /** Collects every {@code .class} file produced for the release. */
    public static List<Path> collectClassFiles(Collection<Path> directories) throws IOException {
        List<Path> classFiles = new ArrayList<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (var paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .forEach(classFiles::add);
            }
        }
        classFiles.sort(Path::compareTo);
        return classFiles;
    }
}
