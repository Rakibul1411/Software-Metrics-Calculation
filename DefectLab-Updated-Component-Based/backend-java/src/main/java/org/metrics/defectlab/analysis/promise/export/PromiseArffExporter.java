package org.metrics.defectlab.analysis.promise.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.metrics.defectlab.shared.export.ArffDatasetExporter;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

public final class PromiseArffExporter {

    private PromiseArffExporter() {
    }

    public static void exportPromiseToArff(List<PromiseMetricResult> metrics, Path outputPath) throws IOException {
        metrics.sort((left, right) -> left.getFullyQualifiedName().compareTo(right.getFullyQualifiedName()));
        List<List<Object>> rows = new ArrayList<>();
        for (PromiseMetricResult metric : metrics) {
            rows.add(PromiseFeatureSchema.row(metric));
        }
        ArffDatasetExporter.export("promise_extracted_metrics", PromiseFeatureSchema.columns(), rows, outputPath);
    }
}
