package org.promise.metrics.parser;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.promise.metrics.calculator.ComplexityCalculator;
import org.promise.metrics.calculator.LOCCalculator;
import org.promise.metrics.calculator.NPMCalculator;
import org.promise.metrics.model.ClassMetrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return parseSource(sourceCode, filePath.toString());
    }

    /**
     * Parse Java source code and calculate metrics.
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

        // Set compiler options for Java 1.4 (compatible with old Ant source)
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

        for (AbstractTypeDeclaration typeDeclaration : types) {
            ClassMetrics metrics = calculateMetricsForType(compilationUnit, typeDeclaration, sourceCode);
            if (metrics != null) {
                metricsList.add(metrics);
            }

            // Handle nested classes
            List<ClassMetrics> nestedMetrics = extractNestedClasses(compilationUnit, typeDeclaration, sourceCode);
            metricsList.addAll(nestedMetrics);
        }

        return metricsList;
    }

    /**
     * Calculate metrics for a single type (class/interface/enum).
     */
    private static ClassMetrics calculateMetricsForType(CompilationUnit compilationUnit,
                                                        AbstractTypeDeclaration typeDeclaration,
                                                        String sourceCode) {
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
            // Calculate complexity metrics
            ComplexityCalculator.ComplexityResult complexity =
                    ComplexityCalculator.calculateComplexity(compilationUnit);

            metrics.setWmc(complexity.getWmc());
            metrics.setMaxCC(complexity.getMaxCC());
            metrics.setAvgCC(complexity.getAvgCC());
            metrics.setNumberOfMethods(complexity.getMethodCount());

            // Calculate NPM
            int npm = NPMCalculator.calculateNPMForType(typeDeclaration);
            metrics.setNpm(npm);

            // Calculate LOC
            int loc = LOCCalculator.calculateLOCForType(compilationUnit, typeDeclaration, sourceCode);
            metrics.setLoc(loc);

            // Calculate derived metrics (AMC)
            metrics.calculateDerivedMetrics();

        } catch (Exception e) {
            System.err.println("Error calculating metrics for " + fullyQualifiedName + ": " + e.getMessage());
            return null;
        }

        return metrics;
    }

    /**
     * Extract nested classes and calculate their metrics separately.
     */
    private static List<ClassMetrics> extractNestedClasses(CompilationUnit compilationUnit,
                                                           AbstractTypeDeclaration typeDeclaration,
                                                           String sourceCode) {
        List<ClassMetrics> nestedMetrics = new ArrayList<>();

        if (typeDeclaration instanceof TypeDeclaration) {
            TypeDeclaration classDecl = (TypeDeclaration) typeDeclaration;

            // Get nested types
            TypeDeclaration[] nestedTypes = classDecl.getTypes();
            for (TypeDeclaration nestedType : nestedTypes) {
                ClassMetrics metrics = calculateNestedTypeMetrics(compilationUnit, nestedType,
                        typeDeclaration.getName().getIdentifier(), sourceCode);
                if (metrics != null) {
                    nestedMetrics.add(metrics);
                }

                // Recursively handle deeply nested classes
                List<ClassMetrics> deeplyNested = extractNestedClasses(compilationUnit, nestedType, sourceCode);
                nestedMetrics.addAll(deeplyNested);
            }
        }

        return nestedMetrics;
    }

    /**
     * Calculate metrics for nested type with proper naming (OuterClass$InnerClass).
     */
    private static ClassMetrics calculateNestedTypeMetrics(CompilationUnit compilationUnit,
                                                           TypeDeclaration nestedType,
                                                           String outerClassName,
                                                           String sourceCode) {
        String packageName = "";
        if (compilationUnit.getPackage() != null) {
            packageName = compilationUnit.getPackage().getName().getFullyQualifiedName();
        }

        String nestedClassName = nestedType.getName().getIdentifier();
        String fullyQualifiedName = packageName.isEmpty()
                ? outerClassName + "$" + nestedClassName
                : packageName + "." + outerClassName + "$" + nestedClassName;

        ClassMetrics metrics = new ClassMetrics(fullyQualifiedName);

        try {
            // For nested classes, we need to create a visitor specifically for this type
            ComplexityCalculator.ComplexityResult complexity =
                    calculateComplexityForTypeNode(nestedType);

            metrics.setWmc(complexity.getWmc());
            metrics.setMaxCC(complexity.getMaxCC());
            metrics.setAvgCC(complexity.getAvgCC());
            metrics.setNumberOfMethods(complexity.getMethodCount());

            int npm = NPMCalculator.calculateNPMForType(nestedType);
            metrics.setNpm(npm);

            int loc = LOCCalculator.calculateLOCForType(compilationUnit, nestedType, sourceCode);
            metrics.setLoc(loc);

            metrics.calculateDerivedMetrics();

        } catch (Exception e) {
            System.err.println("Error calculating metrics for nested class " + fullyQualifiedName);
            return null;
        }

        return metrics;
    }

    /**
     * Calculate complexity for a specific type node (used for nested classes).
     */
    private static ComplexityCalculator.ComplexityResult calculateComplexityForTypeNode(TypeDeclaration typeNode) {
        MethodDeclaration[] methods = typeNode.getMethods();
        List<Integer> methodComplexities = new ArrayList<>();

        for (MethodDeclaration method : methods) {
            int cc = calculateMethodCC(method);
            methodComplexities.add(cc);
        }

        int wmc = 0;
        int maxCC = 0;
        for (int cc : methodComplexities) {
            wmc += cc;
            if (cc > maxCC) {
                maxCC = cc;
            }
        }

        double avgCC = methodComplexities.size() > 0
                ? (double) wmc / methodComplexities.size()
                : 0.0;

        return new ComplexityCalculator.ComplexityResult(wmc, maxCC, avgCC, methodComplexities.size());
    }

    /**
     * Calculate cyclomatic complexity for a single method.
     */
    private static int calculateMethodCC(MethodDeclaration method) {
        CCVisitor visitor = new CCVisitor();
        if (method.getBody() != null) {
            method.getBody().accept(visitor);
        }
        return 1 + visitor.decisionPoints;
    }

    /**
     * Simple visitor to count decision points.
     */
    private static class CCVisitor extends ASTVisitor {
        int decisionPoints = 0;

        @Override
        public boolean visit(IfStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(SwitchCase node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(CatchClause node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(ConditionalExpression node) {
            decisionPoints++;
            return true;
        }

        @Override
        public boolean visit(InfixExpression node) {
            InfixExpression.Operator operator = node.getOperator();
            if (operator == InfixExpression.Operator.CONDITIONAL_AND ||
                    operator == InfixExpression.Operator.CONDITIONAL_OR) {
                decisionPoints++;
                if (node.hasExtendedOperands()) {
                    decisionPoints += node.extendedOperands().size();
                }
            }
            return true;
        }
    }
}
