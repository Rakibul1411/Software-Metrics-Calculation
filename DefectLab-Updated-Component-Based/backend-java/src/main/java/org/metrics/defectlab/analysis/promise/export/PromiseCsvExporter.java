package org.metrics.defectlab.analysis.promise.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromiseCsvExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromiseCsvExporter.class);

    /**
     * Exports PROMISE metric results to a CSV file sorted by class name.
     */
    public static void exportPromiseToCSV(List<PromiseMetricResult> metricsList, Path outputPath) throws IOException {
        List<PromiseMetricResult> sorted = new ArrayList<>(metricsList);
        sorted.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (java.io.BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            csvPrinter.printRecord(PromiseFeatureSchema.columns());

            for (PromiseMetricResult metrics : sorted) {
                csvPrinter.printRecord(PromiseFeatureSchema.row(metrics));
            }
        }
        LOGGER.info("Exported {} PROMISE class metrics to: {}", sorted.size(), outputPath);
    }
}
