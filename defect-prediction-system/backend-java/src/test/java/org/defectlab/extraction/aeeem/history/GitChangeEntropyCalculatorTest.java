package org.metrics.aeeem.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.metrics.aeeem.model.AeeemMetricResult;

class GitChangeEntropyCalculatorTest {

    @Test
    void calculatesFiveHistoryOfComplexityVariantsFromChangedLines() {
        Map<String, Double> changedLines = new LinkedHashMap<>();
        changedLines.put("src/main/java/demo/A.java", 60d);
        changedLines.put("src/main/java/demo/B.java", 30d);
        changedLines.put("src/main/java/demo/C.java", 10d);

        Map<String, AeeemMetricResult> classes = new LinkedHashMap<>();
        classes.put("demo.A", metric("demo.A", "src/main/java/demo/A.java"));
        classes.put("demo.B", metric("demo.B", "src/main/java/demo/B.java"));
        classes.put("demo.C", metric("demo.C", "src/main/java/demo/C.java"));

        new GitChangeEntropyCalculator().apply(
                Arrays.asList(new GitChangePeriod(0, changedLines)),
                classes,
                AeeemHistoryConfiguration.defaults());

        double entropy = -(0.6d * Math.log(0.6d)
                + 0.3d * Math.log(0.3d)
                + 0.1d * Math.log(0.1d)) / Math.log(3d);
        AeeemMetricResult a = classes.get("demo.A");
        assertEquals(entropy, a.getCvsEntropy(), 1.0e-12);
        assertEquals(0.6d * entropy, a.getCvsWEntropy(), 1.0e-12);
        assertEquals(entropy, a.getCvsExpEntropy(), 1.0e-12);
        assertEquals(entropy, a.getCvsLinEntropy(), 1.0e-12);
        assertEquals(entropy / Math.log(1.01d), a.getCvsLogEntropy(), 1.0e-12);
    }

    @Test
    void assignsFileEntropyToEveryClassDeclaredInThatFile() {
        Map<String, Double> changedLines = new LinkedHashMap<>();
        changedLines.put("src/A.java", 2d);
        changedLines.put("src/B.java", 2d);
        Map<String, AeeemMetricResult> classes = new LinkedHashMap<>();
        classes.put("A", metric("A", "src/A.java"));
        classes.put("A.Inner", metric("A.Inner", "src/A.java"));
        classes.put("B", metric("B", "src/B.java"));

        new GitChangeEntropyCalculator().apply(
                Arrays.asList(new GitChangePeriod(0, changedLines)),
                classes,
                AeeemHistoryConfiguration.defaults());

        assertEquals(1d, classes.get("A").getCvsEntropy(), 1.0e-12);
        assertEquals(1d, classes.get("A.Inner").getCvsEntropy(), 1.0e-12);
        assertEquals(0.5d, classes.get("A").getCvsWEntropy(), 1.0e-12);
    }

    private static AeeemMetricResult metric(String name, String sourcePath) {
        AeeemMetricResult result = new AeeemMetricResult(name);
        result.setSourcePath(sourcePath);
        return result;
    }
}
