package org.metrics.aeeem.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        Path dottedTestModule = project.resolve(
                "org.example.core.tests.model/src/org/example/tests/ModelFixture.java");
        Path jclStub = project.resolve("JCL/converterJclMin1.8/src/java/io/File.java");
        Path moduleInfo = project.resolve("src/main/java/module-info.java");
        Path invalidJar = project.resolve("legacy/lib/broken.jar");
        Files.createDirectories(production.getParent());
        Files.createDirectories(deprecatedTest.getParent());
        Files.createDirectories(buildTool.getParent());
        Files.createDirectories(dottedTestModule.getParent());
        Files.createDirectories(jclStub.getParent());
        Files.createDirectories(invalidJar.getParent());
        Files.write(production, "package demo; public class Service { public void run() {} }"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(deprecatedTest, ("package demo; import junit.framework.TestCase; "
                + "public class TestService extends TestCase { public void testRun() { assertTrue(true); } }")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(buildTool, ("package demo; import org.gradle.api.DefaultTask; "
                + "public class Checksum extends DefaultTask { }").getBytes(StandardCharsets.UTF_8));
        Files.write(dottedTestModule,
                "package org.example.tests; public class ModelFixture {}"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(jclStub, "package java.io; public class File {}"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(moduleInfo, "module demo.module { exports demo; }".getBytes(StandardCharsets.UTF_8));
        Files.write(invalidJar, "not a zip archive".getBytes(StandardCharsets.UTF_8));

        List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseProject(project);

        assertEquals(1, metrics.size());
        assertEquals("demo.Service", metrics.get(0).getFullyQualifiedName());
    }

    @Test
    void parsesLargeSourceSetsInBoundedBatches() throws Exception {
        Path sourceRoot = project.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        for (int index = 0; index < 20; index++) {
            Path source = sourceRoot.resolve("Service" + index + ".java");
            Files.write(source, ("package demo; public class Service" + index
                    + " { public int value() { return " + index + "; } }")
                    .getBytes(StandardCharsets.UTF_8));
        }

        String previous = System.getProperty("aeeem.jdt.batchSize");
        System.setProperty("aeeem.jdt.batchSize", "16");
        try {
            List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseProject(project);
            assertEquals(20, metrics.size());
            assertEquals("demo.Service0", metrics.get(0).getFullyQualifiedName());
            assertTrue(metrics.stream()
                    .anyMatch(metric -> "demo.Service19".equals(metric.getFullyQualifiedName())));
        } finally {
            restoreProperty("aeeem.jdt.batchSize", previous);
        }
    }

    @Test
    void parsesOnlyTheSelectedRepositoryModule() throws Exception {
        Path selected = project.resolve(
                "org.eclipse.jdt.core/src/demo/Core.java");
        Path sibling = project.resolve(
                "org.eclipse.jdt.apt.core/src/demo/Apt.java");
        Files.createDirectories(selected.getParent());
        Files.createDirectories(sibling.getParent());
        Files.write(selected,
                "package demo; public class Core {}".getBytes(StandardCharsets.UTF_8));
        Files.write(sibling,
                "package demo; public class Apt {}".getBytes(StandardCharsets.UTF_8));

        List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseProject(
                project, project.resolve("org.eclipse.jdt.core"));

        assertEquals(1, metrics.size());
        assertEquals("demo.Core", metrics.get(0).getFullyQualifiedName());
        assertTrue(metrics.get(0).getSourcePath().replace('\\', '/')
                .startsWith("org.eclipse.jdt.core/"));
    }

    @Test
    void appliesTheSameProductionFilterToGitPaths() {
        assertTrue(ProductionSourceSelector.isProductionJavaPath(
                "org.eclipse.jdt.core/model/org/eclipse/jdt/core/JavaCore.java"));
        assertFalse(ProductionSourceSelector.isProductionJavaPath(
                "JCL/converterJclMin1.8/src/java/io/File.java"));
        assertFalse(ProductionSourceSelector.isProductionJavaPath(
                "org.eclipse.jdt.core.tests.model/src/org/eclipse/jdt/tests/ModelTest.java"));
        assertFalse(ProductionSourceSelector.isProductionJavaPath(
                "src/testFixtures/java/demo/Fixture.java"));
        assertTrue(ProductionSourceSelector.isProductionJavaPath(
                "src/main/java/demo/Contest.java"));
        assertTrue(ProductionSourceSelector.isProductionJavaPath(
                "src/main/java/demo/Testament.java"));
    }

    @Test
    void calculatesInterfaceAndNestedTypeLocFromTheirOwnSource() throws Exception {
        Path source = project.resolve("src/main/java/demo/Types.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ("package demo;\n"
                + "interface Worker {\n"
                + "  void work();\n"
                + "}\n"
                + "class Container {\n"
                + "  int value;\n"
                + "  void outer() { class Local { void ignored() {} } value++; }\n"
                + "  static class Nested {\n"
                + "    void nested() { }\n"
                + "  }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));

        List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseProject(project);
        AeeemMetricResult worker = byName(metrics, "demo.Worker");
        AeeemMetricResult container = byName(metrics, "demo.Container");
        AeeemMetricResult nested = byName(metrics, "demo.Container.Nested");

        assertTrue(worker.getCkOoNumberOfLinesOfCode() >= 3d);
        assertEquals(1d, worker.getCkOoWmc(), 1.0e-12);
        assertTrue(container.getCkOoNumberOfLinesOfCode()
                > nested.getCkOoNumberOfLinesOfCode());
        assertEquals(1d, container.getCkOoNumberOfMethods(), 1.0e-12);
        assertEquals(1d, nested.getCkOoNumberOfMethods(), 1.0e-12);
        assertFalse(metrics.stream()
                .anyMatch(metric -> metric.getFullyQualifiedName().endsWith(".Local")));
    }

    private static AeeemMetricResult byName(List<AeeemMetricResult> metrics, String name) {
        return metrics.stream()
                .filter(metric -> name.equals(metric.getFullyQualifiedName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metrics for " + name));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
