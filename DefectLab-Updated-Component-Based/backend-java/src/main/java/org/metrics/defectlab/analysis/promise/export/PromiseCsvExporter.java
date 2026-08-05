package org.metrics.defectlab.analysis.promise.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

public class PromiseCsvExporter {

    public static void exportPromiseToCSV(List<PromiseMetricResult> metricsList, Path outputPath) throws IOException {
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (java.io.BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            csvPrinter.printRecord(PromiseFeatureSchema.columns());

            for (PromiseMetricResult metrics : metricsList) {
                csvPrinter.printRecord(PromiseFeatureSchema.row(metrics));
            }
        }
        System.out.println("Exported " + metricsList.size() + " PROMISE class metrics to: " + outputPath);
    }

}
