package org.metrics.aeeem.export;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stable numeric representation for generated AEEEM datasets.
 *
 * <p>The reference ARFF datasets keep at most six fractional digits. Applying
 * the same output precision removes binary floating-point tails without
 * changing the values used by the metric calculations.</p>
 */
final class AeeemNumericFormatter {

    static final int DECIMAL_PLACES = 6;

    private AeeemNumericFormatter() {
    }

    static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "AEEEM metric output contains a non-finite numeric value.");
        }
        if (value == 0d) {
            return "0";
        }
        return BigDecimal.valueOf(value)
                .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
