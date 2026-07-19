package org.metrics.aeeem.history;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.metrics.aeeem.model.AeeemMetricResult;

public final class LdhhCalculator {

    private static final double DECAY = 0.9d;

    private LdhhCalculator() {
    }

    public static void apply(List<Map<String, AeeemMetricResult>> snapshots,
                             Map<String, AeeemMetricResult> finalSnapshot) {
        Map<String, double[]> accumulated = new LinkedHashMap<>();
        int intervalCount = snapshots.size() - 1;
        for (int snapshotIndex = 1; snapshotIndex < snapshots.size(); snapshotIndex++) {
            Map<String, double[]> changes = changes(snapshots.get(snapshotIndex - 1), snapshots.get(snapshotIndex));
            double[] totals = totals(changes);
            double recencyWeight = Math.pow(DECAY, intervalCount - snapshotIndex);
            for (Map.Entry<String, double[]> entry : changes.entrySet()) {
                double[] result = accumulated.computeIfAbsent(entry.getKey(), ignored ->
                        new double[AeeemMetricAccess.FEATURE_COUNT]);
                for (int metricIndex = 0; metricIndex < result.length; metricIndex++) {
                    if (totals[metricIndex] == 0d) {
                        continue;
                    }
                    double probability = entry.getValue()[metricIndex] / totals[metricIndex];
                    if (probability > 0d) {
                        result[metricIndex] += recencyWeight * -probability * Math.log(probability);
                    }
                }
            }
        }
        for (Map.Entry<String, AeeemMetricResult> entry : finalSnapshot.entrySet()) {
            AeeemMetricAccess.setLdhhValues(entry.getValue(), accumulated.getOrDefault(
                    entry.getKey(), new double[AeeemMetricAccess.FEATURE_COUNT]));
        }
    }

    private static Map<String, double[]> changes(Map<String, AeeemMetricResult> previous,
                                                  Map<String, AeeemMetricResult> current) {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, AeeemMetricResult> entry : current.entrySet()) {
            AeeemMetricResult oldValue = previous.get(entry.getKey());
            if (oldValue == null) {
                continue;
            }
            double[] oldMetrics = AeeemMetricAccess.staticValues(oldValue);
            double[] newMetrics = AeeemMetricAccess.staticValues(entry.getValue());
            double[] delta = new double[AeeemMetricAccess.FEATURE_COUNT];
            for (int metricIndex = 0; metricIndex < delta.length; metricIndex++) {
                delta[metricIndex] = Math.abs(newMetrics[metricIndex] - oldMetrics[metricIndex]);
            }
            result.put(entry.getKey(), delta);
        }
        return result;
    }

    private static double[] totals(Map<String, double[]> changes) {
        double[] totals = new double[AeeemMetricAccess.FEATURE_COUNT];
        for (double[] values : changes.values()) {
            for (int metricIndex = 0; metricIndex < totals.length; metricIndex++) {
                totals[metricIndex] += values[metricIndex];
            }
        }
        return totals;
    }
}
