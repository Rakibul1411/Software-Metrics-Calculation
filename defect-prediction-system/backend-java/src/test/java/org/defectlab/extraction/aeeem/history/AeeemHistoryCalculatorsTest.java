package org.metrics.aeeem.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.metrics.aeeem.model.AeeemMetricResult;

class AeeemHistoryCalculatorsTest {

    @Test
    void wchuUsesPaperWeightForEachPositiveMetricChange() {
        Map<String, AeeemMetricResult> first = map(
                metric("demo.A", 10),
                metric("demo.B", 20),
                metric("demo.Missing", 7));
        Map<String, AeeemMetricResult> second = map(
                metric("demo.A", 50),
                metric("demo.B", 30));
        Map<String, AeeemMetricResult> release = map(
                metric("demo.A", 60),
                metric("demo.B", 35),
                metric("demo.Missing", 9),
                metric("demo.New", 1));

        WchuCalculator.apply(Arrays.asList(first, second, release), release);

        assertEquals(2.50d, release.get("demo.A").getWchuWmc(), 1.0e-12);
        assertEquals(2.15d, release.get("demo.B").getWchuWmc(), 1.0e-12);
        assertEquals(0d, release.get("demo.Missing").getWchuWmc(), 1.0e-12);
        assertEquals(0d, release.get("demo.New").getWchuWmc(), 1.0e-12);
        assertEquals(0d, release.get("demo.A").getWchuDit(), 1.0e-12);
    }

    @Test
    void ldhhUsesSystemEntropyAndLinearTimeDecay() {
        Map<String, AeeemMetricResult> first = map(
                metric("demo.A", 10),
                metric("demo.B", 20));
        Map<String, AeeemMetricResult> second = map(
                metric("demo.A", 50),
                metric("demo.B", 30));
        Map<String, AeeemMetricResult> release = map(
                metric("demo.A", 60),
                metric("demo.B", 35));
        MetricDeltaHistory history = MetricDeltaHistory.from(
                Arrays.asList(first, second, release), release.keySet());

        LdhhCalculator.apply(history, release, AeeemHistoryConfiguration.defaults());

        double firstEntropy = binaryEntropy(0.8d, 0.2d);
        double secondEntropy = binaryEntropy(2d / 3d, 1d / 3d);
        double expected = firstEntropy / 2d + secondEntropy;
        assertEquals(expected, release.get("demo.A").getLdhhWmc(), 1.0e-12);
        assertEquals(expected, release.get("demo.B").getLdhhWmc(), 1.0e-12);
        assertEquals(1.2792598814981706d, expected, 1.0e-12);
        assertEquals(0d, release.get("demo.A").getLdhhDit(), 1.0e-12);
    }

    @Test
    void entropyIsZeroWhenOnlyOneClassChanges() {
        Map<String, double[]> deltas = new LinkedHashMap<>();
        deltas.put("demo.A", values(4d));
        deltas.put("demo.B", values(0d));

        assertEquals(0d, LdhhCalculator.adaptiveEntropy(deltas, 0, 4d, 1), 1.0e-12);
    }

    private static double binaryEntropy(double first, double second) {
        return -(first * Math.log(first) + second * Math.log(second)) / Math.log(2d);
    }

    private static double[] values(double first) {
        double[] values = new double[AeeemMetricAccess.FEATURE_COUNT];
        values[0] = first;
        return values;
    }

    private static AeeemMetricResult metric(String name, double wmc) {
        AeeemMetricResult result = new AeeemMetricResult(name);
        result.setCkOoWmc(wmc);
        return result;
    }

    private static Map<String, AeeemMetricResult> map(AeeemMetricResult... values) {
        Map<String, AeeemMetricResult> result = new LinkedHashMap<>();
        for (AeeemMetricResult value : values) {
            result.put(value.getFullyQualifiedName(), value);
        }
        return result;
    }
}
