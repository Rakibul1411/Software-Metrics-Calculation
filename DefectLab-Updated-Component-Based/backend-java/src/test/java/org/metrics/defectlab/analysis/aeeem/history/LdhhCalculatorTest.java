package org.metrics.defectlab.analysis.aeeem.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.analysis.aeeem.model.AeeemMetricResult;

/**
 * Verifies LDHH against the paper's Eq. 14-20: the interval's column entropy
 * (PHH) is broadcast to every class present at both endpoints of that
 * interval (D(i,j) != -1), not only classes whose own value for that metric
 * happened to change -- LDHH is the base HH variant, not the delta-weighted
 * HWH variant.
 */
class LdhhCalculatorTest {

    @Test
    void entropyIsAssignedOnlyToClassesThatChanged() {
        // One interval, three classes all present at both endpoints:
        // A changes wmc by 4, B changes wmc by 6, C's wmc does not move at all.
        Map<String, AeeemMetricResult> s0 = snapshot(
                metric("A", 0d), metric("B", 0d), metric("C", 5d));
        Map<String, AeeemMetricResult> s1 = snapshot(
                metric("A", 4d), metric("B", 6d), metric("C", 5d));

        Map<String, double[]> deltasByClass = Map.of(
                "A", new double[] {4d}, "B", new double[] {6d}, "C", new double[] {0d});
        double expectedEntropy = LdhhCalculator.adaptiveEntropy(deltasByClass, 0, 10d, 2);
        assertTrue(expectedEntropy > 0d, "entropy should be positive when classes changed");

        LdhhCalculator.apply(List.of(s0, s1), s1);

        // Single interval, default decay factor of 1.0 -> denominator is 1.
        // A and B changed (delta > 0), so each receives the interval's entropy.
        assertEquals(expectedEntropy, s1.get("A").getLdhhWmc(), 1e-9);
        assertEquals(expectedEntropy, s1.get("B").getLdhhWmc(), 1e-9);
        // Per Eq. 17 of D'Ambros et al. 2012, only classes that change (delta > 0)
        // receive system entropy. C never changed its own wmc, so its LDHH is 0.
        assertEquals(0.0d, s1.get("C").getLdhhWmc(), 1e-9);
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
