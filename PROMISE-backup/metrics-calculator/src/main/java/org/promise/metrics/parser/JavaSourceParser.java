package org.promise.metrics.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.promise.metrics.calculator.DITCalculator;
import org.promise.metrics.calculator.LOCCalculator;
import org.promise.metrics.calculator.NOCCalculator;
import org.promise.metrics.calculator.NPMCalculator;
import org.promise.metrics.calculator.WMCCalculator;
import org.promise.metrics.model.ClassMetrics;

/**
 * Parser for Java source files using Eclipse JDT.
 */
public class JavaSourceParser {

    /**
     * Parse a Java source file and calculate metrics for all classes.
     *
     * @param filePath Path to the .java file
     * @return List of ClassMetrics (one per class/interface/enum in the file)
     * @throws IOException If a file cannot be read
     */
    public static List<ClassMetrics> parseFile(Path filePath) throws IOException {
        String sourceCode = new String(Files.readAllBytes(filePath));
//        System.out.println(
//                "Parsing file:" + filePath.toString() +
//                        "\nSource code: " + sourceCode
//        );
        return parseSource(sourceCode, filePath.toString());
    }

    /**
     * Parse Java source code and calculate metrics.
     * Includes all top-level classes in the file (not inner classes).
     *
     * @param sourceCode The Java source code
     * @param fileName   The file name (for error reporting)
     * @return List of ClassMetrics
     */
    public static List<ClassMetrics> parseSource(String sourceCode, String fileName) {
        List<ClassMetrics> metricsList = new ArrayList<>();

        // Create AST parser
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);

        //System.out.println("Parsing file: " + parser);

        // Set compiler options for Java 1.4 (compatible with an old Ant source)
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_4);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_4);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_4);
        parser.setCompilerOptions(options);

        // Parse the source
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        // Check for parse errors
        if (compilationUnit.getProblems().length > 0) {
            System.err.println("Warning: Parse problems in " + fileName);
            for (IProblem problem : compilationUnit.getProblems()) {
                if (problem.isError()) {
                    System.err.println("  " + problem.getMessage());
                }
            }
        }

        // Get all type declarations (classes, interfaces, enums)
        @SuppressWarnings("unchecked")
        List<AbstractTypeDeclaration> types = compilationUnit.types();

        // Include ALL top-level classes in the file (PROMISE dataset includes secondary classes like MailPrintStream)
        for (AbstractTypeDeclaration typeDeclaration : types) {

            ClassMetrics metrics = calculateMetricsForType(compilationUnit, typeDeclaration, sourceCode);
            if (metrics != null) {
                metricsList.add(metrics);
            }

            // Note: Inner/nested classes are NOT output separately.
            // Their LOC is included in the parent class LOC since we count
            // all non-blank lines within the parent class body brackets.
            // This matches the PROMISE dataset behavior where inner classes
            // like ProjectHelper$AbstractHandler are not listed separately.
        }

        return metricsList;
    }


    /**
     * Calculate metrics for a single type (class/interface/enum).
     */
    private static ClassMetrics calculateMetricsForType(CompilationUnit compilationUnit,
                                                        AbstractTypeDeclaration typeDeclaration,
                                                        String sourceCode) {
        //System.out.println("Calculating metrics for " + typeDeclaration + ":");
        // Get a fully qualified name
        String packageName = "";
        if (compilationUnit.getPackage() != null) {
            packageName = compilationUnit.getPackage().getName().getFullyQualifiedName();
        }

        String className = typeDeclaration.getName().getIdentifier();
        String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

        // Create a metrics object
        ClassMetrics metrics = new ClassMetrics(fullyQualifiedName);

        try {
            // Calculate WMC (Weighted Methods per Class)
            int wmc = WMCCalculator.calculateWMCForType(typeDeclaration);
            metrics.setWmc(wmc);

            // Calculate NPM
            int npm = NPMCalculator.calculateNPMForType(typeDeclaration);
            metrics.setNpm(npm);

            // Calculate LOC
            int loc = LOCCalculator.calculateLOCForType(compilationUnit, typeDeclaration, sourceCode);
            metrics.setLoc(loc);

            // Extract superclass name for NOC and DIT calculation
            String superclassName = NOCCalculator.extractSuperclassName(typeDeclaration);
            metrics.setSuperclassName(superclassName);

            // Check if it's an interface (for DIT calculation)
            boolean isInterface = DITCalculator.isInterface(typeDeclaration);
            metrics.setInterface(isInterface);

        } catch (Exception e) {
            System.err.println("Error calculating metrics for " + fullyQualifiedName + ": " + e.getMessage());
            return null;
        }


        return metrics;
    }
}
