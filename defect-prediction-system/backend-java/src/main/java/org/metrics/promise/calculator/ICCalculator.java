package org.metrics.promise.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for Inheritance Coupling (IC) metric.
 * 
 * IC measures the number of parent classes to which a child class is coupled.
 * A child class is coupled to a parent class if it calls a method defined in the parent class.
 * 
 * IC = Number of ancestor classes whose methods are called by the child class.
 * 
 * This metric is part of the PROMISE dataset metrics suite and helps identify
 * the degree of coupling through the inheritance hierarchy.
 * 
 * A high IC value indicates that a class is heavily dependent on its parent classes'
 * implementations, which may make the class harder to maintain or modify.
 */
public class ICCalculator {

    /**
     * Stores the mapping from a class's fully qualified name to its superclass name.
     * Key: class fully qualified name
     * Value: superclass name (simple or fully qualified)
     */
    private Map<String, String> classToSuperclass = new HashMap<>();

    /**
     * Stores the methods defined in each class.
     * Key: class simple name
     * Value: set of method names defined in that class
     */
    private Map<String, Set<String>> classToMethods = new HashMap<>();

    /**
     * Stores all known class names in the project (simple names for matching).
     */
    private Set<String> allClassSimpleNames = new HashSet<>();

    /**
     * Stores the fully qualified names for reverse lookup.
     */
    private Map<String, String> simpleToFullyQualified = new HashMap<>();

    /**
     * Standard library classes and their known parent classes.
     */
    private static final Map<String, String> STANDARD_LIBRARY_PARENTS = new HashMap<>();

    static {
        // Common standard library inheritance
        STANDARD_LIBRARY_PARENTS.put("Object", null);
        STANDARD_LIBRARY_PARENTS.put("java.lang.Object", null);
        STANDARD_LIBRARY_PARENTS.put("Throwable", "Object");
        STANDARD_LIBRARY_PARENTS.put("Exception", "Throwable");
        STANDARD_LIBRARY_PARENTS.put("RuntimeException", "Exception");
        STANDARD_LIBRARY_PARENTS.put("Error", "Throwable");
        STANDARD_LIBRARY_PARENTS.put("InputStream", "Object");
        STANDARD_LIBRARY_PARENTS.put("OutputStream", "Object");
        STANDARD_LIBRARY_PARENTS.put("FilterInputStream", "InputStream");
        STANDARD_LIBRARY_PARENTS.put("FilterOutputStream", "OutputStream");
        STANDARD_LIBRARY_PARENTS.put("Reader", "Object");
        STANDARD_LIBRARY_PARENTS.put("Writer", "Object");
        STANDARD_LIBRARY_PARENTS.put("Thread", "Object");
    }

    /**
     * Register a class for IC calculation.
     * 
     * @param fullyQualifiedName The fully qualified class name
     * @param superclassName The superclass name (simple or fully qualified)
     * @param methodNames The set of method names defined in this class
     */
    public void registerClass(String fullyQualifiedName, String superclassName, Set<String> methodNames) {
        String simpleName = getSimpleName(fullyQualifiedName);
        allClassSimpleNames.add(simpleName);
        simpleToFullyQualified.put(simpleName, fullyQualifiedName);

        if (superclassName != null && !superclassName.isEmpty()) {
            classToSuperclass.put(simpleName, getSimpleName(superclassName));
        }

        if (methodNames != null) {
            classToMethods.put(simpleName, new HashSet<>(methodNames));
        } else {
            classToMethods.put(simpleName, new HashSet<>());
        }
    }

    /**
     * Calculate IC for a specific class.
     * IC = number of parent classes whose methods are invoked by the child class.
     * 
     * @param fullyQualifiedName The fully qualified class name
     * @param invokedMethods Set of method names invoked by this class
     * @return IC value (number of parent classes coupled through method calls)
     */
    public int calculateIC(String fullyQualifiedName, Set<String> invokedMethods) {
        if (invokedMethods == null || invokedMethods.isEmpty()) {
            return 0;
        }

        String simpleName = getSimpleName(fullyQualifiedName);
        Set<String> coupledParents = new HashSet<>();

        // Get the inheritance chain (ancestors)
        List<String> ancestors = getAncestorChain(simpleName);

        // For each ancestor, check if any of its methods are invoked
        for (String ancestor : ancestors) {
            Set<String> ancestorMethods = getMethodsForClass(ancestor);
            
            // Check if any invoked method exists in this ancestor
            for (String invokedMethod : invokedMethods) {
                if (ancestorMethods.contains(invokedMethod)) {
                    coupledParents.add(ancestor);
                    break; // Count each parent only once
                }
            }
        }

        return coupledParents.size();
    }

