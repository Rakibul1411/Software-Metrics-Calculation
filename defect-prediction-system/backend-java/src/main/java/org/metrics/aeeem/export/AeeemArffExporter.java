package org.metrics.aeeem.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.common.export.ArffDatasetExporter;

public final class AeeemArffExporter {

    private AeeemArffExporter() {
    }

    public static void exportAeeemToArff(List<AeeemMetricResult> metrics, Path outputPath) throws IOException {
        metrics.sort((left, right) -> left.getFullyQualifiedName().compareTo(right.getFullyQualifiedName()));
        List<List<Object>> rows = new ArrayList<>();
        for (AeeemMetricResult metric : metrics) {
            rows.add(AeeemFeatureSchema.row(metric));
        }
        ArffDatasetExporter.export("aeeem_extracted_metrics", AeeemFeatureSchema.columnsWithIdentifier(), rows,
                outputPath);
    }
}
