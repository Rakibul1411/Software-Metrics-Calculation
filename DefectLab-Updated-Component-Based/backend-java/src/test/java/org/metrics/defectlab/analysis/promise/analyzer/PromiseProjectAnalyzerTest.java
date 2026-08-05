package org.metrics.defectlab.analysis.promise.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

class PromiseProjectAnalyzerTest {

    @TempDir
    Path project;

    @Test
    void calculatesPromiseMetricsWithJdtBindings() throws Exception {
        write("src/main/java/fixture/Helper.java",
                "package fixture;\n" +
                "public class Helper {\n" +
                "public void touch() { }\n" +
                "}\n");
        write("src/main/java/fixture/Collaborator.java",
                "package fixture;\n" +
                "public class Collaborator {\n" +
                "public void work() { }\n" +
                "}\n");
        write("src/main/java/fixture/Base.java",
                "package fixture;\n" +
                "public class Base {\n" +
                "protected Helper helper;\n" +
                "public int baseValue;\n" +
                "public Base() { }\n" +
                "public void inherited() { helper.touch(); }\n" +
                "protected void template() { }\n" +
                "}\n");
        write("src/main/java/fixture/Child.java",
                "package fixture;\n" +
                "import java.util.List;\n" +
                "public class Child extends Base {\n" +
                "private Helper helper;\n" +
                "protected Collaborator collaborator;\n" +
                "public int count;\n" +
                "List<Helper> helpers;\n" +
                "public Child() { super(); }\n" +
                "public void setHelper(Helper helper) { this.helper = helper; }\n" +
                "public int compute(int value, Collaborator other) { if (value > 0 && other != null) { helper.touch(); inherited(); return value; } else { for (int i = 0; i < value; i++) { count += i; } } return count; }\n" +
                "protected void reset() { count = 0; }\n" +
                "private void localOnly() { collaborator.work(); }\n" +
                "}\n");
        write("src/test/java/fixture/ChildTest.java",
                "package fixture;\n" +
                "public class ChildTest {\n" +
                "public void exercisesTestSource() { new Child().reset(); }\n" +
                "}\n");
        write("src/main/java/fixture/Outer.java",
                "package fixture;\n" +
                "public class Outer {\n" +
                "public static class Nested {\n" +
                "public void ping() { }\n" +
                "}\n" +
                "}\n");
        write("src/main/java/fixture/Marker.java",
                "package fixture;\n" +
                "public @interface Marker { }\n");
        write("src/main/java/fixture/Action.java",
                "package fixture;\n" +
                "public interface Action { void execute(); }\n");
        write("src/main/java/fixture/StaticOnly.java",
                "package fixture;\n" +
                "public class StaticOnly {\n" +
                "private static int shared;\n" +
                "public StaticOnly() { }\n" +
                "public void first() { shared++; }\n" +
                "public void second() { shared++; }\n" +
                "}\n");

        List<PromiseMetricResult> results = PromiseProjectAnalyzer.analyzeDirectories(
                java.util.Collections.singletonList(project));
        Map<String, PromiseMetricResult> byName = results.stream()
                .collect(Collectors.toMap(PromiseMetricResult::getFullyQualifiedName, Function.identity()));

        assertFalse(byName.containsKey("fixture.Outer.Nested"));
        assertTrue(byName.containsKey("fixture.Marker"));
        assertNull(byName.get("fixture.ChildTest"));

        PromiseMetricResult child = byName.get("fixture.Child");
        assertEquals(5, child.getWmc());
        assertEquals(2, child.getDit());
        assertEquals(0, child.getNoc());
        assertEquals(3, child.getCbo());
        assertEquals(9, child.getRfc());
        assertEquals(6, child.getLcom());
        assertEquals(0, child.getCa());
        assertEquals(3, child.getCe());
        assertEquals(3, child.getNpm());
        assertEquals(0.94, child.getLcom3(), 0.01);
        assertEquals(11, child.getLoc());
        assertEquals(0.50, child.getDam(), 0.001);
        assertEquals(3, child.getMoa());
        assertEquals(0.33, child.getMfa(), 0.01);
        assertEquals(0.40, child.getCam(), 0.001);
        assertEquals(1, child.getIc());
        assertEquals(1, child.getCbm());
        assertEquals(1.00, child.getAmc(), 0.001);
        assertEquals(4, child.getMaxCc());
        assertEquals(1.40, child.getAvgCc(), 0.001);

        PromiseMetricResult base = byName.get("fixture.Base");
        assertEquals(1, base.getNoc());
        assertEquals(1, base.getDit());
        assertEquals(1, base.getCe());
        assertEquals(1, base.getCa());
        assertEquals(2, base.getCbo());

        PromiseMetricResult action = byName.get("fixture.Action");
        assertEquals(1, action.getWmc());
        assertEquals(1, action.getMaxCc());
        assertEquals(1.0, action.getAvgCc(), 0.001);
        assertEquals(1.0, action.getCam(), 0.001);

        PromiseMetricResult staticOnly = byName.get("fixture.StaticOnly");
        assertEquals(3, staticOnly.getWmc());
        assertEquals(1, staticOnly.getLcom());
        assertEquals(0.5, staticOnly.getLcom3(), 0.001);
        assertEquals(1.0, staticOnly.getCam(), 0.001);
        assertEquals(1, staticOnly.getMaxCc());
        assertEquals(2.0 / 3.0, staticOnly.getAvgCc(), 0.001);

        assertFalse(results.isEmpty());
    }

