package org.promise.metrics.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.promise.metrics.calculator.AMCCalculator;
import org.promise.metrics.calculator.CAMCalculator;
import org.promise.metrics.calculator.CBMCalculator;
import org.promise.metrics.calculator.CBOCalculator;
import org.promise.metrics.calculator.CyclomaticComplexityCalculator;
import org.promise.metrics.calculator.DAMCalculator;
import org.promise.metrics.calculator.DITCalculator;
import org.promise.metrics.calculator.ICCalculator;
import org.promise.metrics.calculator.LCOM3Calculator;
import org.promise.metrics.calculator.LCOMCalculator;
import org.promise.metrics.calculator.LOCCalculator;
import org.promise.metrics.calculator.MOACalculator;
import org.promise.metrics.calculator.NOCCalculator;
import org.promise.metrics.calculator.NPMCalculator;
import org.promise.metrics.calculator.RFCCalculator;
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

            // Extract dependencies for CBO calculation (two-pass approach)
            @SuppressWarnings("unchecked")
            List<ImportDeclaration> imports = compilationUnit.imports();
            Set<String> dependencies = CBOCalculator.extractDependencies(typeDeclaration, fullyQualifiedName, imports);
            metrics.setDependencies(dependencies);

            // Calculate RFC (Response For a Class)
            int rfc = RFCCalculator.calculateRFCForType(typeDeclaration);
            metrics.setRfc(rfc);

            // Calculate NPM
            int npm = NPMCalculator.calculateNPMForType(typeDeclaration);
            metrics.setNpm(npm);

            // Calculate LOC
            int loc = LOCCalculator.calculateLOCForType(compilationUnit, typeDeclaration, sourceCode);
            metrics.setLoc(loc);

            // Extract field types for MOA (calculated in 2nd pass)
            java.util.List<String> fieldTypes = MOACalculator.extractFieldTypes(typeDeclaration);
            metrics.setFieldTypes(fieldTypes);

            // Extract superclass name for NOC and DIT calculation
            String superclassName = NOCCalculator.extractSuperclassName(typeDeclaration);
            metrics.setSuperclassName(superclassName);

            // Check if it's an interface (for DIT calculation)
            boolean isInterface = DITCalculator.isInterface(typeDeclaration);
            metrics.setInterface(isInterface);

            // Extract method names and invoked methods for IC calculation
            Set<String> methodNames = ICCalculator.extractMethodNames(typeDeclaration);
            metrics.setMethodNames(methodNames);
            
            Set<String> invokedMethods = ICCalculator.extractInvokedMethods(typeDeclaration);
            metrics.setInvokedMethods(invokedMethods);

            // Extract inherited method invocations for CBM calculation
            Set<String> inheritedMethodInvocations = CBMCalculator.extractInheritedMethodInvocations(typeDeclaration);
            metrics.setInheritedMethodInvocations(inheritedMethodInvocations);

            // Calculate Cyclomatic Complexity (max_cc and avg_cc)
            CyclomaticComplexityCalculator.CCResult ccResult = CyclomaticComplexityCalculator.calculateCCForType(typeDeclaration);
            metrics.setMaxCc(ccResult.getMaxCC());
            metrics.setAvgCc(ccResult.getAvgCC());

            // Calculate LCOM (Lack of Cohesion of Methods - Chidamber & Kemerer)
            Set<String> instanceVariables = LCOMCalculator.extractInstanceVariables(typeDeclaration);
            java.util.Map<String, Set<String>> methodFieldAccess = LCOMCalculator.extractMethodFieldAccess(typeDeclaration, instanceVariables);
            int lcom = LCOMCalculator.calculateLCOMForType(typeDeclaration);
            metrics.setLcom(lcom);

            // Determine if type is an interface and if it has an explicit constructor
            boolean isIfaceType = WMCCalculator.isInterfaceType(typeDeclaration);

            // Check for explicit constructor via method declarations
            java.util.Map<String, Set<String>> lcom3MethodFieldAccess = new java.util.HashMap<>(methodFieldAccess);
            boolean foundConstructor = false;
            for (Object bodyDecl : typeDeclaration.bodyDeclarations()) {
                if (bodyDecl instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
                    if (((org.eclipse.jdt.core.dom.MethodDeclaration) bodyDecl).isConstructor()) {
                        foundConstructor = true;
                        break;
                    }
                }
            }
            if (!foundConstructor && !isIfaceType) {
                lcom3MethodFieldAccess.put("<init>#default", new java.util.HashSet<>());
            }

            // Calculate LCOM3 (Henderson-Sellers variant) — reuses data from LCOM
            double lcom3 = LCOM3Calculator.calculateLCOM3(instanceVariables, lcom3MethodFieldAccess, isIfaceType);
            metrics.setLcom3(lcom3);

            // Calculate AMC (Average Method Complexity)
            double amc = AMCCalculator.calculateAMCForType(compilationUnit, typeDeclaration, sourceCode);
            metrics.setAmc(amc);

            // Calculate DAM (Data Access Metric)
            double dam = DAMCalculator.calculateDAMForType(typeDeclaration);
            metrics.setDam(dam);

            // Calculate CAM (Cohesion Among Methods)
            double cam = CAMCalculator.calculateCAMForType(typeDeclaration);
            metrics.setCam(cam);

        } catch (Exception e) {
            System.err.println("Error calculating metrics for " + fullyQualifiedName + ": " + e.getMessage());
            return null;
        }


        return metrics;
    }
}
