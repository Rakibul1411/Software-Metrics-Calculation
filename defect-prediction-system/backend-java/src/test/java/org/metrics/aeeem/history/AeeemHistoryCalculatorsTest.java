package org.metrics.aeeem.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.metrics.aeeem.model.AeeemMetricResult;

class AeeemHistoryCalculatorsTest {

    @Test
    void wchuAggregatesAbsoluteChangesInsteadOfCopyingStaticValues() {
        AeeemMetricResult first = metric("demo.A", 2);
        AeeemMetricResult second = metric("demo.A", 5);
        Map<String, AeeemMetricResult> initial = map(first);
        Map<String, AeeemMetricResult> current = map(second);

        WchuCalculator.apply(Arrays.asList(initial, current), current);

        assertEquals(3d, second.getWchuWmc());
        assertEquals(0d, second.getWchuDit());
    }

    @Test
    void ldhhUsesPerIntervalChangeDistribution() {
        AeeemMetricResult firstA = metric("demo.A", 1);
        AeeemMetricResult firstB = metric("demo.B", 1);
        AeeemMetricResult secondA = metric("demo.A", 2);
        AeeemMetricResult secondB = metric("demo.B", 3);
        Map<String, AeeemMetricResult> initial = map(firstA, firstB);
        Map<String, AeeemMetricResult> current = map(secondA, secondB);

        LdhhCalculator.apply(Arrays.asList(initial, current), current);

        assertTrue(secondA.getLdhhWmc() > 0d);
        assertTrue(secondB.getLdhhWmc() > 0d);
        assertTrue(secondA.getLdhhWmc() > secondB.getLdhhWmc());
    }

    private AeeemMetricResult metric(String name, double wmc) {
        AeeemMetricResult result = new AeeemMetricResult(name);
        result.setCkOoWmc(wmc);
        return result;
    }

    private Map<String, AeeemMetricResult> map(AeeemMetricResult... values) {
        Map<String, AeeemMetricResult> result = new LinkedHashMap<>();
        for (AeeemMetricResult value : values) {
            result.put(value.getFullyQualifiedName(), value);
        }
        return result;
    }
}
