package org.promise.metrics.calculator;

import org.eclipse.jdt.core.dom.*;

/**
 * Calculator for Weighted Methods per Class (WMC).
 * 
 * WMC is the sum of the complexities of all methods in a class.
 * In the simplest case (complexity = 1 for each method), WMC equals
 * the number of methods defined in the class.
 * 
 * This implementation counts all methods (public, private, protected, package-private)
 * defined directly in the class (not inherited methods).
 */
public class WMCCalculator {

    /**
     * Calculate WMC for a compilation unit (entire file).
     * Returns the sum of WMC for all top-level types.
     *
     * @param compilationUnit The parsed Java file
     * @return Total WMC for all types in the file
     */
    public static int calculateWMC(CompilationUnit compilationUnit) {
        WMCVisitor visitor = new WMCVisitor();
        compilationUnit.accept(visitor);
        return visitor.methodCount;
    }

    /**
     * Calculate WMC for a specific type declaration.
     * Counts all methods directly declared in the type (not in nested classes).
     *
     * @param typeDeclaration The type to analyze
     * @return Number of methods (WMC) in the type
     */
    public static int calculateWMCForType(AbstractTypeDeclaration typeDeclaration) {
        WMCVisitor visitor = new WMCVisitor();
        typeDeclaration.accept(visitor);
        return visitor.methodCount;
    }

    /**
     * AST Visitor to count methods.
     * Only counts methods directly in the top-level type, NOT in nested/inner classes.
     * Counts all methods regardless of visibility (public, private, protected, package-private).
     */
    private static class WMCVisitor extends ASTVisitor {
        int methodCount = 0;
        int nestingLevel = 0;

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return true; // Continue visiting children
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
            // Only count methods at the top level (nesting level 1)
            // This excludes methods in nested/inner classes
            if (nestingLevel == 1) {
                methodCount++;
            }
            return false; // Don't need to visit method body
        }
    }
}