    /**
     * Get the chain of ancestors for a class (excluding java.lang.Object).
     * 
     * @param simpleName The simple name of the class
     * @return List of ancestor class names (from immediate parent to root)
     */
    private List<String> getAncestorChain(String simpleName) {
        List<String> ancestors = new ArrayList<>();
        String current = simpleName;
        Set<String> visited = new HashSet<>(); // Prevent infinite loops

        int maxDepth = 20; // Safety limit
        int depth = 0;

        while (current != null && depth < maxDepth) {
            String parent = classToSuperclass.get(current);
            
            // Check standard library if not found in project
            if (parent == null) {
                parent = STANDARD_LIBRARY_PARENTS.get(current);
            }

            if (parent == null || "Object".equals(parent) || "java.lang.Object".equals(parent)) {
                break;
            }

            if (visited.contains(parent)) {
                break; // Cycle detected
            }

            visited.add(parent);
            ancestors.add(parent);
            current = parent;
            depth++;
        }

        return ancestors;
    }

    /**
     * Get methods defined in a class.
     * 
     * @param className The simple class name
     * @return Set of method names, or empty set if unknown
     */
    private Set<String> getMethodsForClass(String className) {
        Set<String> methods = classToMethods.get(className);
        if (methods != null) {
            return methods;
        }
        return Collections.emptySet();
    }

    /**
     * Extract the simple class name from a fully qualified name.
     */
    private static String getSimpleName(String fullName) {
        if (fullName == null) return "";
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    /**
     * Extract method names defined in a type declaration.
     * 
     * @param typeDeclaration The type declaration to analyze
     * @return Set of method names defined in this type
     */
    public static Set<String> extractMethodNames(AbstractTypeDeclaration typeDeclaration) {
        Set<String> methodNames = new HashSet<>();

        if (typeDeclaration instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) typeDeclaration;
            for (MethodDeclaration method : td.getMethods()) {
                methodNames.add(method.getName().getIdentifier());
            }
        } else if (typeDeclaration instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) typeDeclaration;
            @SuppressWarnings("unchecked")
            List<BodyDeclaration> bodyDeclarations = ed.bodyDeclarations();
            for (BodyDeclaration bd : bodyDeclarations) {
                if (bd instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) bd;
                    methodNames.add(method.getName().getIdentifier());
                }
            }
        }

        return methodNames;
    }

    /**
     * Extract method invocations from a type declaration.
     * Returns the set of method names that are invoked within the class.
     * 
     * @param typeDeclaration The type declaration to analyze
     * @return Set of invoked method names
     */
    public static Set<String> extractInvokedMethods(AbstractTypeDeclaration typeDeclaration) {
        Set<String> invokedMethods = new HashSet<>();

        typeDeclaration.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                // Get the method name being invoked
                String methodName = node.getName().getIdentifier();
                
                // Check if this is a call on 'this' or 'super' or unqualified (implicit this/super)
                Expression expr = node.getExpression();
                if (expr == null) {
                    // Unqualified call - could be this.method() or super.method() or inherited
                    invokedMethods.add(methodName);
                } else if (expr instanceof ThisExpression) {
                    invokedMethods.add(methodName);
                } else if (expr instanceof SuperMethodInvocation) {
                    // This won't happen here, SuperMethodInvocation is separate
                }
                
                return true;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                // Explicit super.method() call - definitely from parent
                String methodName = node.getName().getIdentifier();
                invokedMethods.add(methodName);
                return true;
            }
        });

        return invokedMethods;
    }

    /**
     * Calculate IC directly from a type declaration.
     * This is a convenience method that extracts invoked methods and calculates IC.
     * 
     * @param typeDeclaration The type to analyze
     * @param fullyQualifiedName The fully qualified name of the class
     * @return IC value
     */
    public int calculateICForType(AbstractTypeDeclaration typeDeclaration, String fullyQualifiedName) {
        Set<String> invokedMethods = extractInvokedMethods(typeDeclaration);
        return calculateIC(fullyQualifiedName, invokedMethods);
    }

    /**
     * Get all registered class names.
     */
    public Set<String> getAllClassNames() {
        return new HashSet<>(allClassSimpleNames);
    }

    /**
     * Check if a class is registered.
     */
    public boolean isClassRegistered(String className) {
        return allClassSimpleNames.contains(getSimpleName(className));
    }
}
