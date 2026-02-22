package org.promise.metrics.calculator;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for Response For a Class (RFC).
 * 
 * RFC is the count of the set of all methods that can be invoked in response
 * to a message to an object of the class. This includes:
 * - All methods in the class (M)
 * - All distinct methods called by the methods of the class (R)
 * 
 * RFC = M + R (where R is the count of distinct method names called)
 * 
 * According to the Chidamber and Kemerer (CK) metrics definition:
 * RFC = |RS| where RS (Response Set) = {M} ∪ {all methods called by M}
 * 
 * The PROMISE dataset counts distinct method NAMES only (not distinguishing
 * by receiver object), so "obj1.foo()" and "obj2.foo()" count as one method "foo".
 */
public class RFCCalculator {

    /**
     * Calculate RFC for a compilation unit (entire file).
     * Returns the RFC for all top-level types combined.
     *
     * @param compilationUnit The parsed Java file
     * @return Total RFC for all types in the file
     */
    public static int calculateRFC(CompilationUnit compilationUnit) {
        RFCVisitor visitor = new RFCVisitor();
        compilationUnit.accept(visitor);
        return visitor.getRFC();
    }

    /**
     * Calculate RFC for a specific type declaration.
     * Counts all methods directly declared in the type plus
     * all distinct method names called from within those methods.
     * Includes implicit default constructor if no explicit constructor exists.
     *
     * @param typeDeclaration The type to analyze
     * @return RFC value for the type
     */
    public static int calculateRFCForType(AbstractTypeDeclaration typeDeclaration) {
        RFCVisitor visitor = new RFCVisitor();
        typeDeclaration.accept(visitor);
        int rfc = visitor.getRFC();

        // Add implicit default constructor to own methods if none was explicitly declared
        if (!visitor.hasExplicitConstructor && !WMCCalculator.isInterfaceType(typeDeclaration)) {
            rfc++; // Default constructor adds 1 to the response set
        }

        return rfc;
    }

    /**
     * AST Visitor to calculate RFC.
     * Counts:
     * 1. Methods declared in the class (M)
     * 2. Distinct method names called by those methods (R)
     * 
     * Only counts methods/invocations directly in the top-level type,
     * NOT in nested/inner classes.
     */
    private static class RFCVisitor extends ASTVisitor {
        private Set<String> ownMethods = new HashSet<>();
        private Set<String> calledMethods = new HashSet<>();
        private int nestingLevel = 0;
        private int innerClassNesting = 0;
        boolean hasExplicitConstructor = false;

        /**
         * Get the RFC value.
         * RFC = count of own methods + count of distinct called methods (excluding own methods)
         * @return RFC value
         */
        public int getRFC() {
            // Create a copy of called methods and remove any that are own methods
            // to avoid double counting
            Set<String> externalCalls = new HashSet<>(calledMethods);
            externalCalls.removeAll(ownMethods);
            
            return ownMethods.size() + externalCalls.size();
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            if (nestingLevel > 1) {
                innerClassNesting++;
            }
            return true;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            if (nestingLevel > 1) {
                innerClassNesting--;
            }
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            if (nestingLevel > 1) {
                innerClassNesting++;
            }
            return true;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            if (nestingLevel > 1) {
                innerClassNesting--;
            }
            nestingLevel--;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            innerClassNesting++;
            return true;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            innerClassNesting--;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            // Only count methods at the top level (not in inner/anonymous classes)
            if (nestingLevel == 1 && innerClassNesting == 0) {
                String methodName = node.getName().getIdentifier();
                ownMethods.add(methodName);
                if (node.isConstructor()) {
                    hasExplicitConstructor = true;
                }
            }
            return true; // Visit method body to find method invocations
        }

        @Override
        public boolean visit(MethodInvocation node) {
            // Only count method calls from the top-level class (not from inner classes)
            if (innerClassNesting == 0) {
                // Use just the method name for distinct counting
                String methodName = node.getName().getIdentifier();
                calledMethods.add(methodName);
            }
            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            if (innerClassNesting == 0) {
                String methodName = node.getName().getIdentifier();
                calledMethods.add(methodName);
            }
            return true;
        }

        @Override
        public boolean visit(ConstructorInvocation node) {
            // this() calls - count as a special method
            if (innerClassNesting == 0) {
                calledMethods.add("<init>");
            }
            return true;
        }

        @Override
        public boolean visit(SuperConstructorInvocation node) {
            // super() calls - count as a special method
            if (innerClassNesting == 0) {
                calledMethods.add("<super-init>");
            }
            return true;
        }

        @Override
        public boolean visit(ClassInstanceCreation node) {
            // new ClassName() - count constructor calls by type name
            if (innerClassNesting == 0) {
                String typeName = node.getType().toString();
                // Extract simple type name if it's qualified
                int lastDot = typeName.lastIndexOf('.');
                if (lastDot >= 0) {
                    typeName = typeName.substring(lastDot + 1);
                }
                // Remove any generic type parameters
                int genericStart = typeName.indexOf('<');
                if (genericStart >= 0) {
                    typeName = typeName.substring(0, genericStart);
                }
                calledMethods.add("new:" + typeName);
            }
            return true;
        }
    }
}
