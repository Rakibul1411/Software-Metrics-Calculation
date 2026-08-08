package org.metrics.defectlab.analysis.promise.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

/**
 * Resolves the bytecode of a class that was not compiled as part of the
 * analysed release: a JDK class, or a class from a dependency JAR on the
 * compile classpath.
 *
 * <p>CKJM resolves ancestor classes the same way &mdash; BCEL's
 * {@code Repository}, backed by the full system classpath, is what
 * {@code JavaClass.getSuperClasses()} uses internally &mdash; so its DIT, MFA,
 * IC and CBM all see the true superclass chain up through the JDK. Without
 * this resolver, any class whose immediate ancestor lies outside the uploaded
 * project (which is most classes extending anything in {@code java.*}) has its
 * hierarchy walk truncated right at that boundary, undercounting all four
 * metrics.
 *
 * <p>Resolution is best-effort and cached: a class that cannot be found (a
 * missing dependency jar, a JDK internal module) is remembered as absent so it
 * is not looked up again, and the hierarchy walk simply stops there, exactly
 * as it did before this resolver existed.
 */
final class ExternalAncestorResolver {

    private final List<Path> classpathJars;
    private final Map<String, Optional<JavaClass>> cache = new HashMap<>();

    ExternalAncestorResolver(List<Path> classpathJars) {
        this.classpathJars = List.copyOf(classpathJars);
    }

    Optional<JavaClass> resolve(String binaryName) {
        return cache.computeIfAbsent(binaryName, this::load);
    }

    private Optional<JavaClass> load(String binaryName) {
        Optional<JavaClass> fromJdk = loadFromJdk(binaryName);
        return fromJdk.isPresent() ? fromJdk : loadFromClasspath(binaryName);
    }

    /**
     * The JDK's own classes are resolved via the running JVM's system
     * classloader, which serves {@code java.base} resources uniformly across
     * JDK 8 through the latest release without needing the {@code jrt:}
     * filesystem API directly.
     */
    private Optional<JavaClass> loadFromJdk(String binaryName) {
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(resource)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(new ClassParser(stream, binaryName).parse());
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<JavaClass> loadFromClasspath(String binaryName) {
        String entryName = binaryName.replace('.', '/') + ".class";
        for (Path jar : classpathJars) {
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                ZipEntry entry = jarFile.getEntry(entryName);
                if (entry == null) {
                    continue;
                }
                try (InputStream stream = jarFile.getInputStream(entry)) {
                    return Optional.of(new ClassParser(stream, binaryName).parse());
                }
            } catch (IOException | RuntimeException exception) {
                // Try the next jar; one unreadable archive should not block
                // resolution from the others.
            }
        }
        return Optional.empty();
    }
}
