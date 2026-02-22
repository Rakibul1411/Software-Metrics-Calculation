package org.promise.metrics.export;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.promise.metrics.model.ClassMetrics;

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
            csvPrinter.printRecord("name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                    "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc");

            // Write data rows
            for (ClassMetrics metrics : metricsList) {
                csvPrinter.printRecord(
                        metrics.getFullyQualifiedName(),
                        metrics.getWmc(),
                        metrics.getDit(),
                        metrics.getNoc(),
                        metrics.getCbo(),
                        metrics.getRfc(),
                        metrics.getLcom(),
                        metrics.getCa(),
                        metrics.getCe(),
                        metrics.getNpm(),
                        String.format("%.2f", metrics.getLcom3()),
                        metrics.getLoc(),
                        String.format("%.2f", metrics.getDam()),
                        metrics.getMoa(),
                        String.format("%.2f", metrics.getMfa()),
                        String.format("%.2f", metrics.getCam()),
                        metrics.getIc(),
                        metrics.getCbm(),
                        String.format("%.2f", metrics.getAmc()),
                        metrics.getMaxCc(),
                        String.format("%.2f", metrics.getAvgCc())
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
                            metrics.getWmc(),  // wmc - Weighted Methods per Class
                            metrics.getDit(),  // dit - Depth of Inheritance Tree
                            metrics.getNoc(),  // noc - Number of Children
                            metrics.getCbo(),  // cbo - Coupling Between Objects
                            metrics.getRfc(),  // rfc - Response For a Class
                            metrics.getLcom(),  // lcom - Lack of Cohesion of Methods
                            metrics.getCa(),   // ca - Afferent Coupling
                            metrics.getCe(),   // ce - Efferent Coupling
                            metrics.getNpm(),
                            String.format("%.2f", metrics.getLcom3()),  // lcom3 - LCOM variant 3
                            metrics.getLoc(),
                            String.format("%.2f", metrics.getDam()),  // dam - Data Access Metric
                            metrics.getMoa(),  // moa - Measure of Aggregation
                            String.format("%.2f", metrics.getMfa()),  // mfa - Measure of Functional Abstraction
                            String.format("%.2f", metrics.getCam()),  // cam - Cohesion Among Methods
                            metrics.getIc(),  // ic - Inheritance Coupling
                            metrics.getCbm(),  // cbm - Coupling Between Methods
                            String.format("%.2f", metrics.getAmc()),  // amc - Average Method Complexity
                            metrics.getMaxCc(),  // max_cc - Maximum Cyclomatic Complexity
                            String.format("%.2f", metrics.getAvgCc()),  // avg_cc - Average Cyclomatic Complexity
                            0   // bug - not calculable from source
                    );
                }
            } else {
                // Write only implemented columns
                csvPrinter.printRecord("name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                        "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc");

                for (ClassMetrics metrics : metricsList) {
                    csvPrinter.printRecord(
                            metrics.getFullyQualifiedName(),
                            metrics.getWmc(),
                            metrics.getDit(),
                            metrics.getNoc(),
                            metrics.getCbo(),
                            metrics.getRfc(),
                            metrics.getLcom(),
                            metrics.getCa(),
                            metrics.getCe(),
                            metrics.getNpm(),
                            String.format("%.2f", metrics.getLcom3()),
                            metrics.getLoc(),
                            String.format("%.2f", metrics.getDam()),
                            metrics.getMoa(),
                            String.format("%.2f", metrics.getMfa()),
                            String.format("%.2f", metrics.getCam()),
                            metrics.getIc(),
                            metrics.getCbm(),
                            String.format("%.2f", metrics.getAmc()),
                            metrics.getMaxCc(),
                            String.format("%.2f", metrics.getAvgCc())
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
            int totalWMC = 0;
            int totalCBO = 0;
            int totalRFC = 0;
            int totalNPM = 0;
            int totalLOC = 0;

            for (ClassMetrics metrics : metricsList) {
                totalWMC += metrics.getWmc();
                totalCBO += metrics.getCbo();
                totalRFC += metrics.getRfc();
                totalNPM += metrics.getNpm();
                totalLOC += metrics.getLoc();
            }

            double avgWMC = (double) totalWMC / metricsList.size();
            double avgCBO = (double) totalCBO / metricsList.size();
            double avgRFC = (double) totalRFC / metricsList.size();
            double avgNPM = (double) totalNPM / metricsList.size();
            double avgLOC = (double) totalLOC / metricsList.size();

            System.out.println("Average WMC: " + String.format("%.2f", avgWMC));
            System.out.println("Average CBO: " + String.format("%.2f", avgCBO));
            System.out.println("Average RFC: " + String.format("%.2f", avgRFC));
            System.out.println("Average NPM: " + String.format("%.2f", avgNPM));
            System.out.println("Average LOC: " + String.format("%.2f", avgLOC));
            System.out.println("Total LOC: " + totalLOC);
        }
    }
}
