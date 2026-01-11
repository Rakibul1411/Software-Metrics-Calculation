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
     */
    private static class NPMVisitor extends ASTVisitor {
        int publicMethodCount = 0;

        @Override
        public boolean visit(MethodDeclaration node) {
            int modifiers = node.getModifiers();

            // Check if method is public
            if (Modifier.isPublic(modifiers)) {
                publicMethodCount++;
            }

            // Continue visiting nested types
            return false;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            // Visit methods in this type
            return true;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            // Visit methods in enum
            return true;
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            // Visit methods in annotation
            return true;
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
     */
    private static class MethodCountVisitor extends ASTVisitor {
        int methodCount = 0;

        @Override
        public boolean visit(MethodDeclaration node) {
            methodCount++;
            return false; // Don't visit nested types
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            return true;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            return true;
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            return true;
        }
    }
}
