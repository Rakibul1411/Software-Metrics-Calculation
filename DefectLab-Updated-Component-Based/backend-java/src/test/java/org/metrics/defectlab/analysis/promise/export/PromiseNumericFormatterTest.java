package org.metrics.defectlab.analysis.promise.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PromiseNumericFormatterTest {

    @Test
    void dropsBinaryFloatingPointTailsFromRatios() {
        assertEquals("0.182971014",
                PromiseNumericFormatter.formatRatio(0.18297101449275363));
        assertEquals("10.56521739",
                PromiseNumericFormatter.formatRatio(10.565217391304348));
        assertEquals("0.368421053",
                PromiseNumericFormatter.formatRatio(0.3684210526315789));
    }

    @Test
    void reproducesTheWidthUsedByTheLabelledReleases() {
        // ant-1.7.csv stores these as 0.444444444 and 32.66666667.
        assertEquals("0.444444444", PromiseNumericFormatter.formatRatio(4.0 / 9.0));
        assertEquals("32.66666667", PromiseNumericFormatter.formatRatio(98.0 / 3.0));
        assertTrue(PromiseNumericFormatter.formatRatio(1.0 / 3.0).length() <= 11);
        assertTrue(PromiseNumericFormatter.formatRatio(1234.5678901234).length() <= 11);
    }

    @Test
    void keepsExactValuesShortAndFreeOfExponents() {
        assertEquals("0", PromiseNumericFormatter.formatRatio(0d));
        assertEquals("1.1", PromiseNumericFormatter.formatRatio(1.1));
        assertEquals("0.5", PromiseNumericFormatter.formatRatio(0.5));
        assertEquals("100", PromiseNumericFormatter.formatRatio(100d));
        assertEquals("713", PromiseNumericFormatter.formatRatio(713d));
    }

    @Test
    void roundsAverageComplexityToFourDecimals() {
        // ant-1.7.csv stores 2 / 3 as 0.6667 in avg_cc.
        assertEquals("0.6667", PromiseNumericFormatter.formatAverageComplexity(2.0 / 3.0));
        assertEquals("1.375", PromiseNumericFormatter.formatAverageComplexity(1.375));
        assertEquals("1.4", PromiseNumericFormatter.formatAverageComplexity(1.4));
        assertEquals("0", PromiseNumericFormatter.formatAverageComplexity(0d));
        assertEquals("2.8043", PromiseNumericFormatter.formatAverageComplexity(2.804347826086));
    }

    @Test
    void rejectsNonFiniteMetricOutput() {
        assertThrows(IllegalArgumentException.class,
                () -> PromiseNumericFormatter.formatRatio(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> PromiseNumericFormatter.formatAverageComplexity(
                        Double.POSITIVE_INFINITY));
    }
}
