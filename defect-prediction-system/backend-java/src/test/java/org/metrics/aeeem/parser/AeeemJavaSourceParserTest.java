package org.metrics.aeeem.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.aeeem.model.AeeemMetricResult;

class AeeemJavaSourceParserTest {

    @TempDir
    Path project;

    @Test
    void ignoresDeprecatedTestsAndInvalidBundledJars() throws Exception {
        Path production = project.resolve("src/main/java/demo/Service.java");
        Path deprecatedTest = project.resolve("src/test-deprecated/demo/TestService.java");
        Path buildTool = project.resolve("build-tools/build-infra/src/main/java/demo/Checksum.java");
        Path moduleInfo = project.resolve("src/main/java/module-info.java");
        Path invalidJar = project.resolve("legacy/lib/broken.jar");
        Files.createDirectories(production.getParent());
        Files.createDirectories(deprecatedTest.getParent());
        Files.createDirectories(buildTool.getParent());
        Files.createDirectories(invalidJar.getParent());
        Files.write(production, "package demo; public class Service { public void run() {} }"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(deprecatedTest, ("package demo; import junit.framework.TestCase; "
                + "public class TestService extends TestCase { public void testRun() { assertTrue(true); } }")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(buildTool, ("package demo; import org.gradle.api.DefaultTask; "
                + "public class Checksum extends DefaultTask { }").getBytes(StandardCharsets.UTF_8));
        Files.write(moduleInfo, "module demo.module { exports demo; }".getBytes(StandardCharsets.UTF_8));
        Files.write(invalidJar, "not a zip archive".getBytes(StandardCharsets.UTF_8));

        List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseProject(project);

        assertEquals(1, metrics.size());
        assertEquals("demo.Service", metrics.get(0).getFullyQualifiedName());
    }
}
