package org.metrics.aeeem.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.aeeem.git.GitNumstatParser;
import org.metrics.aeeem.model.AeeemMetricResult;

/**
 * Calculates HCM, WHCM, EDHCM, LDHCM and LGDHCM from Git changed lines.
 */
public final class GitChangeEntropyCalculator {

    public void apply(List<GitChangePeriod> periods,
                      Map<String, AeeemMetricResult> classes,
                      AeeemHistoryConfiguration configuration) {
        Map<String, double[]> accumulatedByPath = new LinkedHashMap<>();
        for (AeeemMetricResult metrics : classes.values()) {
            if (metrics.getSourcePath() != null) {
                accumulatedByPath.computeIfAbsent(normalize(metrics.getSourcePath()),
                        ignored -> new double[5]);
            }
        }

        Deque<Set<String>> recentPeriods = new ArrayDeque<>();
        Map<String, Integer> recentPathCounts = new HashMap<>();
        int periodCount = periods.size();
        for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
            GitChangePeriod period = periods.get(periodIndex);
            Map<String, Double> changes = positiveChanges(period.getChangedLinesByPath());
            Set<String> modifiedPaths = new LinkedHashSet<>(changes.keySet());
            recentPeriods.addLast(modifiedPaths);
            for (String path : modifiedPaths) {
                recentPathCounts.put(path, recentPathCounts.getOrDefault(path, 0) + 1);
            }
            while (recentPeriods.size() > configuration.getRecentPeriodCount()) {
                for (String path : recentPeriods.removeFirst()) {
                    int count = recentPathCounts.get(path) - 1;
                    if (count == 0) {
                        recentPathCounts.remove(path);
                    } else {
                        recentPathCounts.put(path, count);
                    }
                }
            }

            double total = changes.values().stream().mapToDouble(Double::doubleValue).sum();
            double entropy = adaptiveEntropy(changes, total, recentPathCounts.size());
            if (entropy == 0d) {
                continue;
            }
            int age = periodCount - 1 - periodIndex;
            double exponentialDenominator = Math.exp(
                    configuration.getExponentialDecayFactor() * age);
            double linearDenominator = configuration.getLinearDecayFactor() * (age + 1d);
            double logarithmicDenominator = configuration.getLogarithmicDecayFactor()
                    * Math.log(age + 1.01d);

            for (Map.Entry<String, Double> change : changes.entrySet()) {
                double[] result = accumulatedByPath.get(change.getKey());
                if (result == null) {
                    continue;
                }
                double probability = change.getValue() / total;
                result[0] += entropy;
                result[1] += probability * entropy;
                result[2] += entropy / exponentialDenominator;
                result[3] += entropy / linearDenominator;
                result[4] += entropy / logarithmicDenominator;
            }
        }

        for (AeeemMetricResult metrics : classes.values()) {
            double[] values = metrics.getSourcePath() == null
                    ? null : accumulatedByPath.get(normalize(metrics.getSourcePath()));
            if (values == null) {
                values = new double[5];
            }
            metrics.setCvsEntropy(values[0]);
            metrics.setCvsWEntropy(values[1]);
            metrics.setCvsExpEntropy(values[2]);
            metrics.setCvsLinEntropy(values[3]);
            metrics.setCvsLogEntropy(values[4]);
        }
    }

    static double adaptiveEntropy(Map<String, Double> changedLines,
                                  double total,
                                  int recentlyModifiedFileCount) {
        if (total <= 0d || recentlyModifiedFileCount <= 1) {
            return 0d;
        }
        double logBase = Math.log(recentlyModifiedFileCount);
        double entropy = 0d;
        for (double value : changedLines.values()) {
            if (value > 0d) {
                double probability = value / total;
                entropy -= probability * Math.log(probability) / logBase;
            }
        }
        return entropy;
    }

    private Map<String, Double> positiveChanges(Map<String, Double> input) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : input.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0d) {
                result.put(normalize(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String normalize(String path) {
        return GitNumstatParser.normalize(path);
    }
}
