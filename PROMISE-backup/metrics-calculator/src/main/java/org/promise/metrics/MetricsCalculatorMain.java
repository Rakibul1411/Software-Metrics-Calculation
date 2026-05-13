package org.promise.metrics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.promise.metrics.calculator.CBMCalculator;
import org.promise.metrics.calculator.CBOCalculator;
import org.promise.metrics.calculator.DITCalculator;
import org.promise.metrics.calculator.ICCalculator;
import org.promise.metrics.calculator.MFACalculator;
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

        String[] sourceDirs = args[0].split(",");
        String outputFile = args.length > 1 ? args[1] : "output/metrics.csv";

        // Parse optional flags from remaining arguments
        boolean fullFormat = false;
        String filterFile = null;
        for (int i = 2; i < args.length; i++) {
            if ("--full-format".equals(args[i])) {
                fullFormat = true;
            } else if ("--filter".equals(args[i]) && i + 1 < args.length) {
                filterFile = args[++i];
            }
        }

        System.out.println("Java Metrics Calculator");
        System.out.println("======================");
        System.out.println("Source directories: " + String.join(", ", sourceDirs));
        System.out.println("Output file: " + outputFile);
        if (filterFile != null) {
            System.out.println("Filter file: " + filterFile);
        }
        System.out.println();

        try {
            // Calculate metrics
            List<ClassMetrics> allMetrics = calculateMetricsForDirectories(sourceDirs);

            if (allMetrics.isEmpty()) {
                System.err.println("No Java files found or no metrics calculated.");
                System.exit(1);
            }

            // Filter: keep only classes that exist in the predefined dataset
            if (filterFile != null) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                int beforeSize = allMetrics.size();
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
                int afterSize = allMetrics.size();
                System.out.println("Filtering: " + beforeSize + " -> " + afterSize + " classes (removed " + (beforeSize - afterSize) + " classes not in predefined dataset)");
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
     * Load class names (first column) from a predefined PROMISE CSV file.
     * The CSV has no header; the first column is the fully qualified class name.
     */
    private static Set<String> loadClassNamesFromCSV(String csvPath) throws IOException {
        Set<String> classNames = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // First column is the class name (comma-separated)
                int commaIndex = line.indexOf(',');
                if (commaIndex > 0) {
                    classNames.add(line.substring(0, commaIndex).trim());
                }
            }
        }
        System.out.println("Loaded " + classNames.size() + " class names from predefined dataset: " + csvPath);
        return classNames;
    }

    /**
     * Calculate metrics for all Java files in multiple directories (recursively).
     */
    private static List<ClassMetrics> calculateMetricsForDirectories(String[] dirPaths) throws IOException {
        List<ClassMetrics> allMetrics = new ArrayList<>();
        
        System.out.println("Scanning for Java files...");

        for (String dirPath : dirPaths) {
            Path sourcePath = Paths.get(dirPath.trim());

            if (!Files.exists(sourcePath)) {
                System.err.println("Warning: Source directory does not exist: " + dirPath);
                continue;
            }

            if (!Files.isDirectory(sourcePath)) {
                System.err.println("Warning: Source path is not a directory: " + dirPath);
                continue;
            }

            // Find all .java files recursively, excluding 'optional' and 'test' packages
            try (Stream<Path> paths = Files.walk(sourcePath)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().contains("/optional/") && !path.toString().contains("\\optional\\"))
                        .filter(path -> !path.toString().contains("/test/") && !path.toString().contains("\\test\\"))
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

        // Seventh pass: Calculate MFA (Measure of Functional Abstraction) for all classes
        System.out.println("Calculating MFA (Measure of Functional Abstraction) for all classes...");
        calculateMFAForAllClasses(allMetrics);

        // Eighth pass: Calculate MOA (Measure of Aggregation) for all classes
        System.out.println("Calculating MOA (Measure of Aggregation) for all classes...");
        calculateMOAForAllClasses(allMetrics);

        return allMetrics;
    }

    /**
     * Calculate MOA (Measure of Aggregation) for all classes.
     * MOA is the count of fields whose types are user-defined classes (project classes).
     */
    private static void calculateMOAForAllClasses(List<ClassMetrics> allMetrics) {
        java.util.Set<String> projectClassNames = new java.util.HashSet<>();
        
        // Build a registry of all classes defined in this project
        for (ClassMetrics metrics : allMetrics) {
            projectClassNames.add(metrics.getFullyQualifiedName());
            
            // Also add simple name since AST fields often only use simple names
            String simpleName = metrics.getFullyQualifiedName();
            int lastDot = simpleName.lastIndexOf('.');
            if (lastDot >= 0) {
                simpleName = simpleName.substring(lastDot + 1);
            }
            projectClassNames.add(simpleName);
        }

        // Compare each field against the project classes
        for (ClassMetrics metrics : allMetrics) {
            int moa = 0;
            for (String fieldType : metrics.getFieldTypes()) {
                if (projectClassNames.contains(fieldType)) {
                    moa++;
                }
            }
            metrics.setMoa(moa);
        }
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
     * Calculate MFA (Measure of Functional Abstraction) for all classes.
     * MFA measures the ratio of inherited methods to total methods available.
     * This requires knowing all classes and their inheritance relationships.
     */
    private static void calculateMFAForAllClasses(List<ClassMetrics> allMetrics) {
        MFACalculator mfaCalculator = new MFACalculator();

        // First pass: register all classes with their superclass and method names
        for (ClassMetrics metrics : allMetrics) {
            mfaCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getMethodNames()
            );
        }

        // Second pass: calculate MFA for each class
        for (ClassMetrics metrics : allMetrics) {
            if (metrics.isInterface()) {
                metrics.setMfa(0.0);
            } else {
                double mfa = mfaCalculator.calculateMFA(metrics.getFullyQualifiedName());
                metrics.setMfa(mfa);
            }
        }
    }

    /**
     * Print usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: java -jar metrics-calculator.jar <source-directories> [output-file] [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  source-directories  Comma-separated paths to Java source code directories");
        System.out.println("  output-file       (Optional) Path to output CSV file (default: output/metrics.csv)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --full-format         Export with all 22 columns (unimplemented metrics as 0)");
        System.out.println("  --filter <csv-file>   Filter output to only include classes present in the");
        System.out.println("                        given predefined PROMISE CSV file");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Basic usage");
        System.out.println("  java -jar metrics-calculator.jar \"src/main\" output/metrics.csv");
        System.out.println();
        System.out.println("  # With filtering (output will only contain classes from the predefined CSV)");
        System.out.println("  java -jar metrics-calculator.jar \"src/main\" output/ant-1.3.csv --filter ../bug-data/ant/ant-1.3.csv");
        System.out.println();
        System.out.println("Calculated Metrics:");
        System.out.println("  - WMC, DIT, NOC, CBO, RFC, LCOM, CA, CE, NPM");
        System.out.println("  - LCOM3, LOC, DAM, MOA, MFA, CAM, IC, CBM, AMC");
        System.out.println("  - MAX_CC, AVG_CC");
    }
}
