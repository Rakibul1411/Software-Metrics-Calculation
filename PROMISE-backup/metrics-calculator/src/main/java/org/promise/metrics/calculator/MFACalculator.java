package org.promise.metrics.calculator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Calculator for MFA (Measure of Functional Abstraction).
 *
 * MFA measures the ratio of inherited methods to total methods available to a class.
 *
 * Formula:
 *   MFA = number of inherited (non-overridden) methods / total number of methods available
 *   total methods available = inherited methods + locally declared methods
 *
 * This requires a two-pass approach (similar to NOC, DIT, IC):
 *   1. Register all classes with their superclass and method names
 *   2. Calculate MFA for each class by walking the inheritance chain
 *
 * Special cases:
 *   - If a class has no methods available (total = 0), MFA = 0
 *   - If a class has no parent (or parent is Object), inherited methods = 0
 *
 * MFA ranges from 0.0 to 1.0:
 *   - 0.0 = all methods are locally defined (no inheritance)
 *   - 1.0 = all methods are inherited (no locally defined methods)
 */
public class MFACalculator {

    // Map of class name -> superclass name
    private final Map<String, String> superclassMap = new HashMap<>();

    // Map of class name -> set of method names defined in the class
    private final Map<String, Set<String>> classMethodsMap = new HashMap<>();

    /**
     * Register a class with its superclass and declared methods.
     *
     * @param className     Fully qualified class name
     * @param superclassName Fully qualified superclass name (null if none/Object)
     * @param methodNames   Set of method names declared in this class
     */
    public void registerClass(String className, String superclassName, Set<String> methodNames) {
        superclassMap.put(className, superclassName);
        classMethodsMap.put(className, methodNames != null ? new HashSet<>(methodNames) : new HashSet<>());
    }

    /**
     * Calculate MFA for a specific class.
     *
     * @param className Fully qualified class name
     * @return MFA value (ratio of inherited methods to total methods)
     */
    public double calculateMFA(String className) {
        Set<String> localMethods = classMethodsMap.getOrDefault(className, new HashSet<>());

        // Collect all inherited methods (from parent chain) that are not overridden
        Set<String> inheritedMethods = collectInheritedMethods(className);

        // Remove methods that are overridden locally
        Set<String> nonOverriddenInherited = new HashSet<>(inheritedMethods);
        nonOverriddenInherited.removeAll(localMethods);

        int totalMethods = localMethods.size() + nonOverriddenInherited.size();

        if (totalMethods == 0) {
            return 0.0;
        }

        return (double) nonOverriddenInherited.size() / totalMethods;
    }

    /**
     * Standard java.lang.Object methods that all classes inherit.
     * These are included as inherited methods for MFA calculation (matching ckjm behavior).
     */
    private static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList(
        "toString", "hashCode", "equals", "getClass",
        "notify", "notifyAll", "wait", "clone", "finalize"
    ));

    /**
     * Collect all inherited methods by walking up the inheritance chain.
     * Includes java.lang.Object methods as inherited for all classes (matching ckjm).
     *
     * @param className The class to collect inherited methods for
     * @return Set of inherited method names
     */
    private Set<String> collectInheritedMethods(String className) {
        Set<String> inherited = new HashSet<>();
        Set<String> visited = new HashSet<>();
        visited.add(className);

        // Always include java.lang.Object methods as inherited
        inherited.addAll(OBJECT_METHODS);

        String current = superclassMap.get(className);

        while (current != null && !visited.contains(current)) {
            visited.add(current);

            Set<String> parentMethods = classMethodsMap.get(current);
            if (parentMethods != null) {
                inherited.addAll(parentMethods);
            }

            // Move up the chain
            current = superclassMap.get(current);
        }

        return inherited;
    }
}
