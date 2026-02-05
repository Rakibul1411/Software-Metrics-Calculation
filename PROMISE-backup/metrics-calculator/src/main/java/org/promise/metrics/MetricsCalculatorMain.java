package org.promise.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.promise.metrics.calculator.CBMCalculator;
import org.promise.metrics.calculator.CBOCalculator;
import org.promise.metrics.calculator.DITCalculator;
import org.promise.metrics.calculator.ICCalculator;
import org.promise.metrics.calculator.NOCCalculator;
import org.promise.metrics.export.CSVExporter;
import org.promise.metrics.model.ClassMetrics;
import org.promise.metrics.parser.JavaSourceParser;

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

        // Find all .java files recursively, excluding 'optional' package
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/optional/") && !path.toString().contains("\\optional\\"))
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

        // Second pass: Calculate NOC for all classes
        System.out.println("Calculating NOC (Number of Children) for all classes...");
        calculateNOCForAllClasses(allMetrics);

        // Third pass: Calculate DIT for all classes
        System.out.println("Calculating DIT (Depth of Inheritance Tree) for all classes...");
        calculateDITForAllClasses(allMetrics);

        // Fourth pass: Calculate CBO (bidirectional coupling) for all classes
        System.out.println("Calculating CBO (Coupling Between Objects) for all classes...");
        calculateCBOForAllClasses(allMetrics);

        // Fifth pass: Calculate IC (Inheritance Coupling) for all classes
        System.out.println("Calculating IC (Inheritance Coupling) for all classes...");
        calculateICForAllClasses(allMetrics);

        // Sixth pass: Calculate CBM (Coupling Between Methods) for all classes
        System.out.println("Calculating CBM (Coupling Between Methods) for all classes...");
        calculateCBMForAllClasses(allMetrics);

        return allMetrics;
    }

    /**
     * Calculate CBO (Coupling Between Objects) for all classes.
     * This requires knowing all class dependencies (bidirectional coupling).
     */
    private static void calculateCBOForAllClasses(List<ClassMetrics> allMetrics) {
        CBOCalculator cboCalculator = new CBOCalculator();

        // First pass: register all classes and their dependencies
        for (ClassMetrics metrics : allMetrics) {
            cboCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getDependencies()
            );
        }
        
        // Resolve implicit dependencies (like Task inheriting dependency on Project)
        cboCalculator.postProcessDependencies();

        // Second pass: calculate CBO, CA, CE for each class
        for (ClassMetrics metrics : allMetrics) {
            int cbo = cboCalculator.calculateCBO(metrics.getFullyQualifiedName());
            int ca = cboCalculator.calculateCA(metrics.getFullyQualifiedName());
            int ce = cboCalculator.calculateCE(metrics.getFullyQualifiedName());
            metrics.setCbo(cbo);
            metrics.setCa(ca);
            metrics.setCe(ce);
        }
    }

    /**
     * Calculate NOC (Number of Children) for all classes.
     * This requires knowing all classes and their inheritance relationships.
     */
    private static void calculateNOCForAllClasses(List<ClassMetrics> allMetrics) {
        // Create NOC calculator and register all classes
        NOCCalculator nocCalculator = new NOCCalculator();

        // First pass: register all classes and their superclasses
        for (ClassMetrics metrics : allMetrics) {
            nocCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName()
            );
        }

        // Second pass: calculate NOC for each class
        for (ClassMetrics metrics : allMetrics) {
            int noc = nocCalculator.calculateNOC(metrics.getFullyQualifiedName());
            metrics.setNoc(noc);
        }
    }

    /**
     * Calculate DIT (Depth of Inheritance Tree) for all classes.
     * This requires knowing all classes and their inheritance relationships.
     */
    private static void calculateDITForAllClasses(List<ClassMetrics> allMetrics) {
        // Create DIT calculator and register all classes
        DITCalculator ditCalculator = new DITCalculator();

        // First pass: register all classes and their superclasses
        for (ClassMetrics metrics : allMetrics) {
            ditCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.isInterface()
            );
        }

        // Second pass: calculate DIT for each class
        for (ClassMetrics metrics : allMetrics) {
            int dit = ditCalculator.calculateDIT(metrics.getFullyQualifiedName());
            metrics.setDit(dit);
        }
    }

    /**
     * Calculate IC (Inheritance Coupling) for all classes.
     * IC measures how many parent classes are coupled to through method calls.
     */
    private static void calculateICForAllClasses(List<ClassMetrics> allMetrics) {
        // Create IC calculator
        ICCalculator icCalculator = new ICCalculator();

        // First pass: register all classes with their superclass and method names
        for (ClassMetrics metrics : allMetrics) {
            icCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getMethodNames()
            );
        }

        // Second pass: calculate IC for each class
        for (ClassMetrics metrics : allMetrics) {
            int ic = icCalculator.calculateIC(
                    metrics.getFullyQualifiedName(),
                    metrics.getInvokedMethods()
            );
            metrics.setIc(ic);
        }
    }

    /**
     * Calculate CBM (Coupling Between Methods) for all classes.
     * CBM measures the number of method invocations to parent class methods.
     */
    private static void calculateCBMForAllClasses(List<ClassMetrics> allMetrics) {
        // Create CBM calculator
        CBMCalculator cbmCalculator = new CBMCalculator();

        // First pass: register all classes with their superclass and method names
        for (ClassMetrics metrics : allMetrics) {
            cbmCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getMethodNames()
            );
        }

        // Second pass: calculate CBM for each class
        for (ClassMetrics metrics : allMetrics) {
            int cbm = cbmCalculator.calculateCBMSimple(
                    metrics.getFullyQualifiedName(),
                    metrics.getInheritedMethodInvocations()
            );
            metrics.setCbm(cbm);
        }
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
        System.out.println("  - WMC     : Weighted Methods per Class (method count)");
        System.out.println("  - DIT     : Depth of Inheritance Tree");
        System.out.println("  - NOC     : Number of Children (immediate subclasses)");
        System.out.println("  - CBO     : Coupling Between Objects");
        System.out.println("  - NPM     : Number of Public Methods");
        System.out.println("  - LOC     : Lines of Code (excluding blanks and comments)");
    }
}
