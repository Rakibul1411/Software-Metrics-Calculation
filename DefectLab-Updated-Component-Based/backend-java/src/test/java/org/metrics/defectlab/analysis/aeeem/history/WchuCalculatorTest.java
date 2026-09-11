package org.metrics.defectlab.analysis.aeeem.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.analysis.aeeem.model.AeeemMetricResult;

/**
 * Verifies WCHU against the paper's Eq. 8-10: every class present at both
 * endpoints of an interval (D(i,j) != -1) earns the +1 survival credit for
 * every metric that interval, even when that specific metric's value did not
 * move -- only the alpha*delta term is metric-specific.
 */
class WchuCalculatorTest {

    @Test
    void positiveChangesEarnWeightedChurnAndUnchangedMetricsAreZero() {
        // A survives both intervals: wmc unchanged in interval 0 (delta 0), +3 in interval 1.
        // B is only present from snapshot 1 onward: +2 in interval 1 only.
        // C survives both intervals with unchanged metric (delta 0 in both).
        Map<String, AeeemMetricResult> s0 = snapshot(metric("A", 5d), metric("C", 10d));
        Map<String, AeeemMetricResult> s1 = snapshot(metric("A", 5d), metric("B", 10d), metric("C", 10d));
        Map<String, AeeemMetricResult> s2 = snapshot(metric("A", 8d), metric("B", 12d), metric("C", 10d));

        WchuCalculator.apply(List.of(s0, s1, s2), s2);

        // A: interval0 (delta 0) -> 0; interval1 (delta 3) -> 1 + 0.01*3 = 1.03; total 1.03
        assertEquals(1.03d, s2.get("A").getWchuWmc(), 1e-9);
        // B: only interval1 (delta 2) -> 1 + 0.01*2 = 1.02
        assertEquals(1.02d, s2.get("B").getWchuWmc(), 1e-9);
        // C: unchanged across all intervals -> 0.0
        assertEquals(0.0d, s2.get("C").getWchuWmc(), 1e-9);
    }

    private static Map<String, AeeemMetricResult> snapshot(AeeemMetricResult... classes) {
        Map<String, AeeemMetricResult> snapshot = new LinkedHashMap<>();
        for (AeeemMetricResult value : classes) {
            snapshot.put(value.getFullyQualifiedName(), value);
        }
        return snapshot;
    }

    private static AeeemMetricResult metric(String name, double wmc) {
        AeeemMetricResult result = new AeeemMetricResult(name);
        result.setCkOoWmc(wmc);
        return result;
    }
}
