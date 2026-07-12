package org.metrics.aeeem.calculator.legacy;

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
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for Coupling Between Methods (CBM) metric.
 * 
 * CBM measures the total number of new/redefined methods in a class that are
 * coupled to methods in parent classes. It counts the number of distinct 
 * method-to-parent-method invocations.
 * 
 * CBM = Total count of method invocations to parent class methods.
 * 
 * This is different from IC:
 * - IC = number of parent classes coupled to (unique parent count)
 * - CBM = number of method invocations to parent methods (can be > IC)
 * 
 * A high CBM value indicates strong coupling to the parent class implementations.
 */
public class CBMCalculator {

    /**
     * Stores the mapping from a class's simple name to its superclass name.
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
     * Standard library classes and their known parent classes.
     */
    private static final Map<String, String> STANDARD_LIBRARY_PARENTS = new HashMap<>();

    /**
     * Standard library methods - common methods inherited from standard classes.
     */
    private static final Map<String, Set<String>> STANDARD_LIBRARY_METHODS = new HashMap<>();

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
        STANDARD_LIBRARY_PARENTS.put("BufferedInputStream", "FilterInputStream");
        STANDARD_LIBRARY_PARENTS.put("BufferedOutputStream", "FilterOutputStream");
        STANDARD_LIBRARY_PARENTS.put("DataInputStream", "FilterInputStream");
        STANDARD_LIBRARY_PARENTS.put("DataOutputStream", "FilterOutputStream");
        STANDARD_LIBRARY_PARENTS.put("PrintStream", "FilterOutputStream");
        STANDARD_LIBRARY_PARENTS.put("ByteArrayInputStream", "InputStream");
        STANDARD_LIBRARY_PARENTS.put("ByteArrayOutputStream", "OutputStream");
        STANDARD_LIBRARY_PARENTS.put("Reader", "Object");
        STANDARD_LIBRARY_PARENTS.put("Writer", "Object");
        STANDARD_LIBRARY_PARENTS.put("BufferedReader", "Reader");
        STANDARD_LIBRARY_PARENTS.put("BufferedWriter", "Writer");
        STANDARD_LIBRARY_PARENTS.put("InputStreamReader", "Reader");
        STANDARD_LIBRARY_PARENTS.put("OutputStreamWriter", "Writer");
        STANDARD_LIBRARY_PARENTS.put("PrintWriter", "Writer");
        STANDARD_LIBRARY_PARENTS.put("Thread", "Object");

        // Object methods
        Set<String> objectMethods = new HashSet<>();
        objectMethods.add("toString");
        objectMethods.add("equals");
        objectMethods.add("hashCode");
        objectMethods.add("getClass");
        objectMethods.add("clone");
        objectMethods.add("notify");
        objectMethods.add("notifyAll");
        objectMethods.add("wait");
        objectMethods.add("finalize");
        STANDARD_LIBRARY_METHODS.put("Object", objectMethods);

        // InputStream methods
        Set<String> inputStreamMethods = new HashSet<>();
        inputStreamMethods.add("read");
        inputStreamMethods.add("available");
        inputStreamMethods.add("close");
        inputStreamMethods.add("mark");
        inputStreamMethods.add("markSupported");
        inputStreamMethods.add("reset");
        inputStreamMethods.add("skip");
        STANDARD_LIBRARY_METHODS.put("InputStream", inputStreamMethods);

        // OutputStream methods
        Set<String> outputStreamMethods = new HashSet<>();
        outputStreamMethods.add("write");
        outputStreamMethods.add("close");
        outputStreamMethods.add("flush");
        STANDARD_LIBRARY_METHODS.put("OutputStream", outputStreamMethods);

        // FilterInputStream methods (inherits from InputStream)
        Set<String> filterInputStreamMethods = new HashSet<>(inputStreamMethods);
        STANDARD_LIBRARY_METHODS.put("FilterInputStream", filterInputStreamMethods);

