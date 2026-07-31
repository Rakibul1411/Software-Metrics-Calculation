package org.metrics.aeeem.history;

/**
 * Parameters used by the history formulas.
 *
 * <p>The papers fix WCHU alpha at 0.01 but do not publish numeric values for
 * every decay factor. The decay factors therefore default to 1.0 and can be
 * supplied explicitly through environment variables.</p>
 */
public final class AeeemHistoryConfiguration {

    public static final String EXP_DECAY_VARIABLE = "AEEEM_EXP_DECAY_FACTOR";
    public static final String LINEAR_DECAY_VARIABLE = "AEEEM_LINEAR_DECAY_FACTOR";
    public static final String LOG_DECAY_VARIABLE = "AEEEM_LOG_DECAY_FACTOR";
    public static final String RECENT_PERIODS_VARIABLE = "AEEEM_RECENT_PERIODS";
    public static final double WCHU_ALPHA = 0.01d;

    private final double exponentialDecayFactor;
    private final double linearDecayFactor;
    private final double logarithmicDecayFactor;
    private final int recentPeriodCount;

    public AeeemHistoryConfiguration(double exponentialDecayFactor,
                                     double linearDecayFactor,
                                     double logarithmicDecayFactor,
                                     int recentPeriodCount) {
        this.exponentialDecayFactor = positive(exponentialDecayFactor, "exponential decay factor");
        this.linearDecayFactor = positive(linearDecayFactor, "linear decay factor");
        this.logarithmicDecayFactor = positive(logarithmicDecayFactor, "logarithmic decay factor");
        if (recentPeriodCount < 1) {
            throw new IllegalArgumentException("The recent-period window must be at least 1.");
        }
        this.recentPeriodCount = recentPeriodCount;
    }

    public static AeeemHistoryConfiguration fromEnvironment() {
        return new AeeemHistoryConfiguration(
                environmentDouble(EXP_DECAY_VARIABLE, 1d),
                environmentDouble(LINEAR_DECAY_VARIABLE, 1d),
                environmentDouble(LOG_DECAY_VARIABLE, 1d),
                environmentInteger(RECENT_PERIODS_VARIABLE, 6));
    }

    public static AeeemHistoryConfiguration defaults() {
        return new AeeemHistoryConfiguration(1d, 1d, 1d, 6);
    }

    public double getExponentialDecayFactor() {
        return exponentialDecayFactor;
    }

    public double getLinearDecayFactor() {
        return linearDecayFactor;
    }

    public double getLogarithmicDecayFactor() {
        return logarithmicDecayFactor;
    }

    public int getRecentPeriodCount() {
        return recentPeriodCount;
    }

    private static double environmentDouble(String variable, double defaultValue) {
        String value = System.getenv(variable);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return positive(Double.parseDouble(value.trim()), variable);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(variable + " must be a positive number.", exception);
        }
    }

    private static int environmentInteger(String variable, int defaultValue) {
        String value = System.getenv(variable);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int result = Integer.parseInt(value.trim());
            if (result < 1) {
                throw new IllegalArgumentException(variable + " must be at least 1.");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(variable + " must be a positive integer.", exception);
        }
    }

    private static double positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be a finite positive number.");
        }
        return value;
    }
}
