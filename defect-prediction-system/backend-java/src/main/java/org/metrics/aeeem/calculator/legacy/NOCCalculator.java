package org.metrics.aeeem.calculator.legacy;

import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Calculator for Number of Children (NOC) metric.
 * NOC counts the number of immediate subclasses (direct children) of a class.
 *
 * NOC is part of the Chidamber and Kemerer (CK) metrics suite.
 * A high NOC indicates high reuse through inheritance but may also indicate
 * improper abstraction.
 */
public class NOCCalculator {

    /**
     * Stores the mapping from a classed fully qualified name to its superclass name.
     * Key: class fully qualified name
     * Value: superclass fully qualified name (or simple name if not resolvable)
     */
    private Map<String, String> classToSuperclass = new HashMap<>();

    /**
     * Stores all known class names in the project.
     */
    private Set<String> allClassNames = new HashSet<>();

    /**
     * Register a class and its superclass for NOC calculation.
     * This should be called during the first pass over all source files.
     *
     * @param fullyQualifiedName The fully qualified name of the class
     * @param superclassName The name of the superclass (can be simple or fully qualified)
     */
    public void registerClass(String fullyQualifiedName, String superclassName) {
        allClassNames.add(fullyQualifiedName);
        if (superclassName != null && !superclassName.isEmpty()) {
            classToSuperclass.put(fullyQualifiedName, superclassName);
        }
    }

    /**
     * Calculate NOC for a specific class.
     * This should be called after all classes have been registered.
     *
     * @param fullyQualifiedName The fully qualified name of the class to calculate NOC for
     * @return Number of immediate children (subclasses)
     */
    public int calculateNOC(String fullyQualifiedName) {
        int noc = 0;

        // Count how many classes have this class as their direct superclass
        for (Map.Entry<String, String> entry : classToSuperclass.entrySet()) {
            String childClass = entry.getKey();
            String parentClass = entry.getValue();

            // Check if the parent matches (either fully qualified or simple name)
            if (isParentMatch(fullyQualifiedName, parentClass)) {
                noc++;
            }
        }

        return noc;
    }

    /**
     * Check if a parent class name matches the given fully qualified name.
     * Handles both fully qualified names and simple class names.
     *
     * @param fullyQualifiedName The fully qualified name of the potential parent
     * @param parentName The parent name from the child class (maybe simple or fully qualified)
     * @return true if they match
     */
    private boolean isParentMatch(String fullyQualifiedName, String parentName) {
        if (parentName == null || parentName.isEmpty()) {
            return false;
        }

        // Direct match (fully qualified)
        if (fullyQualifiedName.equals(parentName)) {
            return true;
        }

        // Simple name match (check if fullyQualifiedName ends with the simple name)
        String simpleName = getSimpleName(fullyQualifiedName);
        if (simpleName.equals(parentName)) {
            return true;
        }

        // Check if parentName is a simple name that matches the end of fullyQualifiedName
        if (fullyQualifiedName.endsWith("." + parentName)) {
            return true;
        }

        return false;
    }

    /**
     * Get the simple class name from a fully qualified name.
     *
     * @param fullyQualifiedName The fully qualified name (e.g., "org.example.MyClass")
     * @return The simple name (e.g., "MyClass")
     */
    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        int lastDollar = fullyQualifiedName.lastIndexOf('$');
        int lastSeparator = Math.max(lastDot, lastDollar);

        if (lastSeparator >= 0) {
            return fullyQualifiedName.substring(lastSeparator + 1);
        }
        return fullyQualifiedName;
    }

    /**
     * Extract the superclass name from a type declaration.
     *
     * @param typeDeclaration The type declaration to analyze
     * @return The superclass name, or null if no explicit superclass
     */
    public static String extractSuperclassName(AbstractTypeDeclaration typeDeclaration) {
        if (typeDeclaration instanceof TypeDeclaration) {
            TypeDeclaration classDecl = (TypeDeclaration) typeDeclaration;

            // Interfaces don't have a superclass in the traditional sense
            if (classDecl.isInterface()) {
                return null;
            }

            Type superclassType = classDecl.getSuperclassType();
            if (superclassType != null) {
                return getTypeName(superclassType);
            }
        }

        // EnumDeclaration and AnnotationTypeDeclaration don't have explicit superclasses
        return null;
    }

    /**
     * Get the name from a Type node.
     *
     * @param type The Type node
     * @return The type name as a string
     */
    private static String getTypeName(Type type) {
        if (type == null) {
            return null;
        }

        if (type.isSimpleType()) {
            SimpleType simpleType = (SimpleType) type;
            return simpleType.getName().getFullyQualifiedName();
        } else if (type.isQualifiedType()) {
            QualifiedType qualifiedType = (QualifiedType) type;
            return qualifiedType.getName().getFullyQualifiedName();
        } else if (type.isParameterizedType()) {
            ParameterizedType paramType = (ParameterizedType) type;
            return getTypeName(paramType.getType());
        } else if (type.isNameQualifiedType()) {
            NameQualifiedType nameQualifiedType = (NameQualifiedType) type;
            return nameQualifiedType.getName().getFullyQualifiedName();
        }

        return type.toString();
    }

    /**
     * Reset the calculator for a new project analysis.
     */
    public void reset() {
        classToSuperclass.clear();
        allClassNames.clear();
    }

    /**
     * Get all registered class names.
     *
     * @return Set of all class fully qualified names
     */
    public Set<String> getAllClassNames() {
        return Collections.unmodifiableSet(allClassNames);
    }

    /**
     * Get the superclass map for debugging.
     *
     * @return Map of class to superclass
     */
    public Map<String, String> getClassToSuperclassMap() {
        return Collections.unmodifiableMap(classToSuperclass);
    }
}
