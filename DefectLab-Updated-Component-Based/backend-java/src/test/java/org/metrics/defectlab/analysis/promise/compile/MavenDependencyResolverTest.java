package org.metrics.defectlab.analysis.promise.compile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenDependencyResolverTest {

    @TempDir
    Path workspace;

    @Test
    void doesNothingWhenNoPomXmlIsPresent() throws Exception {
        Path project = Files.createDirectory(workspace.resolve("project"));
        Files.writeString(project.resolve("Plain.java"), "class Plain {}");
        Path output = workspace.resolve("deps");

        List<String> diagnostics = MavenDependencyResolver.resolve(project, output);

        assertTrue(diagnostics.isEmpty());
        assertTrue(Files.notExists(output),
                "no output directory should be created when there is nothing to fetch");
    }

    @Test
    void fetchesDeclaredDependenciesForARealMavenProject() throws Exception {
        Path project = Files.createDirectory(workspace.resolve("project"));
        // commons-io is small, stable, and always on Maven Central, so this
        // exercises a real dependency:copy-dependencies run without pulling in
        // anything large or flaky.
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>fixture</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>commons-io</groupId>
                      <artifactId>commons-io</artifactId>
                      <version>2.16.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Path output = workspace.resolve("deps");

        MavenDependencyResolver.resolve(project, output);

        assertTrue(Files.isDirectory(output));
        try (var files = Files.list(output)) {
            assertTrue(files.anyMatch(file -> file.getFileName().toString()
                    .startsWith("commons-io")), "commons-io.jar should be fetched");
        }
    }
}
