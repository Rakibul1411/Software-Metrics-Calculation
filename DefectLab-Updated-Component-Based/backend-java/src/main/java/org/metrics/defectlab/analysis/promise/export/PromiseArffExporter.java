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

    /**
     * Sorts a copy rather than the caller's list in place: an archive with no
     * Java classes hands back an immutable empty list, and sorting it directly
     * throws UnsupportedOperationException before the "no classes found"
     * validation in MetricsExtractionService ever gets a chance to run.
     */
    public static void exportPromiseToArff(List<PromiseMetricResult> metrics, Path outputPath) throws IOException {
        List<PromiseMetricResult> sorted = new ArrayList<>(metrics);
        sorted.sort((left, right) -> left.getFullyQualifiedName().compareTo(right.getFullyQualifiedName()));
        List<List<Object>> rows = new ArrayList<>();
        for (PromiseMetricResult metric : sorted) {
            rows.add(PromiseFeatureSchema.row(metric));
        }
        ArffDatasetExporter.export("promise_extracted_metrics", PromiseFeatureSchema.columns(), rows, outputPath);
    }
}
