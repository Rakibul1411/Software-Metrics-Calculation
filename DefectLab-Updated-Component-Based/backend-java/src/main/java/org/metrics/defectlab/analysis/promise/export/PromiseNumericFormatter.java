package org.metrics.defectlab.analysis.promise.export;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stable numeric representation for generated PROMISE datasets.
 *
 * <p>Raw {@code double} values carry binary floating-point tails such as
 * {@code 0.18297101449275363}. The labelled PROMISE releases instead spend
 * eleven characters on each ratio ({@code 0.444444444}, {@code 32.66666667})
 * and round {@code avg_cc} to four decimals. Matching that output removes the
 * tails without changing the values the calculators produce.</p>
 */
final class PromiseNumericFormatter {

    /** Characters the labelled releases spend on one ratio value. */
    private static final int RATIO_WIDTH = 11;
    private static final int MAX_RATIO_DECIMALS = 9;
    private static final int AVERAGE_COMPLEXITY_DECIMALS = 4;

    private PromiseNumericFormatter() {
    }

    /** Formats lcom3, dam, mfa, cam, and amc the way the labelled releases do. */
    static String formatRatio(double value) {
        requireFinite(value);
        // Decimals left once the sign, integer part, and separator are spent.
        int integerWidth = BigDecimal.valueOf(value)
                .setScale(0, RoundingMode.DOWN)
                .toPlainString()
                .length();
        int decimals = Math.min(MAX_RATIO_DECIMALS, RATIO_WIDTH - integerWidth - 1);
        return format(value, Math.max(0, decimals));
    }

    /** Formats avg_cc, which the labelled releases round to four decimals. */
    static String formatAverageComplexity(double value) {
        requireFinite(value);
        return format(value, AVERAGE_COMPLEXITY_DECIMALS);
    }

    private static String format(double value, int decimals) {
        BigDecimal rounded = BigDecimal.valueOf(value)
                .setScale(decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "PROMISE metric output contains a non-finite numeric value.");
        }
    }
}
