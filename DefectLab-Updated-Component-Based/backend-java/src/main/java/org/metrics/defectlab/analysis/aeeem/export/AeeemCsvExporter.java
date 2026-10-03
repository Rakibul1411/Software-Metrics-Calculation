package org.metrics.defectlab.analysis.aeeem.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.defectlab.analysis.aeeem.model.AeeemMetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AeeemCsvExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeeemCsvExporter.class);

    public static void exportAeeemToCSV(List<AeeemMetricResult> metricsList, Path outputPath) throws IOException {
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (java.io.BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            csvPrinter.printRecord(AeeemFeatureSchema.columnsWithIdentifier());

            for (AeeemMetricResult metrics : metricsList) {
                csvPrinter.printRecord(AeeemFeatureSchema.row(metrics));
            }
        }

        LOGGER.info("Exported {} AEEEM class metrics to: {}", metricsList.size(), outputPath);
    }
}
