package org.metrics.defectlab.analysis.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserConfigurationResolverTest {

    @TempDir
    Path project;

    @Test
    void readsInheritedMavenCompilerProperties() throws Exception {
        write("pom.xml",
                "<project><modelVersion>4.0.0</modelVersion><properties>"
                + "<maven.compiler.source>1.5</maven.compiler.source>"
                + "<maven.compiler.target>1.5</maven.compiler.target>"
                + "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>"
                + "</properties></project>");
        write("module/pom.xml",
                "<project><modelVersion>4.0.0</modelVersion></project>");
        Path source = write("module/src/main/java/demo/Service.java",
                "package demo; public class Service { }");

        JavaLanguageConfiguration configuration =
                resolve(source).configurationFor(source);

        assertEquals("1.5", configuration.getSource());
        assertEquals("1.5", configuration.getCompliance());
        assertEquals("1.5", configuration.getTarget());
    }

    @Test
    void givesNearestEclipsePreferencesHighestPriority() throws Exception {
        write("module/.settings/org.eclipse.jdt.core.prefs",
                "org.eclipse.jdt.core.compiler.source=1.3\n"
                + "org.eclipse.jdt.core.compiler.compliance=1.4\n"
                + "org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.2\n");
        Path source = write("module/src/demo/Service.java",
                "package demo; public class Service { }");

        JavaLanguageConfiguration configuration =
                resolve(source).configurationFor(source);

        assertEquals("1.3", configuration.getSource());
        assertEquals("1.4", configuration.getCompliance());
        assertEquals("1.2", configuration.getTarget());
    }

    @Test
    void readsAntPropertiesAndKeepsTheOldestZeroErrorFallback() throws Exception {
        write("build.properties", "javac.source=1.4\njavac.target=1.2\n");
        write("build.xml",
                "<project><property file=\"build.properties\"/>"
                + "<javac source=\"${javac.source}\" "
                + "target=\"${javac.target}\"/></project>");
        Path configuredSource = write("src/demo/Configured.java",
                "package demo; public class Configured { }");

        JavaLanguageConfiguration configured =
                resolve(configuredSource).configurationFor(configuredSource);

        assertEquals("1.4", configured.getSource());
        assertEquals("1.2", configured.getTarget());

        Path plainProject = Files.createDirectory(project.resolve("plain"));
        Path plainSource = plainProject.resolve("src/demo/Plain.java");
        Files.createDirectories(plainSource.getParent());
        Files.write(plainSource,
                "package demo; public class Plain { }"
                        .getBytes(StandardCharsets.UTF_8));
        ResolvedJavaProject detected = JavaParserConfigurationResolver.resolve(
                plainProject,
                Collections.singletonList(plainSource),
                null);
        assertEquals("1.3",
                detected.configurationFor(plainSource).getSource());
    }

    private ResolvedJavaProject resolve(Path source) throws Exception {
        return JavaParserConfigurationResolver.resolve(
                project,
                Collections.singletonList(source),
                null);
    }

    private Path write(String relativePath, String value) throws Exception {
        Path file = project.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, value.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
