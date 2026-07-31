package org.metrics.aeeem.history;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.metrics.aeeem.model.AeeemMetricResult;

/** Paper-defined weighted churn (WCHU) for all 17 source metrics. */
public final class WchuCalculator {

    private WchuCalculator() {
    }

    public static void apply(List<Map<String, AeeemMetricResult>> snapshots,
                             Map<String, AeeemMetricResult> finalSnapshot) {
        apply(MetricDeltaHistory.from(snapshots, finalSnapshot.keySet()), finalSnapshot);
    }

    static void apply(MetricDeltaHistory history,
                      Map<String, AeeemMetricResult> finalSnapshot) {
        Map<String, double[]> accumulated = emptyValues(finalSnapshot);
        for (MetricDeltaHistory.Interval interval : history.getIntervals()) {
            for (Map.Entry<String, double[]> entry : interval.getDeltasByClass().entrySet()) {
                double[] result = accumulated.get(entry.getKey());
                double[] deltas = entry.getValue();
                for (int metric = 0; metric < result.length; metric++) {
                    if (deltas[metric] > 0d) {
                        result[metric] += 1d + AeeemHistoryConfiguration.WCHU_ALPHA * deltas[metric];
                    }
                }
            }
        }
        for (Map.Entry<String, AeeemMetricResult> entry : finalSnapshot.entrySet()) {
            AeeemMetricAccess.setWchuValues(entry.getValue(), accumulated.get(entry.getKey()));
        }
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
