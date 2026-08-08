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

    // The former calculatesPromiseMetricsWithJdtBindings test asserted the
    // AST-derived semantics that the bytecode pipeline replaced (source-line LOC,
    // source-average AMC, AST-estimated complexity). The replacement expectations
    // live in PromiseMetricSpecificationTest and PromiseInvariantTest.

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