        // FilterOutputStream methods (inherits from OutputStream)
        Set<String> filterOutputStreamMethods = new HashSet<>(outputStreamMethods);
        STANDARD_LIBRARY_METHODS.put("FilterOutputStream", filterOutputStreamMethods);

        // Throwable methods
        Set<String> throwableMethods = new HashSet<>();
        throwableMethods.add("getMessage");
        throwableMethods.add("getLocalizedMessage");
        throwableMethods.add("getCause");
        throwableMethods.add("initCause");
        throwableMethods.add("printStackTrace");
        throwableMethods.add("fillInStackTrace");
        throwableMethods.add("getStackTrace");
        throwableMethods.add("setStackTrace");
        STANDARD_LIBRARY_METHODS.put("Throwable", throwableMethods);

        // Exception inherits from Throwable
        STANDARD_LIBRARY_METHODS.put("Exception", new HashSet<>(throwableMethods));
        STANDARD_LIBRARY_METHODS.put("RuntimeException", new HashSet<>(throwableMethods));

        // Thread methods
        Set<String> threadMethods = new HashSet<>();
        threadMethods.add("run");
        threadMethods.add("start");
        threadMethods.add("interrupt");
        threadMethods.add("isInterrupted");
        threadMethods.add("isAlive");
        threadMethods.add("setPriority");
        threadMethods.add("getPriority");
        threadMethods.add("setName");
        threadMethods.add("getName");
        threadMethods.add("getThreadGroup");
        threadMethods.add("join");
        threadMethods.add("sleep");
        threadMethods.add("yield");
        STANDARD_LIBRARY_METHODS.put("Thread", threadMethods);
    }

    /**
     * Register a class for CBM calculation.
     * 
     * @param fullyQualifiedName The fully qualified class name
     * @param superclassName The superclass name (simple or fully qualified)
     * @param methodNames The set of method names defined in this class
     */
    public void registerClass(String fullyQualifiedName, String superclassName, Set<String> methodNames) {
        String simpleName = getSimpleName(fullyQualifiedName);
        allClassSimpleNames.add(simpleName);

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
     * Calculate CBM for a specific class.
     * CBM = total number of method invocations to parent class methods.
     * 
     * @param fullyQualifiedName The fully qualified class name
     * @param parentMethodInvocations Map of method names to invocation counts
     * @return CBM value (total count of parent method invocations)
     */
    public int calculateCBM(String fullyQualifiedName, Map<String, Integer> parentMethodInvocations) {
        if (parentMethodInvocations == null || parentMethodInvocations.isEmpty()) {
            return 0;
        }

        String simpleName = getSimpleName(fullyQualifiedName);
        int cbm = 0;

        // Get the inheritance chain (ancestors)
        List<String> ancestors = getAncestorChain(simpleName);
        
        // Get the set of methods defined in THIS class (to exclude self-calls)
        Set<String> ownMethods = classToMethods.getOrDefault(simpleName, Collections.emptySet());

        // For each method invocation, check if it's defined in an ancestor
        for (Map.Entry<String, Integer> entry : parentMethodInvocations.entrySet()) {
            String methodName = entry.getKey();
            int count = entry.getValue();

            // Skip if this method is defined in the class itself (not inherited)
            if (ownMethods.contains(methodName)) {
                continue;
            }

            // Check if any ancestor defines this method
            for (String ancestor : ancestors) {
                Set<String> ancestorMethods = getMethodsForClass(ancestor);
                if (ancestorMethods.contains(methodName)) {
                    cbm += count;
                    break; // Count once per method (from nearest ancestor)
                }
            }
        }

        return cbm;
    }

    /**
     * Calculate CBM using a simpler approach based on invoked methods set.
     * Counts unique methods that are inherited from parent classes.
     * 
     * @param fullyQualifiedName The fully qualified class name
     * @param invokedMethods Set of method names invoked by this class
     * @return CBM value
     */
    public int calculateCBMSimple(String fullyQualifiedName, Set<String> invokedMethods) {
        if (invokedMethods == null || invokedMethods.isEmpty()) {
            return 0;
        }

        String simpleName = getSimpleName(fullyQualifiedName);
        int cbm = 0;

        // Get the inheritance chain (ancestors)
        List<String> ancestors = getAncestorChain(simpleName);
        
        // Get the set of methods defined in THIS class
        Set<String> ownMethods = classToMethods.getOrDefault(simpleName, Collections.emptySet());

        // For each invoked method, check if it's from a parent
        for (String methodName : invokedMethods) {
            // Skip if this method is defined in the class itself
            if (ownMethods.contains(methodName)) {
                continue;
            }

            // Check if any ancestor defines this method
            for (String ancestor : ancestors) {
                Set<String> ancestorMethods = getMethodsForClass(ancestor);
                if (ancestorMethods.contains(methodName)) {
                    cbm++;
                    break; // Count from nearest ancestor
                }
            }
        }

        return cbm;
    }

    /**
     * Get the chain of ancestors for a class (excluding java.lang.Object).
     */
    private List<String> getAncestorChain(String simpleName) {
        List<String> ancestors = new ArrayList<>();
        String current = simpleName;
        Set<String> visited = new HashSet<>();

        int maxDepth = 20;
        int depth = 0;

        while (current != null && depth < maxDepth) {
            String parent = classToSuperclass.get(current);
            
            if (parent == null) {
                parent = STANDARD_LIBRARY_PARENTS.get(current);
            }

            if (parent == null || "Object".equals(parent) || "java.lang.Object".equals(parent)) {
                break;
            }

            if (visited.contains(parent)) {
                break;
            }

            visited.add(parent);
            ancestors.add(parent);
            current = parent;
            depth++;
        }

        return ancestors;
    }

    /**
     * Get methods defined in a class, including standard library methods.
     */
    private Set<String> getMethodsForClass(String className) {
        // First check project classes
        Set<String> methods = classToMethods.get(className);
        if (methods != null && !methods.isEmpty()) {
            return methods;
        }
        
        // Then check standard library
        Set<String> stdMethods = STANDARD_LIBRARY_METHODS.get(className);
        if (stdMethods != null) {
            return stdMethods;
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
     * Extract parent method invocations from a type declaration.
     * Returns a map of method names to invocation counts.
     * 
     * @param typeDeclaration The type declaration to analyze
     * @return Map of method name to invocation count
     */
    public static Map<String, Integer> extractParentMethodInvocations(AbstractTypeDeclaration typeDeclaration) {
        Map<String, Integer> invocations = new HashMap<>();
        Set<String> ownMethods = extractOwnMethodNames(typeDeclaration);

        typeDeclaration.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                Expression expr = node.getExpression();
                String methodName = node.getName().getIdentifier();
                
                // Unqualified calls or this.method() - potentially parent method
                if (expr == null) {
                    // Only count if not defined in this class
                    if (!ownMethods.contains(methodName)) {
                        invocations.merge(methodName, 1, Integer::sum);
                    }
                }
                return true;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                // Explicit super.method() - definitely parent method
                String methodName = node.getName().getIdentifier();
                invocations.merge(methodName, 1, Integer::sum);
                return true;
            }
        });

        return invocations;
    }

    /**
     * Extract method names defined in a type declaration.
     */
    private static Set<String> extractOwnMethodNames(AbstractTypeDeclaration typeDeclaration) {
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
     * Extract invoked method names (for simpler CBM calculation).
     * Only includes calls that could be inherited methods.
     */
    public static Set<String> extractInheritedMethodInvocations(AbstractTypeDeclaration typeDeclaration) {
        Set<String> invocations = new HashSet<>();
        Set<String> ownMethods = extractOwnMethodNames(typeDeclaration);

        typeDeclaration.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                Expression expr = node.getExpression();
                String methodName = node.getName().getIdentifier();
                
                // Unqualified calls - potentially inherited
                if (expr == null && !ownMethods.contains(methodName)) {
                    invocations.add(methodName);
                }
                return true;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                String methodName = node.getName().getIdentifier();
                invocations.add(methodName);
                return true;
            }
        });

        return invocations;
    }
}