    @Test
    void selectsOnlyPromiseReleaseProductSources() throws Exception {
        write("jakarta-ant-1.3/src/main/org/example/Core.java",
                "package org.example; public class Core { }");
        write("jakarta-ant-1.3/src/main/org/example/Secondary.java",
                "package org.example; class Secondary { } class Additional { }");
        write("jakarta-ant-1.3/src/main/org/example/test/Support.java",
                "package org.example.test; public class Support { }");
        write("jakarta-ant-1.3/src/main/org/apache/tools/ant/taskdefs/SendEmail.java",
                "package org.apache.tools.ant.taskdefs; public class SendEmail { }");
        write("jakarta-ant-1.3/src/main/org/apache/tools/ant/taskdefs/optional/OptionalTask.java",
                "package org.apache.tools.ant.taskdefs.optional; public class OptionalTask { }");
        write("jakarta-ant-1.3/src/main/org/apache/tools/ant/launch/Launcher.java",
                "package org.apache.tools.ant.launch; public class Launcher { }");
        write("jakarta-ant-1.3/src/main/org/apache/tools/ant/util/regexp/Regexp.java",
                "package org.apache.tools.ant.util.regexp; public interface Regexp { }");
        write("jakarta-ant-1.3/src/main/org/apache/tools/ant/util/regexp/JakartaRegexpMatcher.java",
                "package org.apache.tools.ant.util.regexp; public class JakartaRegexpMatcher { }");
        write("jakarta-ant-1.3/src/antidote/org/example/Gui.java",
                "package org.example; public class Gui { }");

        List<PromiseMetricResult> results = PromiseProjectAnalyzer.analyzeDirectories(
                java.util.Collections.singletonList(project));
        List<String> names = results.stream()
                .map(PromiseMetricResult::getFullyQualifiedName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(
                "org.apache.tools.ant.util.regexp.Regexp",
                "org.example.Additional",
                "org.example.Core",
                "org.example.Secondary",
                "org.example.test.Support"), names);
    }

    @Test
    void selectsTheMeasuredCoreScopesForMultiModulePromiseReleases()
            throws Exception {
        Path lucene = project.resolve("lucene-solr-releases-lucene-2.4.0");
        write("lucene-solr-releases-lucene-2.4.0/"
                        + "lucene-solr-releases-lucene-2.4.0/"
                        + "src/java/org/apache/lucene/Core.java",
                "package org.apache.lucene; public class Core { }");
        write("lucene-solr-releases-lucene-2.4.0/"
                        + "lucene-solr-releases-lucene-2.4.0/"
                        + "contrib/demo/src/java/org/apache/lucene/Extra.java",
                "package org.apache.lucene; public class Extra { }");

        List<PromiseMetricResult> luceneResults =
                PromiseProjectAnalyzer.analyzeDirectories(
                        java.util.Collections.singletonList(lucene));
        assertEquals(List.of("org.apache.lucene.Core"), luceneResults.stream()
                .map(PromiseMetricResult::getFullyQualifiedName)
                .collect(Collectors.toList()));

        Path synapse = project.resolve("synapse-1.2");
        write("synapse-1.2/synapse-1.2/"
                        + "modules/core/src/main/java/demo/SynapseCore.java",
                "package demo; public class SynapseCore { }");
        write("synapse-1.2/synapse-1.2/"
                        + "modules/transports/src/main/java/demo/Transport.java",
                "package demo; public class Transport { }");

        List<PromiseMetricResult> synapseResults =
                PromiseProjectAnalyzer.analyzeDirectories(
                        java.util.Collections.singletonList(synapse));
        assertEquals(List.of("demo.SynapseCore"), synapseResults.stream()
                .map(PromiseMetricResult::getFullyQualifiedName)
                .collect(Collectors.toList()));
    }

    @Test
    void rejectsACollectionOfNestedPromiseReleaseArchives() throws Exception {
        Files.write(project.resolve("ant-1.3.zip"), new byte[] {1});
        Files.write(project.resolve("camel-1.0.tar.gz"), new byte[] {2});

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> PromiseInputValidator.requireSingleRelease(
                        java.util.Collections.singletonList(project)));
    }

    @Test
    void acceptsAReleaseThatBundlesTestFixtureArchives() throws Exception {
        write("apache-ant-1.7.0/src/main/org/apache/tools/ant/Main.java",
                "package org.apache.tools.ant; public class Main { }");
        Files.createDirectories(project.resolve(
                "apache-ant-1.7.0/src/etc/testcases/taskdefs/zip"));
        Files.write(project.resolve(
                "apache-ant-1.7.0/src/etc/testcases/taskdefs/zip/zipgroupfileset1.zip"),
                new byte[] {1});
        Files.write(project.resolve(
                "apache-ant-1.7.0/src/etc/testcases/taskdefs/zip/zipgroupfileset2.zip"),
                new byte[] {2});
        Files.createDirectories(project.resolve("apache-ant-1.7.0/docs/manual"));
        Files.write(project.resolve(
                "apache-ant-1.7.0/docs/manual/tutorial-writing-tasks-src.zip"),
                new byte[] {3});

        PromiseInputValidator.requireSingleRelease(
                java.util.Collections.singletonList(project));
    }

    private void write(String relativePath, String source) throws Exception {
        Path file = project.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
    }
}
