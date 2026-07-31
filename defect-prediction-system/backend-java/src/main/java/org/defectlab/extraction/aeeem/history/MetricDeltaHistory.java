package org.metrics.aeeem.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.aeeem.model.AeeemMetricResult;

/**
 * Absolute deltas of the 17 static metrics between consecutive snapshots.
 *
 * <p>Only classes present in the prediction release are rows. A row is absent
 * from an interval when the class is missing from either endpoint, which is the
 * programmatic equivalent of the paper's -1 sentinel.</p>
 */
public final class MetricDeltaHistory {

    private final List<Interval> intervals;

    private MetricDeltaHistory(List<Interval> intervals) {
        this.intervals = Collections.unmodifiableList(intervals);
    }

    public static MetricDeltaHistory from(
            List<Map<String, AeeemMetricResult>> snapshots,
            Set<String> releaseClasses) {
        if (snapshots == null || snapshots.size() < 2) {
            throw new IllegalArgumentException("At least two snapshots are required.");
        }
        List<Interval> intervals = new ArrayList<>();
        for (int index = 1; index < snapshots.size(); index++) {
            Map<String, AeeemMetricResult> previous = snapshots.get(index - 1);
            Map<String, AeeemMetricResult> current = snapshots.get(index);
            Map<String, double[]> deltas = new LinkedHashMap<>();
            for (String className : releaseClasses) {
                AeeemMetricResult oldValue = previous.get(className);
                AeeemMetricResult newValue = current.get(className);
                if (oldValue == null || newValue == null) {
                    continue;
                }
                double[] oldMetrics = AeeemMetricAccess.staticValues(oldValue);
                double[] newMetrics = AeeemMetricAccess.staticValues(newValue);
                double[] classDeltas = new double[AeeemMetricAccess.FEATURE_COUNT];
                for (int metric = 0; metric < classDeltas.length; metric++) {
                    classDeltas[metric] = Math.abs(newMetrics[metric] - oldMetrics[metric]);
                }
                deltas.put(className, classDeltas);
            }
            intervals.add(new Interval(index - 1, deltas));
        }
        return new MetricDeltaHistory(intervals);
    }

    public List<Interval> getIntervals() {
        return intervals;
    }

    public static final class Interval {
        private final int index;
        private final Map<String, double[]> deltasByClass;

        private Interval(int index, Map<String, double[]> deltasByClass) {
            this.index = index;
            this.deltasByClass = Collections.unmodifiableMap(deltasByClass);
        }

        public int getIndex() {
            return index;
        }

        public Map<String, double[]> getDeltasByClass() {
            return deltasByClass;
        }
    }
}
