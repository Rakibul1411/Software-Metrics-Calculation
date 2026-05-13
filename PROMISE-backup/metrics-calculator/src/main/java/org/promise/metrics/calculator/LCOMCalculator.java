package org.promise.metrics.calculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Calculator for LCOM (Lack of Cohesion of Methods) - Chidamber & Kemerer definition.
 *
 * LCOM measures class cohesion by looking at method pairs and shared instance variables.
 *
 * Algorithm:
 *   Consider all pairs of methods (mi, mj).
 *   Let P = number of pairs that do NOT share any instance variable.
 *   Let Q = number of pairs that DO share at least one instance variable.
 *   LCOM = max(0, |P| - |Q|)
 *
 * If there are fewer than 2 methods, LCOM = 0.
 */
public class LCOMCalculator {

    /**
     * Calculate LCOM for a specific type declaration.
     *
     * @param typeDeclaration The type to analyze
     * @return LCOM value (Chidamber & Kemerer)
     */
    public static int calculateLCOMForType(AbstractTypeDeclaration typeDeclaration) {
        // Step 1: Collect all instance variable names declared in the class
        Set<String> instanceVariables = extractInstanceVariables(typeDeclaration);

        // Step 2: For each method, find which instance variables it accesses
        Map<String, Set<String>> methodFieldAccess = extractMethodFieldAccess(typeDeclaration, instanceVariables);

        // Step 2.5: Add implicit default constructor (accesses no fields) if applicable
        if (!hasExplicitConstructor(typeDeclaration) && !WMCCalculator.isInterfaceType(typeDeclaration)) {
            methodFieldAccess.put("<init>#default", new HashSet<>());
        }

        List<String> methodNames = new ArrayList<>(methodFieldAccess.keySet());
        int methodCount = methodNames.size();

        if (methodCount < 2) {
            return 0;
        }

        // Step 3: Count pairs
        int pCount = 0; // pairs that do NOT share any instance variable
        int qCount = 0; // pairs that DO share at least one instance variable

        for (int i = 0; i < methodCount; i++) {
            for (int j = i + 1; j < methodCount; j++) {
                Set<String> fieldsI = methodFieldAccess.get(methodNames.get(i));
                Set<String> fieldsJ = methodFieldAccess.get(methodNames.get(j));

                // Check intersection
                boolean shared = false;
                for (String field : fieldsI) {
                    if (fieldsJ.contains(field)) {
                        shared = true;
                        break;
                    }
                }

                if (shared) {
                    qCount++;
                } else {
                    pCount++;
                }
            }
        }

        return Math.max(0, pCount - qCount);
    }

    /**
     * Check if a type has an explicit constructor.
     */
    private static boolean hasExplicitConstructor(AbstractTypeDeclaration typeDeclaration) {
        ConstructorCheckVisitor visitor = new ConstructorCheckVisitor();
        typeDeclaration.accept(visitor);
        return visitor.hasConstructor;
    }

    private static class ConstructorCheckVisitor extends ASTVisitor {
        boolean hasConstructor = false;
        int nestingLevel = 0;

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
        public boolean visit(AnonymousClassDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (nestingLevel == 1 && node.isConstructor()) {
                hasConstructor = true;
            }
            return false;
        }
    }

    /**
     * Extract all instance variable (field) names from a type declaration.
     * Only considers fields directly in the top-level type, not nested classes.
     */
    public static Set<String> extractInstanceVariables(AbstractTypeDeclaration typeDeclaration) {
        Set<String> variables = new HashSet<>();
        FieldCollectorVisitor visitor = new FieldCollectorVisitor(variables);
        typeDeclaration.accept(visitor);
        return variables;
    }

    /**
     * For each method in the type, extract the set of instance variables it accesses.
     * Returns a map of methodIdentifier -> set of accessed field names.
     */
    public static Map<String, Set<String>> extractMethodFieldAccess(
            AbstractTypeDeclaration typeDeclaration, Set<String> instanceVariables) {
        Map<String, Set<String>> methodFieldAccess = new HashMap<>();
        MethodFieldAccessVisitor visitor = new MethodFieldAccessVisitor(instanceVariables, methodFieldAccess);
        typeDeclaration.accept(visitor);
        return methodFieldAccess;
    }

    /**
     * Visitor to collect field names from the top-level type only.
     */
    private static class FieldCollectorVisitor extends ASTVisitor {
        private final Set<String> fieldNames;
        private int nestingLevel = 0;

        FieldCollectorVisitor(Set<String> fieldNames) {
            this.fieldNames = fieldNames;
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
        public boolean visit(AnonymousClassDeclaration node) {
            nestingLevel++;
            return false;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            nestingLevel--;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean visit(FieldDeclaration node) {
            if (nestingLevel == 1) {
                // Exclude static fields - LCOM only considers instance variables
                if (!org.eclipse.jdt.core.dom.Modifier.isStatic(node.getModifiers())) {
                    List<VariableDeclarationFragment> fragments = node.fragments();
                    for (VariableDeclarationFragment fragment : fragments) {
                        fieldNames.add(fragment.getName().getIdentifier());
                    }
                }
            }
            return false;
        }
    }

    /**
     * Visitor to collect which instance variables each method accesses.
     * Only considers top-level methods (not methods in nested/inner classes).
     */
    private static class MethodFieldAccessVisitor extends ASTVisitor {
        private final Set<String> instanceVariables;
        private final Map<String, Set<String>> methodFieldAccess;
        private int nestingLevel = 0;
        private int methodIndex = 0;

        MethodFieldAccessVisitor(Set<String> instanceVariables, Map<String, Set<String>> methodFieldAccess) {
            this.instanceVariables = instanceVariables;
            this.methodFieldAccess = methodFieldAccess;
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
        public boolean visit(AnonymousClassDeclaration node) {
            // Don't visit anonymous class methods
            return false;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (nestingLevel == 1) {
                // Use unique key for each method (name + index to handle overloads)
                String methodKey = node.getName().getIdentifier() + "#" + (methodIndex++);
                Set<String> accessedFields = new HashSet<>();

                // Visit the method body to find field accesses
                if (node.getBody() != null) {
                    node.getBody().accept(new ASTVisitor() {
                        @Override
                        public boolean visit(SimpleName name) {
                            String identifier = name.getIdentifier();
                            if (instanceVariables.contains(identifier)) {
                                accessedFields.add(identifier);
                            }
                            return true;
                        }

                        @Override
                        public boolean visit(AnonymousClassDeclaration anonNode) {
                            // Don't descend into anonymous classes
                            return false;
                        }
                    });
                }

                methodFieldAccess.put(methodKey, accessedFields);
                return false; // Don't visit children again
            }
            return false;
        }
    }
}
