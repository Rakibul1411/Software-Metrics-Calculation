package org.metrics.promise.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

/** Prevents a collection archive from being silently parsed as one release. */
public final class PromiseInputValidator {

    /**
     * Release archives sit at the top of a collection upload. Fixture archives
     * shipped inside a real release (Ant's {@code src/etc/testcases}, for
     * example) are nested far deeper, so only shallow entries are counted.
     */
    private static final int MAX_RELEASE_ARCHIVE_DEPTH = 2;

    private PromiseInputValidator() {
    }

    public static void requireSingleRelease(Collection<Path> roots) throws IOException {
        int shallowArchives = 0;
        int javaFiles = 0;
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            Path base = root.toAbsolutePath().normalize();
            try (Stream<Path> files = Files.walk(base)) {
                java.util.Iterator<Path> iterator = files
                        .filter(Files::isRegularFile)
                        .iterator();
                while (iterator.hasNext()) {
                    Path file = iterator.next().toAbsolutePath().normalize();
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".java")) {
                        javaFiles++;
                    } else if (isArchiveName(name)
                            && base.relativize(file).getNameCount() <= MAX_RELEASE_ARCHIVE_DEPTH) {
                        shallowArchives++;
                    }
                }
            }
        }

        // A real release upload always exposes parseable Java source. Only an
        // upload with no source at all is a collection of release archives.
        if (javaFiles == 0 && shallowArchives > 0) {
            throw new IllegalArgumentException(shallowArchives > 1
                    ? "This PROMISE upload contains multiple release archives and no Java "
                      + "source. Extract the collection and upload one project release "
                      + "(for example camel-1.0 or lucene-2.4) at a time."
                    : "The uploaded PROMISE archive contains another project archive "
                      + "but no directly parseable Java source. Upload the inner "
                      + "project-release archive.");
        }
    }

    private static boolean isArchiveName(String name) {
        return name.endsWith(".zip") || name.endsWith(".tar.gz") || name.endsWith(".tgz");
    }
}
