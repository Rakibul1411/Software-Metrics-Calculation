package org.metrics.aeeem.export;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.aeeem.model.AeeemClassMetrics;

/**
 * Exporter to write calculated AEEEM metrics to a CSV file.
 */
public class AeeemCSVExporter {

    /**
     * Export AEEEM metrics to a CSV file.
     */
    public static void exportAeeemToCSV(List<AeeemClassMetrics> metricsList, Path outputPath) throws IOException {
        metricsList.sort((m1, m2) -> m1.getFullyQualifiedName().compareTo(m2.getFullyQualifiedName()));

        try (FileWriter writer = new FileWriter(outputPath.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // Write header exactly matching AEEEM attribute names (with colons for bug attributes)
            csvPrinter.printRecord(
                    "name",
                    "ck_oo_numberOfPrivateMethods",
                    "LDHH_lcom",
                    "LDHH_fanIn",
                    "numberOfNonTrivialBugsFoundUntil:",
                    "WCHU_numberOfPublicAttributes",
                    "WCHU_numberOfAttributes",
                    "CvsWEntropy",
                    "LDHH_numberOfPublicMethods",
                    "WCHU_fanIn",
                    "LDHH_numberOfPrivateAttributes",
                    "CvsEntropy",
                    "LDHH_numberOfPublicAttributes",
                    "WCHU_numberOfPrivateMethods",
                    "WCHU_numberOfMethods",
                    "ck_oo_numberOfPublicAttributes",
                    "ck_oo_noc",
                    "numberOfCriticalBugsFoundUntil:",
                    "ck_oo_wmc",
                    "LDHH_numberOfPrivateMethods",
                    "WCHU_numberOfPrivateAttributes",
                    "CvsLogEntropy",
                    "WCHU_noc",
                    "LDHH_numberOfAttributesInherited",
                    "WCHU_wmc",
                    "ck_oo_fanOut",
                    "ck_oo_numberOfLinesOfCode",
                    "ck_oo_numberOfAttributesInherited",
                    "ck_oo_numberOfMethods",
                    "ck_oo_dit",
                    "ck_oo_fanIn",
                    "LDHH_noc",
                    "WCHU_dit",
                    "ck_oo_lcom",
                    "WCHU_numberOfAttributesInherited",
                    "ck_oo_rfc",
                    "LDHH_wmc",
                    "LDHH_numberOfAttributes",
                    "LDHH_numberOfLinesOfCode",
                    "WCHU_fanOut",
                    "WCHU_lcom",
                    "ck_oo_cbo",
                    "WCHU_rfc",
                    "ck_oo_numberOfAttributes",
                    "numberOfHighPriorityBugsFoundUntil:",
                    "ck_oo_numberOfPrivateAttributes",
                    "numberOfMajorBugsFoundUntil:",
                    "WCHU_numberOfPublicMethods",
                    "LDHH_dit",
                    "WCHU_cbo",
                    "CvsLinEntropy",
                    "WCHU_numberOfMethodsInherited",
                    "numberOfBugsFoundUntil:",
                    "LDHH_fanOut",
                    "LDHH_numberOfMethodsInherited",
                    "LDHH_rfc",
                    "ck_oo_numberOfMethodsInherited",
                    "ck_oo_numberOfPublicMethods",
                    "LDHH_cbo",
                    "WCHU_numberOfLinesOfCode",
                    "CvsExpEntropy",
                    "LDHH_numberOfMethods",
                    "class"
            );

            for (AeeemClassMetrics metrics : metricsList) {
                csvPrinter.printRecord(
                        metrics.getFullyQualifiedName(),
                        metrics.getCkOoNumberOfPrivateMethods(),
                        metrics.getLdhhLcom(),
                        metrics.getLdhhFanIn(),
                        metrics.getNumberOfNonTrivialBugsFoundUntil(),
                        metrics.getWchuNumberOfPublicAttributes(),
                        metrics.getWchuNumberOfAttributes(),
                        metrics.getCvsWEntropy(),
                        metrics.getLdhhNumberOfPublicMethods(),
                        metrics.getWchuFanIn(),
                        metrics.getLdhhNumberOfPrivateAttributes(),
                        metrics.getCvsEntropy(),
                        metrics.getLdhhNumberOfPublicAttributes(),
                        metrics.getWchuNumberOfPrivateMethods(),
                        metrics.getWchuNumberOfMethods(),
                        metrics.getCkOoNumberOfPublicAttributes(),
                        metrics.getCkOoNoc(),
                        metrics.getNumberOfCriticalBugsFoundUntil(),
                        metrics.getCkOoWmc(),
                        metrics.getLdhhNumberOfPrivateMethods(),
                        metrics.getWchuNumberOfPrivateAttributes(),
                        metrics.getCvsLogEntropy(),
                        metrics.getWchuNoc(),
                        metrics.getLdhhNumberOfAttributesInherited(),
                        metrics.getWchuWmc(),
                        metrics.getCkOoFanOut(),
                        metrics.getCkOoNumberOfLinesOfCode(),
                        metrics.getCkOoNumberOfAttributesInherited(),
                        metrics.getCkOoNumberOfMethods(),
                        metrics.getCkOoDit(),
                        metrics.getCkOoFanIn(),
                        metrics.getLdhhNoc(),
                        metrics.getWchuDit(),
                        metrics.getCkOoLcom(),
                        metrics.getWchuNumberOfAttributesInherited(),
                        metrics.getCkOoRfc(),
                        metrics.getLdhhWmc(),
                        metrics.getLdhhNumberOfAttributes(),
                        metrics.getLdhhNumberOfLinesOfCode(),
                        metrics.getWchuFanOut(),
                        metrics.getWchuLcom(),
                        metrics.getCkOoCbo(),
                        metrics.getWchuRfc(),
                        metrics.getCkOoNumberOfAttributes(),
                        metrics.getNumberOfHighPriorityBugsFoundUntil(),
                        metrics.getCkOoNumberOfPrivateAttributes(),
                        metrics.getNumberOfMajorBugsFoundUntil(),
                        metrics.getWchuNumberOfPublicMethods(),
                        metrics.getLdhhDit(),
                        metrics.getWchuCbo(),
                        metrics.getCvsLinEntropy(),
                        metrics.getWchuNumberOfMethodsInherited(),
                        metrics.getNumberOfBugsFoundUntil(),
                        metrics.getLdhhFanOut(),
                        metrics.getLdhhNumberOfMethodsInherited(),
                        metrics.getLdhhRfc(),
                        metrics.getCkOoNumberOfMethodsInherited(),
                        metrics.getCkOoNumberOfPublicMethods(),
                        metrics.getLdhhCbo(),
                        metrics.getWchuNumberOfLinesOfCode(),
                        metrics.getCvsExpEntropy(),
                        metrics.getLdhhNumberOfMethods(),
                        metrics.getClassification()
                );
            }
        }

        System.out.println("Exported " + metricsList.size() + " AEEEM class metrics to: " + outputPath);
    }

    /**
     * Print metrics summary for AEEEM metrics to the console.
     */
    public static void printAeeemSummary(List<AeeemClassMetrics> metricsList) {
        System.out.println("\n=== AEEEM Metrics Summary ===");
        System.out.println("Total classes analyzed: " + metricsList.size());
    }
}
