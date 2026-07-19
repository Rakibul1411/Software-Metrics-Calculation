package org.metrics.aeeem.history;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.metrics.aeeem.model.AeeemMetricResult;

public final class WchuCalculator {

    private WchuCalculator() {
    }

    public static void apply(List<Map<String, AeeemMetricResult>> snapshots,
                             Map<String, AeeemMetricResult> finalSnapshot) {
        Map<String, double[]> accumulated = new LinkedHashMap<>();
        for (int snapshotIndex = 1; snapshotIndex < snapshots.size(); snapshotIndex++) {
            Map<String, AeeemMetricResult> previous = snapshots.get(snapshotIndex - 1);
            Map<String, AeeemMetricResult> current = snapshots.get(snapshotIndex);
            for (Map.Entry<String, AeeemMetricResult> entry : current.entrySet()) {
                AeeemMetricResult oldValue = previous.get(entry.getKey());
                if (oldValue == null) {
                    continue;
                }
                double[] oldMetrics = AeeemMetricAccess.staticValues(oldValue);
                double[] newMetrics = AeeemMetricAccess.staticValues(entry.getValue());
                double[] totals = accumulated.computeIfAbsent(entry.getKey(), ignored ->
                        new double[AeeemMetricAccess.FEATURE_COUNT]);
                for (int metricIndex = 0; metricIndex < totals.length; metricIndex++) {
                    totals[metricIndex] += Math.abs(newMetrics[metricIndex] - oldMetrics[metricIndex]);
                }
            }
        }
        for (Map.Entry<String, AeeemMetricResult> entry : finalSnapshot.entrySet()) {
            AeeemMetricAccess.setWchuValues(entry.getValue(), accumulated.getOrDefault(
                    entry.getKey(), new double[AeeemMetricAccess.FEATURE_COUNT]));
        }
    }
}
