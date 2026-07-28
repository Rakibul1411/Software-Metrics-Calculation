package org.metrics.aeeem.history;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.metrics.aeeem.model.AeeemMetricResult;

/**
 * Linearly decayed history of adaptive entropy (LDHH) for the 17 source metrics.
 */
public final class LdhhCalculator {

    private LdhhCalculator() {
    }

    public static void apply(List<Map<String, AeeemMetricResult>> snapshots,
                             Map<String, AeeemMetricResult> finalSnapshot) {
        apply(MetricDeltaHistory.from(snapshots, finalSnapshot.keySet()), finalSnapshot,
                AeeemHistoryConfiguration.fromEnvironment());
    }

    static void apply(MetricDeltaHistory history,
                      Map<String, AeeemMetricResult> finalSnapshot,
                      AeeemHistoryConfiguration configuration) {
        Map<String, double[]> accumulated = emptyValues(finalSnapshot);
        int intervalCount = history.getIntervals().size();

        for (MetricDeltaHistory.Interval interval : history.getIntervals()) {
            double[] totals = new double[AeeemMetricAccess.FEATURE_COUNT];
            int[] changedClassCounts = new int[AeeemMetricAccess.FEATURE_COUNT];
            for (double[] deltas : interval.getDeltasByClass().values()) {
                for (int metric = 0; metric < deltas.length; metric++) {
                    if (deltas[metric] > 0d) {
                        totals[metric] += deltas[metric];
                        changedClassCounts[metric]++;
                    }
                }
            }

            int age = intervalCount - 1 - interval.getIndex();
            double denominator = configuration.getLinearDecayFactor() * (age + 1d);
            for (int metric = 0; metric < AeeemMetricAccess.FEATURE_COUNT; metric++) {
                double entropy = adaptiveEntropy(
                        interval.getDeltasByClass(), metric,
                        totals[metric], changedClassCounts[metric]);
                if (entropy == 0d) {
                    continue;
                }
                double contribution = entropy / denominator;
                for (Map.Entry<String, double[]> entry
                        : interval.getDeltasByClass().entrySet()) {
                    if (entry.getValue()[metric] > 0d) {
                        accumulated.get(entry.getKey())[metric] += contribution;
                    }
                }
            }
        }

        for (Map.Entry<String, AeeemMetricResult> entry : finalSnapshot.entrySet()) {
            AeeemMetricAccess.setLdhhValues(entry.getValue(), accumulated.get(entry.getKey()));
        }
    }

    static double adaptiveEntropy(Map<String, double[]> deltasByClass,
                                  int metric,
                                  double total,
                                  int changedClassCount) {
        if (total <= 0d || changedClassCount <= 1) {
            return 0d;
        }
        double logBase = Math.log(changedClassCount);
        double entropy = 0d;
        for (double[] deltas : deltasByClass.values()) {
            double delta = deltas[metric];
            if (delta > 0d) {
                double probability = delta / total;
                entropy -= probability * Math.log(probability) / logBase;
            }
        }
        return entropy;
    }

    private static Map<String, double[]> emptyValues(
            Map<String, AeeemMetricResult> finalSnapshot) {
        Map<String, double[]> values = new LinkedHashMap<>();
        for (String className : finalSnapshot.keySet()) {
            values.put(className, new double[AeeemMetricAccess.FEATURE_COUNT]);
        }
        return values;
    }
}
