package org.metrics.defectlab.analysis.javaparser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Builds a target-project classpath without leaking the extractor classpath. */
public final class JdtProjectEnvironment {

    private JdtProjectEnvironment() {
    }

    public static String[] collectJarClassPath(
            Path projectRoot,
            Predicate<Path> include) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT)
                            .endsWith(".jar"))
                    .filter(path -> include == null || include.test(path))
                    .filter(JdtProjectEnvironment::isValidJar)
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .forEach(entries::add);
        }
        return entries.toArray(new String[0]);
    }

    private static boolean isValidJar(Path path) {
        try (JarFile ignored = new JarFile(path.toFile())) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
