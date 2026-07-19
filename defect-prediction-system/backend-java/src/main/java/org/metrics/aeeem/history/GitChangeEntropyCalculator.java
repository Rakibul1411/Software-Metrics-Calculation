package org.metrics.aeeem.history;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.metrics.aeeem.git.GitHistoryAnalyzer;
import org.metrics.aeeem.model.AeeemMetricResult;

public final class GitChangeEntropyCalculator {

    public void apply(Path repository, String branch, LocalDate historyStart,
                      Map<String, AeeemMetricResult> classes) throws IOException {
        Map<String, List<Double>> changesByPath = parseChanges(
                GitHistoryAnalyzer.gitLogJavaChanges(repository, branch, historyStart));
        for (AeeemMetricResult metrics : classes.values()) {
            List<Double> changes = findChanges(changesByPath, metrics.getSourcePath());
            double total = changes.stream().mapToDouble(Double::doubleValue).sum();
            double entropy = shannon(changes, total);
            double weightedEntropy = weightedShannon(changes);
            metrics.setCvsEntropy(entropy);
            metrics.setCvsWEntropy(weightedEntropy);
            metrics.setCvsLinEntropy(total == 0d ? 0d : entropy / total);
            metrics.setCvsLogEntropy(Math.log1p(entropy));
            metrics.setCvsExpEntropy(1d - Math.exp(-entropy));
        }
    }

    private Map<String, List<Double>> parseChanges(String log) {
        Map<String, List<Double>> result = new HashMap<>();
        for (String line : log.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("commit:") || !value.endsWith(".java")) {
                continue;
            }
            result.computeIfAbsent(normalize(value), ignored -> new ArrayList<>()).add(1d);
        }
        return result;
    }

    private List<Double> findChanges(Map<String, List<Double>> changesByPath, String sourcePath) {
        if (sourcePath == null) {
            return java.util.Collections.emptyList();
        }
        String normalized = normalize(sourcePath);
        List<Double> exact = changesByPath.get(normalized);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, List<Double>> entry : changesByPath.entrySet()) {
            if (entry.getKey().endsWith("/" + normalized) || normalized.endsWith("/" + entry.getKey())) {
                return entry.getValue();
            }
        }
        return java.util.Collections.emptyList();
    }

    private double shannon(List<Double> values, double total) {
        if (total <= 0d) {
            return 0d;
        }
        double entropy = 0d;
        for (double value : values) {
            if (value > 0d) {
                double probability = value / total;
                entropy -= probability * Math.log(probability);
            }
        }
        return entropy;
    }

    private double weightedShannon(List<Double> values) {
        double weightedTotal = 0d;
        for (int index = 0; index < values.size(); index++) {
            weightedTotal += values.get(index) * (index + 1d);
        }
        if (weightedTotal == 0d) {
            return 0d;
        }
        double result = 0d;
        for (int index = 0; index < values.size(); index++) {
            double probability = values.get(index) * (index + 1d) / weightedTotal;
            if (probability > 0d) {
                result -= probability * Math.log(probability);
            }
        }
        return result;
    }

    private String normalize(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
