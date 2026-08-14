package org.metrics.defectlab.analysis.promise.compile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Fetches a Maven project's own declared dependencies into a local directory
 * before compilation, so PROMISE extraction does not require the user to
 * pre-bundle dependency JARs by hand.
 *
 * <p>This runs {@code dependency:copy-dependencies} directly &mdash; not
 * {@code mvn compile}, {@code package}, or {@code test} &mdash; so the
 * project's own build lifecycle, plugins bound to those phases, and any test
 * code never execute. The goal only resolves and downloads the artifact
 * coordinates already declared in the POM.
 *
 * <p>Root-cause context: releases this old were built against Maven
 * repositories that have since gone offline or now sit behind the HTTP
 * mirror block modern Maven applies by default. Some fraction of dependencies
 * for a given release are therefore unrecoverable no matter how this is run.
 * {@code -fae} (fail-at-end) makes the reactor skip only the modules whose own
 * dependencies are unreachable, rather than one bad module stopping every
 * other module's resolution.
 */
public final class MavenDependencyResolver {

    private static final long TIMEOUT_MINUTES = 15;

    private MavenDependencyResolver() {
    }

    /**
     * @param projectRoot the extracted release; searched for the reactor's
     *         top-level {@code pom.xml}
     * @param outputDirectory where downloaded jars are copied
     * @return diagnostics describing what happened, for the caller to surface
     *         alongside compilation diagnostics; empty if no pom.xml was found
     */
    public static List<String> resolve(Path projectRoot, Path outputDirectory) throws IOException {
        Optional<Path> reactorRoot = findReactorRoot(projectRoot);
        if (reactorRoot.isEmpty()) {
            return List.of();
        }

        Files.createDirectories(outputDirectory);
        List<String> command = commandFor(reactorRoot.get(), outputDirectory);

        List<String> diagnostics = new ArrayList<>();
        diagnostics.add("Detected a Maven project (" + reactorRoot.get()
                + "); auto-fetching its declared dependencies before compilation.");
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(reactorRoot.get().toFile())
                    .redirectErrorStream(true);
            Process process = builder.start();
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                diagnostics.add("Maven dependency fetch timed out after "
                        + TIMEOUT_MINUTES + " minutes; continuing with whatever "
                        + "jars were downloaded before the timeout.");
            } else if (process.exitValue() != 0) {
                // -fae still exits non-zero when any single module failed, even
                // though every other module's dependencies were copied fine.
                diagnostics.add("Maven dependency fetch finished with some module "
                        + "failures (this is normal for old multi-module releases "
                        + "with a few now-unreachable dependencies); continuing "
                        + "with whatever jars were successfully downloaded.");
            }
            diagnostics.addAll(summarizeMavenOutput(output));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            diagnostics.add("Maven dependency fetch was interrupted.");
        } catch (IOException exception) {
            diagnostics.add("Maven dependency fetch could not run ("
                    + exception.getMessage() + "); continuing without auto-fetched jars.");
        }
        return diagnostics;
    }

    private static List<String> commandFor(Path reactorRoot, Path outputDirectory) {
        Path wrapper = reactorRoot.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        String executable = Files.isRegularFile(wrapper)
                ? wrapper.toAbsolutePath().toString() : "mvn";
        if (!isWindows() && executable.equals(wrapper.toAbsolutePath().toString())) {
            wrapper.toFile().setExecutable(true);
        }
        return List.of(
                executable,
                "-q",
                "-fae",
                "dependency:copy-dependencies",
                "-DoutputDirectory=" + outputDirectory.toAbsolutePath().normalize(),
                "-DincludeScope=compile",
                "-Dmdep.failOnMissingClassifierArtifact=false");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    /**
     * The top-level {@code pom.xml} closest to the project root. A multi-module
     * reactor resolves every submodule's dependencies from this one entry
     * point, so nested module poms are never searched separately.
     */
    private static Optional<Path> findReactorRoot(Path projectRoot) throws IOException {
        try (var paths = Files.walk(projectRoot, 4)) {
            return paths.filter(path -> path.getFileName() != null
                            && "pom.xml".equals(path.getFileName().toString()))
                    .min(Comparator.comparingInt(Path::getNameCount))
                    .map(Path::getParent);
        }
    }

    private static List<String> summarizeMavenOutput(String output) {
        List<String> lines = new ArrayList<>();
        long banned = output.lines()
                .filter(line -> line.contains("banned from the build"))
                .count();
        if (banned > 0) {
            lines.add(banned + " module(s) were skipped because their own "
                    + "dependencies could not be resolved (likely a dead or "
                    + "relocated repository from the release's era).");
        }
        return lines;
    }
}
