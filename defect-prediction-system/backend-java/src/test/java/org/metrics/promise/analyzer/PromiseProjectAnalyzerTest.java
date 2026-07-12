package org.metrics.promise.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.metrics.promise.model.PromiseMetricResult;

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

        List<PromiseMetricResult> results = PromiseProjectAnalyzer.analyzeDirectories(
                java.util.Collections.singletonList(project));
        Map<String, PromiseMetricResult> byName = results.stream()
                .collect(Collectors.toMap(PromiseMetricResult::getFullyQualifiedName, Function.identity()));

        assertTrue(byName.containsKey("fixture.Outer.Nested"));
        assertTrue(byName.containsKey("fixture.ChildTest"));

        PromiseMetricResult child = byName.get("fixture.Child");
        assertEquals(5, child.getWmc());
        assertEquals(2, child.getDit());
        assertEquals(0, child.getNoc());
        assertEquals(3, child.getCbo());
        assertEquals(9, child.getRfc());
        assertEquals(6, child.getLcom());
        assertEquals(1, child.getCa());
        assertEquals(3, child.getCe());
        assertEquals(3, child.getNpm());
        assertEquals(0.94, child.getLcom3(), 0.01);
        assertEquals(11, child.getLoc());
        assertEquals(0.50, child.getDam(), 0.001);
        assertEquals(3, child.getMoa());
        assertEquals(0.33, child.getMfa(), 0.01);
        assertEquals(0.25, child.getCam(), 0.001);
        assertEquals(1, child.getIc());
        assertEquals(1, child.getCbm());
        assertEquals(1.00, child.getAmc(), 0.001);
        assertEquals(3, child.getMaxCc());
        assertEquals(0.60, child.getAvgCc(), 0.001);

        PromiseMetricResult base = byName.get("fixture.Base");
        assertEquals(1, base.getNoc());
        assertEquals(1, base.getDit());
        assertEquals(1, base.getCe());
        assertEquals(1, base.getCa());
        assertEquals(1, base.getCbo());

        assertFalse(results.isEmpty());
    }

    private void write(String relativePath, String source) throws Exception {
        Path file = project.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
    }
}
