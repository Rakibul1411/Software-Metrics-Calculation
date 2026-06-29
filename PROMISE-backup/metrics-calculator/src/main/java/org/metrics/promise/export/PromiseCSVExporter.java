package org.metrics.promise.export;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.promise.model.PromiseClassMetrics;

/**
 * Exporter to write calculated PROMISE metrics to a CSV file.
 */
public class PromiseCSVExporter {

    /**
     * Export PROMISE metrics to a CSV file.
     */
    public static void exportPromiseToCSV(List<PromiseClassMetrics> metricsList, Path outputPath, boolean fullFormat) throws IOException {
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (FileWriter writer = new FileWriter(outputPath.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            if (fullFormat) {
                csvPrinter.printRecord("name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                        "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc", "bug");
            } else {
                csvPrinter.printRecord("name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                        "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc");
            }

            for (PromiseClassMetrics metrics : metricsList) {
                if (fullFormat) {
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
                            String.format("%.2f", metrics.getAvgCc()),
                            0
                    );
                } else {
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

        System.out.println("Exported " + metricsList.size() + " PROMISE class metrics to: " + outputPath);
    }

    /**
     * Print metrics summary for PROMISE metrics to the console.
     */
    public static void printPromiseSummary(List<PromiseClassMetrics> metricsList) {
        System.out.println("\n=== PROMISE Metrics Summary ===");
        System.out.println("Total classes analyzed: " + metricsList.size());

        if (!metricsList.isEmpty()) {
            int totalWMC = 0;
            int totalCBO = 0;
            int totalRFC = 0;
            int totalNPM = 0;
            int totalLOC = 0;

            for (PromiseClassMetrics metrics : metricsList) {
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
