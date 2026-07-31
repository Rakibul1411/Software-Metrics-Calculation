package org.metrics.aeeem.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Selects source that belongs to the shipped application rather than tests,
 * compiler fixtures, generated code, examples, or build infrastructure.
 */
public final class ProductionSourceSelector {

    private static final Set<String> EXCLUDED_SEGMENTS = new HashSet<>(Arrays.asList(
            ".git", ".gradle", "build", "build-infra", "build-tools", "buildsrc",
            "dev-tools", "devtools", "example", "examples", "fixture", "fixtures",
            "generated", "generated-sources", "jcl", "mock", "mocks", "out",
            "sample", "samples", "scripts", "stub", "stubs", "target", "test", "tests",
            "testcase", "testcases", "testdata", "test-data", "test-fixtures",
            "testfixtures", "benchmark", "benchmarks"));

    private static final Set<String> COMPOUND_TEST_SEGMENTS = new HashSet<>(Arrays.asList(
            "acceptancetest", "functionaltest", "integrationtest", "performancetest",
            "regressiontest", "systemtest", "unittest"));

    private ProductionSourceSelector() {
    }

    public static java.util.List<Path> collectJavaFiles(Path projectRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> isProductionJavaFile(projectRoot, path))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static boolean isProductionJavaFile(Path projectRoot, Path file) {
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if ("module-info.java".equals(lowerName) || "package-info.java".equals(lowerName)
                || isTestClassFileName(fileName)) {
            return false;
        }
        return !isExcludedPath(projectRoot, file);
    }

    /**
     * Returns whether a repository-relative path represents production code.
     * This overload is used for Git paths that do not exist in the final tree.
     */
    public static boolean isProductionJavaPath(String repositoryRelativePath) {
        if (repositoryRelativePath == null || repositoryRelativePath.trim().isEmpty()) {
            return false;
        }
        String normalized = repositoryRelativePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        String fileName = parts.length == 0 ? "" : parts[parts.length - 1];
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".java") || "module-info.java".equals(lowerName)
                || "package-info.java".equals(lowerName) || isTestClassFileName(fileName)) {
            return false;
        }
        int sourceRootEnd = sourceRootEnd(parts);
        for (int index = 0; index < parts.length - 1; index++) {
            if (isExcludedSegment(parts[index])
                    && (sourceRootEnd < 0 || index < sourceRootEnd)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isExcludedPath(Path projectRoot, Path path) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        Path relative;
        try {
            relative = root.relativize(normalized);
        } catch (IllegalArgumentException exception) {
            relative = normalized;
        }
        String[] parts = new String[relative.getNameCount()];
        for (int index = 0; index < relative.getNameCount(); index++) {
            parts[index] = relative.getName(index).toString();
        }
        int sourceRootEnd = sourceRootEnd(parts);
        for (int index = 0; index < parts.length; index++) {
            if (isExcludedSegment(parts[index])
                    && (sourceRootEnd < 0 || index < sourceRootEnd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first package-directory index after a conventional
     * production source root. Exclusion names before this point identify test
     * modules (for example {@code src/test/java}); the same words after it are
     * legal Java package names (for example {@code src/main/java/a/test/b}).
     */
    private static int sourceRootEnd(String[] parts) {
        for (int index = 0; index < parts.length; index++) {
            String value = parts[index].toLowerCase(Locale.ROOT);
            if ("src".equals(value) && index + 2 < parts.length
                    && "main".equalsIgnoreCase(parts[index + 1])
                    && "java".equalsIgnoreCase(parts[index + 2])) {
                return index + 3;
            }
            if ("src".equals(value) && index + 1 < parts.length
                    && "java".equalsIgnoreCase(parts[index + 1])) {
                return index + 2;
            }
            if ("source".equals(value) || "src".equals(value)) {
                return index + 1;
            }
        }
        return -1;
    }

    private static boolean isExcludedSegment(String segment) {
        String value = segment.toLowerCase(Locale.ROOT);
        if (EXCLUDED_SEGMENTS.contains(value) || COMPOUND_TEST_SEGMENTS.contains(value)) {
            return true;
        }
        for (String token : value.split("[._-]+")) {
            if ("test".equals(token) || "tests".equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestClassFileName(String fileName) {
        if (!fileName.endsWith(".java")) {
            return false;
        }
        String typeName = fileName.substring(0, fileName.length() - ".java".length());
        if (typeName.endsWith("Test") || typeName.endsWith("Tests")) {
            return true;
        }
        if (!typeName.startsWith("Test")) {
            return false;
        }
        return typeName.length() == "Test".length()
                || !Character.isLowerCase(typeName.charAt("Test".length()));
    }
}
