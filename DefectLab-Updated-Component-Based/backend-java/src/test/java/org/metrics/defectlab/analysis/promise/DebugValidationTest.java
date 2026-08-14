package org.metrics.defectlab.analysis.promise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

class DebugValidationTest {

    private static final String SCRATCH =
        "/private/tmp/claude-501/-Users-md-rakibulislam-IIT-SPL-3-promise-dataset-source-code-DefectLab-Updated-Component-Based/45bb8613-07c9-4e72-9d43-89a5c53f96fb/scratchpad/val/";
    private static final String GT =
        "/Users/md.rakibulislam/IIT/SPL-3/promise-dataset-source-code/DefectLab-Updated-Component-Based/PROMISE-backup-copy/bug-data/";

    private static final String[] METRICS = {
        "wmc","dit","noc","cbo","rfc","lcom","ca","ce","npm","lcom3",
        "loc","dam","moa","mfa","cam","ic","cbm","amc","max_cc","avg_cc" };

    @Test
    void validate() throws Exception {
        String project = System.getProperty("valProject", "ant15");
        String gtFile = System.getProperty("valGt", "ant/ant-1.5.csv");

        Path root = Path.of(SCRATCH + project);
        List<PromiseMetricResult> results = new PromiseProjectAnalyzer().analyze(List.of(root));
        Map<String, PromiseMetricResult> mine = results.stream().collect(Collectors.toMap(
                PromiseMetricResult::getFullyQualifiedName, Function.identity(), (a, b) -> a));

        Map<String, double[]> truth = readGroundTruth(Path.of(GT + gtFile));

        System.out.println("=====PROJECT===== " + project + "  mine=" + mine.size()
                + " groundTruth=" + truth.size());

        List<String> matched = new ArrayList<>();
        for (String name : truth.keySet()) {
            if (mine.containsKey(name)) {
                matched.add(name);
            }
        }
        System.out.println("=====MATCHED===== " + matched.size());
        if (matched.isEmpty()) {
            return;
        }

        System.out.printf("%-8s %8s %10s %10s %10s%n", "METRIC", "EXACT%", "MeanAbsDiff", "MeanMine", "MeanGT");
        for (int m = 0; m < METRICS.length; m++) {
            int exact = 0;
            double sumAbs = 0, sumMine = 0, sumGt = 0;
            for (String name : matched) {
                double a = metricOf(mine.get(name), m);
                double b = truth.get(name)[m];
                if (Math.abs(a - b) < 0.005) exact++;
                sumAbs += Math.abs(a - b);
                sumMine += a;
                sumGt += b;
            }
            int n = matched.size();
            System.out.printf("=====M===== %-8s %7.1f%% %10.3f %10.3f %10.3f%n",
                    METRICS[m], 100.0 * exact / n, sumAbs / n, sumMine / n, sumGt / n);
        }

        // Worst offenders for a chosen metric, smallest classes first so they
        // are easy to inspect by hand with javap.
        int focus = Integer.parseInt(System.getProperty("valFocus", "18"));
        List<String> sorted = new ArrayList<>(matched);
        sorted.sort((a, b) -> Double.compare(
                Math.abs(metricOf(mine.get(b), focus) - truth.get(b)[focus]),
                Math.abs(metricOf(mine.get(a), focus) - truth.get(a)[focus])));
        System.out.println("=====WORST " + METRICS[focus] + " (mine vs gt, wmc mine/gt) =====");
        for (int i = 0; i < Math.min(15, sorted.size()); i++) {
            String name = sorted.get(i);
            System.out.printf("=====W===== %-60s mine=%.2f gt=%.2f  wmc=%d/%.0f%n",
                    name, metricOf(mine.get(name), focus), truth.get(name)[focus],
                    mine.get(name).getWmc(), truth.get(name)[0]);
        }
    }

    private static double metricOf(PromiseMetricResult r, int i) {
        switch (i) {
            case 0: return r.getWmc();
            case 1: return r.getDit();
            case 2: return r.getNoc();
            case 3: return r.getCbo();
            case 4: return r.getRfc();
            case 5: return r.getLcom();
            case 6: return r.getCa();
            case 7: return r.getCe();
            case 8: return r.getNpm();
            case 9: return r.getLcom3();
            case 10: return r.getLoc();
            case 11: return r.getDam();
            case 12: return r.getMoa();
            case 13: return r.getMfa();
            case 14: return r.getCam();
            case 15: return r.getIc();
            case 16: return r.getCbm();
            case 17: return r.getAmc();
            case 18: return r.getMaxCc();
            default: return r.getAvgCc();
        }
    }

    private static Map<String, double[]> readGroundTruth(Path csv) throws Exception {
        Map<String, double[]> out = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(csv);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (parts.length < 21) continue;
            double[] vals = new double[20];
            for (int m = 0; m < 20; m++) {
                try { vals[m] = Double.parseDouble(parts[m + 1].trim()); }
                catch (NumberFormatException e) { vals[m] = Double.NaN; }
            }
            out.put(parts[0].trim(), vals);
        }
        return out;
    }
}
