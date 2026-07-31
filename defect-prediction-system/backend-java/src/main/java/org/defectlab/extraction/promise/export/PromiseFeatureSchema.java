package org.metrics.promise.export;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.metrics.promise.model.PromiseMetricResult;

public final class PromiseFeatureSchema {

    private static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList(
            "name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
            "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc"));

    private PromiseFeatureSchema() {
    }

    public static List<String> columns() {
        return COLUMNS;
    }

    public static List<Object> row(PromiseMetricResult metrics) {
        return Arrays.<Object>asList(
                metrics.getFullyQualifiedName(), metrics.getWmc(), metrics.getDit(), metrics.getNoc(),
                metrics.getCbo(), metrics.getRfc(), metrics.getLcom(), metrics.getCa(), metrics.getCe(),
                metrics.getNpm(),
                PromiseNumericFormatter.formatRatio(metrics.getLcom3()),
                metrics.getLoc(),
                PromiseNumericFormatter.formatRatio(metrics.getDam()),
                metrics.getMoa(),
                PromiseNumericFormatter.formatRatio(metrics.getMfa()),
                PromiseNumericFormatter.formatRatio(metrics.getCam()),
                metrics.getIc(), metrics.getCbm(),
                PromiseNumericFormatter.formatRatio(metrics.getAmc()),
                metrics.getMaxCc(),
                PromiseNumericFormatter.formatAverageComplexity(metrics.getAvgCc()));
    }
}
