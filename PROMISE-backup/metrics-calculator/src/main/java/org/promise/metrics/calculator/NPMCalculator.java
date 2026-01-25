package org.promise.metrics.calculator;

import org.eclipse.jdt.core.dom.*;

/**
 * Calculator for Number of Public Methods (NPM).
 */
public class NPMCalculator {

    /**
     * Calculate the number of public methods in a compilation unit.
     *
     * @param compilationUnit The parsed Java file
     * @return Number of public methods
     */
    public static int calculateNPM(CompilationUnit compilationUnit) {
        NPMVisitor visitor = new NPMVisitor();
        compilationUnit.accept(visitor);
        return visitor.publicMethodCount;
    }

    /**
     * Calculate NPM for a specific type declaration.
     *
     * @param typeDeclaration The type to analyze
     * @return Number of public methods in the type
     */
    public static int calculateNPMForType(AbstractTypeDeclaration typeDeclaration) {
        NPMVisitor visitor = new NPMVisitor();
        typeDeclaration.accept(visitor);
        return visitor.publicMethodCount;
    }

    /**
     * AST Visitor to count public methods.
     * Only counts methods directly in the top-level type, NOT in nested/inner classes.
     */
    private static class NPMVisitor extends ASTVisitor {
        int publicMethodCount = 0;
        int nestingLevel = 0;

        @Override
        public boolean visit(MethodDeclaration node) {
            // Only count methods at the top level (not in nested classes)
            if (nestingLevel == 1) {
                int modifiers = node.getModifiers();
                // Check if the method is public
                if (Modifier.isPublic(modifiers)) {
                    publicMethodCount++;
                }
            }
            // Don't visit children of method
            return false;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            // Only visit the first level type, not nested types
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(AnnotationTypeDeclaration node) {
            nestingLevel--;
        }
    }

    /**
     * Count all methods (public, protected, private) in a compilation unit.
     * Used for calculating average metrics.
     *
     * @param compilationUnit The parsed Java file
     * @return Total number of methods
     */
    public static int countAllMethods(CompilationUnit compilationUnit) {
        MethodCountVisitor visitor = new MethodCountVisitor();
        compilationUnit.accept(visitor);
        return visitor.methodCount;
    }

    /**
     * Count all methods in a specific type declaration.
     *
     * @param typeDeclaration The type to analyze
     * @return Total number of methods
     */
    public static int countAllMethodsForType(AbstractTypeDeclaration typeDeclaration) {
        MethodCountVisitor visitor = new MethodCountVisitor();
        typeDeclaration.accept(visitor);
        return visitor.methodCount;
    }

    /**
     * AST Visitor to count all methods.
     * Only counts methods directly in the top-level type, NOT in nested/inner classes.
     */
    private static class MethodCountVisitor extends ASTVisitor {
        int methodCount = 0;
        int nestingLevel = 0;

        @Override
        public boolean visit(MethodDeclaration node) {
            // Only count methods at the top level (not in nested classes)
            if (nestingLevel == 1) {
                methodCount++;
            }
            return false; // Don't visit children of method
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1;
        }

        @Override
        public void endVisit(AnnotationTypeDeclaration node) {
            nestingLevel--;
        }
    }
}
