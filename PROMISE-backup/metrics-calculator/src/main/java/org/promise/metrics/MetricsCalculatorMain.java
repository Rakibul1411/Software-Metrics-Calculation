package org.promise.metrics;

import org.promise.metrics.export.CSVExporter;
import org.promise.metrics.model.ClassMetrics;
import org.promise.metrics.parser.JavaSourceParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Main entry point for the Metrics Calculator.
 * Scans Java source files and calculates code metrics.
 */
public class MetricsCalculatorMain {

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputFile = args.length > 1 ? args[1] : "output/metrics.csv";
        boolean fullFormat = args.length > 2 && args[2].equals("--full-format");

        System.out.println("Java Metrics Calculator");
        System.out.println("======================");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output file: " + outputFile);
        System.out.println();

        try {
            // Calculate metrics
            List<ClassMetrics> allMetrics = calculateMetricsForDirectory(sourceDir);

            if (allMetrics.isEmpty()) {
                System.err.println("No Java files found or no metrics calculated.");
                System.exit(1);
            }

            // Create an output directory if it doesn't exist
            Path outputPath = Paths.get(outputFile);
            Files.createDirectories(outputPath.getParent());

            // Export to CSV
            if (fullFormat) {
                CSVExporter.exportToCSVWithFullFormat(allMetrics, outputPath, true);
            } else {
                CSVExporter.exportToCSV(allMetrics, outputPath);
            }

            // Print summary
            CSVExporter.printSummary(allMetrics);

            System.out.println("\nMetrics calculation completed successfully!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Calculate metrics for all Java files in a directory (recursively).
     */
    private static List<ClassMetrics> calculateMetricsForDirectory(String dirPath) throws IOException {
        List<ClassMetrics> allMetrics = new ArrayList<>();
        Path sourcePath = Paths.get(dirPath);

        if (!Files.exists(sourcePath)) {
            throw new IOException("Source directory does not exist: " + dirPath);
        }

        if (!Files.isDirectory(sourcePath)) {
            throw new IOException("Source path is not a directory: " + dirPath);
        }

        System.out.println("Scanning for Java files...");

        // Find all .java files recursively
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(javaFile -> {
                        try {
                            System.out.println("Processing: " + javaFile);
                            List<ClassMetrics> metrics = JavaSourceParser.parseFile(javaFile);
                            allMetrics.addAll(metrics);

                            // Print each class found
                            for (ClassMetrics m : metrics) {
                                System.out.println("  - " + m.getFullyQualifiedName());
                            }

                        } catch (Exception e) {
                            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                        }
                    });
        }

        System.out.println("\nTotal classes found: " + allMetrics.size());
        return allMetrics;
    }

    /**
     * Print usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: java -jar metrics-calculator.jar <source-directory> [output-file] [--full-format]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  source-directory  Path to the Java source code directory");
        System.out.println("  output-file       (Optional) Path to output CSV file (default: output/metrics.csv)");
        System.out.println("  --full-format     (Optional) Export with all 22 columns (unimplemented metrics as 0)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Basic usage");
        System.out.println("  java -jar metrics-calculator.jar ../source\\ code/ant/jakarta-ant-1.3/src/main");
        System.out.println();
        System.out.println("  # Specify output file");
        System.out.println("  java -jar metrics-calculator.jar ../source\\ code/ant/jakarta-ant-1.3/src/main output/ant-1.3.csv");
        System.out.println();
        System.out.println("  # Using Maven exec plugin");
        System.out.println("  mvn exec:java -Dexec.args=\"../source\\ code/ant/jakarta-ant-1.3/src/main\"");
        System.out.println();
        System.out.println("Calculated Metrics:");
        System.out.println("  - WMC     : Weighted Methods per Class (sum of cyclomatic complexity)");
        System.out.println("  - NPM     : Number of Public Methods");
        System.out.println("  - LOC     : Lines of Code (excluding blanks and comments)");
        System.out.println("  - AMC     : Average Method Complexity");
        System.out.println("  - MAX_CC  : Maximum Cyclomatic Complexity");
        System.out.println("  - AVG_CC  : Average Cyclomatic Complexity");
    }
}
