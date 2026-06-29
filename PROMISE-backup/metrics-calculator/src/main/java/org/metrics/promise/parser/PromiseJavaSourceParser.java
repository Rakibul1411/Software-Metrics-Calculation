package org.metrics.promise.parser;

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
import org.metrics.promise.calculator.AMCCalculator;
import org.metrics.promise.calculator.CAMCalculator;
import org.metrics.promise.calculator.CBMCalculator;
import org.metrics.promise.calculator.CBOCalculator;
import org.metrics.promise.calculator.CyclomaticComplexityCalculator;
import org.metrics.promise.calculator.DAMCalculator;
import org.metrics.promise.calculator.DITCalculator;
import org.metrics.promise.calculator.ICCalculator;
import org.metrics.promise.calculator.LCOM3Calculator;
import org.metrics.promise.calculator.LCOMCalculator;
import org.metrics.promise.calculator.LOCCalculator;
import org.metrics.promise.calculator.MOACalculator;
import org.metrics.promise.calculator.NOCCalculator;
import org.metrics.promise.calculator.NPMCalculator;
import org.metrics.promise.calculator.RFCCalculator;
import org.metrics.promise.calculator.WMCCalculator;
import org.metrics.promise.model.PromiseClassMetrics;

/**
 * Parser for Java source files to calculate PROMISE metrics.
 */
public class PromiseJavaSourceParser {

    /**
     * Parse a Java source file and calculate PROMISE metrics for all classes.
     */
    public static List<PromiseClassMetrics> parsePromiseFile(Path filePath) throws IOException {
        String sourceCode = new String(Files.readAllBytes(filePath));
        return parsePromiseSource(sourceCode, filePath.toString());
    }

    /**
     * Parse Java source code and calculate PROMISE metrics.
     */
    public static List<PromiseClassMetrics> parsePromiseSource(String sourceCode, String fileName) {
        List<PromiseClassMetrics> metricsList = new ArrayList<>();
        CompilationUnit compilationUnit = parseCompilationUnit(sourceCode, fileName);
        if (compilationUnit == null) return metricsList;

        @SuppressWarnings("unchecked")
        List<AbstractTypeDeclaration> types = compilationUnit.types();
        for (AbstractTypeDeclaration typeDeclaration : types) {
            PromiseClassMetrics metrics = calculatePromiseMetricsForType(compilationUnit, typeDeclaration, sourceCode);
            if (metrics != null) {
                metricsList.add(metrics);
            }
        }
        return metricsList;
    }

    private static CompilationUnit parseCompilationUnit(String sourceCode, String fileName) {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);

        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_4);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_4);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_4);
        parser.setCompilerOptions(options);

        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        if (compilationUnit.getProblems().length > 0) {
            System.err.println("Warning: Parse problems in " + fileName);
            for (IProblem problem : compilationUnit.getProblems()) {
                if (problem.isError()) {
                    System.err.println("  " + problem.getMessage());
                }
            }
        }
        return compilationUnit;
    }

    private static PromiseClassMetrics calculatePromiseMetricsForType(CompilationUnit compilationUnit,
                                                                      AbstractTypeDeclaration typeDeclaration,
                                                                      String sourceCode) {
        String packageName = "";
        if (compilationUnit.getPackage() != null) {
            packageName = compilationUnit.getPackage().getName().getFullyQualifiedName();
        }

        String className = typeDeclaration.getName().getIdentifier();
        String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

        PromiseClassMetrics metrics = new PromiseClassMetrics(fullyQualifiedName);

        try {
            int wmc = WMCCalculator.calculateWMCForType(typeDeclaration);
            metrics.setWmc(wmc);

            @SuppressWarnings("unchecked")
            List<ImportDeclaration> imports = compilationUnit.imports();
            Set<String> dependencies = CBOCalculator.extractDependencies(typeDeclaration, fullyQualifiedName, imports);
            metrics.setDependencies(dependencies);

            int rfc = RFCCalculator.calculateRFCForType(typeDeclaration);
            metrics.setRfc(rfc);

            int npm = NPMCalculator.calculateNPMForType(typeDeclaration);
            metrics.setNpm(npm);

            int loc = LOCCalculator.calculateLOCForType(compilationUnit, typeDeclaration, sourceCode);
            metrics.setLoc(loc);

            java.util.List<String> fieldTypes = MOACalculator.extractFieldTypes(typeDeclaration);
            metrics.setFieldTypes(fieldTypes);

            String superclassName = NOCCalculator.extractSuperclassName(typeDeclaration);
            metrics.setSuperclassName(superclassName);

            boolean isInterface = DITCalculator.isInterface(typeDeclaration);
            metrics.setInterface(isInterface);

            Set<String> methodNames = ICCalculator.extractMethodNames(typeDeclaration);
            metrics.setMethodNames(methodNames);
            
            Set<String> invokedMethods = ICCalculator.extractInvokedMethods(typeDeclaration);
            metrics.setInvokedMethods(invokedMethods);

            Set<String> inheritedMethodInvocations = CBMCalculator.extractInheritedMethodInvocations(typeDeclaration);
            metrics.setInheritedMethodInvocations(inheritedMethodInvocations);

            CyclomaticComplexityCalculator.CCResult ccResult = CyclomaticComplexityCalculator.calculateCCForType(typeDeclaration);
            metrics.setMaxCc(ccResult.getMaxCC());
            metrics.setAvgCc(ccResult.getAvgCC());

            Set<String> instanceVariables = LCOMCalculator.extractInstanceVariables(typeDeclaration);
            java.util.Map<String, Set<String>> methodFieldAccess = LCOMCalculator.extractMethodFieldAccess(typeDeclaration, instanceVariables);
            int lcom = LCOMCalculator.calculateLCOMForType(typeDeclaration);
            metrics.setLcom(lcom);

            boolean isIfaceType = WMCCalculator.isInterfaceType(typeDeclaration);

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

            double lcom3 = LCOM3Calculator.calculateLCOM3(instanceVariables, lcom3MethodFieldAccess, isIfaceType);
            metrics.setLcom3(lcom3);

            double amc = AMCCalculator.calculateAMCForType(compilationUnit, typeDeclaration, sourceCode);
            metrics.setAmc(amc);

            double dam = DAMCalculator.calculateDAMForType(typeDeclaration);
            metrics.setDam(dam);

            double cam = CAMCalculator.calculateCAMForType(typeDeclaration);
            metrics.setCam(cam);

        } catch (Exception e) {
            System.err.println("Error calculating metrics for " + fullyQualifiedName + ": " + e.getMessage());
            return null;
        }

        return metrics;
    }
}
