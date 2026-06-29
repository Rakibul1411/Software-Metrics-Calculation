package org.metrics.aeeem.parser;

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
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.calculator.AeeemStaticMetricsCalculator;

/**
 * Parser for Java source files to calculate AEEEM metrics.
 */
public class AeeemJavaSourceParser {

    /**
     * Parse a Java source file and calculate AEEEM metrics for all classes.
     */
    public static List<AeeemMetricResult> parseAeeemFile(Path filePath) throws IOException {
        String sourceCode = new String(Files.readAllBytes(filePath));
        return parseAeeemSource(sourceCode, filePath.toString());
    }

    /**
     * Parse Java source code and calculate AEEEM metrics.
     */
    public static List<AeeemMetricResult> parseAeeemSource(String sourceCode, String fileName) {
        List<AeeemMetricResult> metricsList = new ArrayList<>();
        CompilationUnit compilationUnit = parseCompilationUnit(sourceCode, fileName);
        if (compilationUnit == null) return metricsList;

        @SuppressWarnings("unchecked")
        List<AbstractTypeDeclaration> types = compilationUnit.types();
        for (AbstractTypeDeclaration typeDeclaration : types) {
            AeeemMetricResult metrics = AeeemStaticMetricsCalculator.calculateAeeemForType(compilationUnit, typeDeclaration, sourceCode);
            if (metrics != null) {
                metricsList.add(metrics);
            }
        }
        return metricsList;
    }

    private static CompilationUnit parseCompilationUnit(String sourceCode, String fileName) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);

        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_17);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
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
}
