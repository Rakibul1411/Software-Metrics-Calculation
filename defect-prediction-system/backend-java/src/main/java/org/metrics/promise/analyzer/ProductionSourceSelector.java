package org.metrics.promise.analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Selects product source files and excludes tests, generators, and bundled tools. */
final class ProductionSourceSelector {

    private static final Set<String> NON_PRODUCT_SEGMENTS = new HashSet<>(Arrays.asList(
            ".git", ".gradle", "benchmark", "benchmarks", "build",
            "demo", "demos", "example", "examples", "generated",
            "generated-sources", "node_modules", "out", "sample", "samples",
            "target", "test", "tests", "testcase", "testcases"
    ));

    private static final Set<String> CAMEL_1_0_EXCLUDED_MODULES = new HashSet<>(Arrays.asList(
            "camel-ftp", "camel-irc", "camel-jpa", "camel-saxon", "tooling"
    ));

    List<Path> select(Path requestedRoot) throws IOException {
        Path root = requestedRoot.toAbsolutePath().normalize();
        List<Path> allFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            allFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .collect(Collectors.toList());
        }

        boolean hasConventionalMainSources = allFiles.stream()
                .anyMatch(this::isUnderMainSourceRoot);
        List<Path> selected = new ArrayList<>();
        for (Path file : allFiles) {
            if (hasConventionalMainSources && !isUnderMainSourceRoot(file)) {
                continue;
            }
            if (containsNonProductSegment(file) || excludedFromPromiseRelease(file)) {
                continue;
            }
            selected.add(file);
        }
        selected.sort(Path::compareTo);
        return selected;
    }

    private boolean isUnderMainSourceRoot(Path file) {
        List<String> segments = segments(file);
        for (int index = 0; index + 1 < segments.size(); index++) {
            if ("src".equals(segments.get(index)) && "main".equals(segments.get(index + 1))) {
                return index + 2 >= segments.size() || !"resources".equals(segments.get(index + 2));
            }
        }
        return false;
    }

    private boolean containsNonProductSegment(Path file) {
        List<String> segments = segments(file);
        int sourceRootEnd = sourceRootEnd(segments);
        int limit = sourceRootEnd < 0 ? segments.size() : sourceRootEnd;
        for (int index = 0; index < limit; index++) {
            if (NON_PRODUCT_SEGMENTS.contains(segments.get(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean excludedFromPromiseRelease(Path file) throws IOException {
        List<String> segments = segments(file);

        if (isApacheAntSource(segments) && excludedFromAntCoreArtifact(segments)) {
            return true;
        }

        if (segments.contains("camel-camel-1.0.0")) {
            for (String module : CAMEL_1_0_EXCLUDED_MODULES) {
                if (segments.contains(module)) {
                    return true;
                }
            }
        }

        if (isJeditSourceTree(file)
                && (segments.contains("installer") || segments.contains("jars"))) {
            return true;
        }

        return isJedit32ReflectManager(file, segments);
    }

    private boolean isApacheAntSource(List<String> segments) {
        return containsSequence(segments, "org", "apache", "tools", "ant");
    }

    private boolean excludedFromAntCoreArtifact(List<String> segments) {
        if (segments.contains("optional")
                || containsSequence(segments, "ant", "launch")
                || containsSequence(segments, "ant", "util", "depend")) {
            return true;
        }

        String fileName = lastSegment(segments);
        if (containsSequence(segments, "ant", "listener")) {
            return "commonslogginglistener.java".equals(fileName)
                    || "log4jlistener.java".equals(fileName);
        }
        if (containsSequence(segments, "ant", "taskdefs", "email")) {
            return "mimemailer.java".equals(fileName);
        }
        if (containsSequence(segments, "ant", "util", "regexp")) {
            return fileName.startsWith("jakarta") || fileName.startsWith("jdk14");
        }
        return "filescanner.java".equals(fileName)
                || (segments.contains("jakarta-ant-1.3")
                    && "sendemail.java".equals(fileName));
    }

    private boolean isJedit32ReflectManager(Path file, List<String> segments) throws IOException {
        if (!endsWith(segments, "bsh", "reflect", "reflectmanagerimpl.java")) {
            return false;
        }

        Path current = file.getParent();
        while (current != null) {
            Path versionSource = current.resolve("org/gjt/sp/jedit/jEdit.java");
            if (Files.isRegularFile(versionSource)) {
                String source = new String(Files.readAllBytes(versionSource), StandardCharsets.UTF_8);
                return source.contains("03.02.99.00");
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isJeditSourceTree(Path file) {
        Path current = file.getParent();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("org/gjt/sp/jedit/jEdit.java"))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private int sourceRootEnd(List<String> segments) {
        for (int index = 0; index + 1 < segments.size(); index++) {
            if ("src".equals(segments.get(index)) && "main".equals(segments.get(index + 1))) {
                if (index + 2 < segments.size() && "java".equals(segments.get(index + 2))) {
                    return index + 3;
                }
                return index + 2;
            }
        }
        return -1;
    }

    private boolean endsWith(List<String> segments, String... suffix) {
        if (segments.size() < suffix.length) {
            return false;
        }
        int offset = segments.size() - suffix.length;
        for (int index = 0; index < suffix.length; index++) {
            if (!suffix[index].equals(segments.get(offset + index))) {
                return false;
            }
        }
        return true;
    }

    private boolean containsSequence(List<String> segments, String... sequence) {
        if (segments.size() < sequence.length) {
            return false;
        }
        for (int offset = 0; offset <= segments.size() - sequence.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < sequence.length; index++) {
                if (!sequence[index].equals(segments.get(offset + index))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private String lastSegment(List<String> segments) {
        return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
    }

    private List<String> segments(Path path) {
        List<String> result = new ArrayList<>();
        for (Path segment : path.toAbsolutePath().normalize()) {
            result.add(segment.toString().toLowerCase(Locale.ROOT));
        }
        return result;
    }

}
