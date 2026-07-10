package org.metrics.promise.calculator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for AMC (Average Method Complexity).
 *
 * AMC measures the average method size in a class, defined as:
 *   AMC = total lines of code across all methods / number of methods
 *
 * For each method, the LOC is counted as the number of non-blank lines
 * in the method body (from the opening '{' to the closing '}').
 *
 * If there are no methods, AMC = 0.
 */
public class AMCCalculator {

    /**
     * Calculate AMC for a specific type declaration.
     * Uses the source code string and compilation unit for line mapping.
     * Includes implicit default constructor (LOC=1) if no explicit constructor exists.
     *
     * @param compilationUnit The parsed Java file
     * @param typeDeclaration The type to analyze
     * @param sourceCode      The original source code string
     * @return AMC value (average method size)
     */
    public static double calculateAMCForType(CompilationUnit compilationUnit,
                                              AbstractTypeDeclaration typeDeclaration,
                                              String sourceCode) {
        // Collect all top-level methods
        List<MethodDeclaration> methods = new ArrayList<>();
        MethodCollectorVisitor collector = new MethodCollectorVisitor(methods);
        typeDeclaration.accept(collector);

        // Check for explicit constructor
        boolean hasExplicitConstructor = false;
        for (MethodDeclaration method : methods) {
            if (method.isConstructor()) {
                hasExplicitConstructor = true;
                break;
            }
        }

        boolean isInterface = WMCCalculator.isInterfaceType(typeDeclaration);
        int totalMethods = methods.size();

        // Add implicit default constructor
        if (!hasExplicitConstructor && !isInterface) {
            totalMethods++;
        }

        if (totalMethods == 0) {
            return 0.0;
        }

        String[] lines = sourceCode.split("\n", -1);
        int totalMethodLOC = 0;

        for (MethodDeclaration method : methods) {
            totalMethodLOC += calculateMethodLOC(compilationUnit, method, lines);
        }

        // Default constructor has LOC = 1 (single INVOKESPECIAL + RETURN in bytecode)
        if (!hasExplicitConstructor && !isInterface) {
            totalMethodLOC += 1;
        }

        return (double) totalMethodLOC / totalMethods;
    }

    /**
     * Calculate the LOC of a single method (non-blank lines in the method body).
     */
    private static int calculateMethodLOC(CompilationUnit compilationUnit,
                                           MethodDeclaration method,
                                           String[] lines) {
        if (method.getBody() == null) {
            // Abstract method or interface method with no body
            return 0;
        }

        int startPos = method.getStartPosition();
        int endPos = startPos + method.getLength() - 1;

        int startLine = compilationUnit.getLineNumber(startPos);
        int endLine = compilationUnit.getLineNumber(endPos);

        int loc = 0;
        for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
            if (lineNum < 1 || lineNum > lines.length) {
                continue;
            }
            String line = lines[lineNum - 1];
            if (!line.trim().isEmpty()) {
                loc++;
            }
        }

        return loc;
    }

    /**
     * Visitor that collects only top-level methods (not methods in nested/inner classes).
     */
    private static class MethodCollectorVisitor extends ASTVisitor {
        private final List<MethodDeclaration> methods;
        private int nestingLevel = 0;

        MethodCollectorVisitor(List<MethodDeclaration> methods) {
            this.methods = methods;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (nestingLevel == 1) {
                methods.add(node);
            }
            return false;
        }
    }
}
