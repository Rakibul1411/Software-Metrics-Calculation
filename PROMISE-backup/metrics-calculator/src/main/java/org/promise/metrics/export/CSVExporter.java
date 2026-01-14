package org.promise.metrics.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.promise.metrics.model.ClassMetrics;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Export metrics to CSV format.
 */
public class CSVExporter {

    /**
     * Export metrics to a CSV file.
     *
     * @param metricsList List of class metrics to export
     * @param outputPath  Path to the output CSV file
     * @throws IOException If a file cannot be written
     */
    public static void exportToCSV(List<ClassMetrics> metricsList, Path outputPath) throws IOException {
        // Sort by fully qualified name
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (FileWriter writer = new FileWriter(outputPath.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // Write header
            csvPrinter.printRecord("name", "npm", "loc");

            // Write data rows
            for (ClassMetrics metrics : metricsList) {
                csvPrinter.printRecord(
                        metrics.getFullyQualifiedName(),
                        metrics.getNpm(),
                        metrics.getLoc()
                );
            }
        }

        System.out.println("Exported " + metricsList.size() + " class metrics to: " + outputPath);
    }

    /**
     * Export metrics to CSV with custom column selection.
     *
     * @param metricsList List of class metrics
     * @param outputPath  Output file path
     * @param includeAllColumns Whether to include placeholder columns for the full 22-column format
     * @throws IOException If a file cannot be written
     */
    public static void exportToCSVWithFullFormat(List<ClassMetrics> metricsList, Path outputPath,
                                                  boolean includeAllColumns) throws IOException {
        // Sort by fully qualified name
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (FileWriter writer = new FileWriter(outputPath.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            if (includeAllColumns) {
                // Write full header matching original format (22 columns)
                csvPrinter.printRecord(
                        "name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                        "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc", "bug"
                );

                // Write data with placeholders for unimplemented metrics
                for (ClassMetrics metrics : metricsList) {
                    csvPrinter.printRecord(
                            metrics.getFullyQualifiedName(),
                            0,  // wmc - not implemented
                            0,  // dit - not implemented
                            0,  // noc - not implemented
                            0,  // cbo - not implemented
                            0,  // rfc - not implemented
                            0,  // lcom - not implemented
                            0,  // ca - not implemented
                            0,  // ce - not implemented
                            metrics.getNpm(),
                            0,  // lcom3 - not implemented
                            metrics.getLoc(),
                            0,  // dam - not implemented
                            0,  // moa - not implemented
                            0,  // mfa - not implemented
                            0,  // cam - not implemented
                            0,  // ic - not implemented
                            0,  // cbm - not implemented
                            0,  // amc - not implemented
                            0,  // max_cc - not calculable from source
                            0,  // avg_cc - not calculable from source
                            0   // bug - not calculable from source
                    );
                }
            } else {
                // Write only implemented columns
                csvPrinter.printRecord("name", "npm", "loc");

                for (ClassMetrics metrics : metricsList) {
                    csvPrinter.printRecord(
                            metrics.getFullyQualifiedName(),
                            metrics.getNpm(),
                            metrics.getLoc()
                    );
                }
            }
        }

        System.out.println("Exported " + metricsList.size() + " class metrics to: " + outputPath);
    }

    /**
     * Print metrics summary to the console.
     */
    public static void printSummary(List<ClassMetrics> metricsList) {
        System.out.println("\n=== Metrics Summary ===");
        System.out.println("Total classes analyzed: " + metricsList.size());

        if (!metricsList.isEmpty()) {
            int totalNPM = 0;
            int totalLOC = 0;

            for (ClassMetrics metrics : metricsList) {
                totalNPM += metrics.getNpm();
                totalLOC += metrics.getLoc();
            }

            double avgNPM = (double) totalNPM / metricsList.size();
            double avgLOC = (double) totalLOC / metricsList.size();

            System.out.println("Average NPM: " + String.format("%.2f", avgNPM));
            System.out.println("Average LOC: " + String.format("%.2f", avgLOC));
            System.out.println("Total LOC: " + totalLOC);
        }
    }
}
