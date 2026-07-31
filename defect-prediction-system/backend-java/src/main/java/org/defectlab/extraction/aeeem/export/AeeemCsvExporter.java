package org.metrics.aeeem.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.aeeem.model.AeeemMetricResult;

public class AeeemCsvExporter {

    public static void exportAeeemToCSV(List<AeeemMetricResult> metricsList, Path outputPath) throws IOException {
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (java.io.BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            csvPrinter.printRecord(AeeemFeatureSchema.columnsWithIdentifier());

            for (AeeemMetricResult metrics : metricsList) {
                csvPrinter.printRecord(AeeemFeatureSchema.row(metrics));
            }
        }

        System.out.println("Exported " + metricsList.size() + " AEEEM class metrics to: " + outputPath);
    }
}
