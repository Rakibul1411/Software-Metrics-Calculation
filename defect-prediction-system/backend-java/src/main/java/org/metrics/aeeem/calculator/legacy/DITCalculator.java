package org.metrics.aeeem.calculator.legacy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for Depth of Inheritance Tree (DIT) metric.
 * DIT measures the maximum length from a class to the root of the inheritance tree.
 * 
 * DIT is part of the Chidamber and Kemerer (CK) metrics suite.
 * A high DIT indicates greater design complexity but potentially more reuse.
 * 
 * For Java:
 * - java.lang.Object has DIT = 0 (or is not counted as it's the root)
 * - A class that directly extends Object has DIT = 1
 * - Interfaces are typically assigned DIT = 0 or 1 depending on convention
 */
public class DITCalculator {

    /**
     * Stores the mapping from a class's fully qualified name to its superclass name.
     * Key: class fully qualified name
     * Value: superclass fully qualified name (or simple name if not resolvable)
     */
    private Map<String, String> classToSuperclass = new HashMap<>();

    /**
     * Stores all known class names in the project.
     */
    private Set<String> allClassNames = new HashSet<>();

    /**
     * Cache for calculated DIT values to avoid repeated calculations.
     */
    private Map<String, Integer> ditCache = new HashMap<>();

    /**
     * Standard Java library classes and their known DIT values.
     * This helps calculate DIT when classes extend standard library classes.
     */
    private static final Map<String, Integer> STANDARD_LIBRARY_DIT = new HashMap<>();

    static {
        // java.lang.Object is the root of all Java classes
        // PROMISE dataset uses DIT=1 for classes extending Object directly
        STANDARD_LIBRARY_DIT.put("java.lang.Object", 0);
        STANDARD_LIBRARY_DIT.put("Object", 0);
        
        // Common classes that extend Object directly (DIT = 1)
        STANDARD_LIBRARY_DIT.put("java.lang.Throwable", 1);
        STANDARD_LIBRARY_DIT.put("Throwable", 1);
        STANDARD_LIBRARY_DIT.put("java.lang.Number", 1);
        STANDARD_LIBRARY_DIT.put("Number", 1);
        STANDARD_LIBRARY_DIT.put("java.lang.String", 1);
        STANDARD_LIBRARY_DIT.put("String", 1);
        STANDARD_LIBRARY_DIT.put("java.lang.Thread", 1);
        STANDARD_LIBRARY_DIT.put("Thread", 1);
        STANDARD_LIBRARY_DIT.put("java.util.EventObject", 1);
        STANDARD_LIBRARY_DIT.put("EventObject", 1);
        
        // IO Stream hierarchy
        STANDARD_LIBRARY_DIT.put("java.io.InputStream", 1);
        STANDARD_LIBRARY_DIT.put("InputStream", 1);
        STANDARD_LIBRARY_DIT.put("java.io.OutputStream", 1);
        STANDARD_LIBRARY_DIT.put("OutputStream", 1);
        STANDARD_LIBRARY_DIT.put("java.io.Reader", 1);
        STANDARD_LIBRARY_DIT.put("Reader", 1);
        STANDARD_LIBRARY_DIT.put("java.io.Writer", 1);
        STANDARD_LIBRARY_DIT.put("Writer", 1);
        
        // FilterInputStream/FilterOutputStream extend InputStream/OutputStream (DIT = 2)
        STANDARD_LIBRARY_DIT.put("java.io.FilterInputStream", 2);
        STANDARD_LIBRARY_DIT.put("FilterInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.FilterOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("FilterOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.BufferedInputStream", 2);
        STANDARD_LIBRARY_DIT.put("BufferedInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.BufferedOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("BufferedOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.DataInputStream", 2);
        STANDARD_LIBRARY_DIT.put("DataInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.DataOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("DataOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.ByteArrayInputStream", 2);
        STANDARD_LIBRARY_DIT.put("ByteArrayInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.ByteArrayOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("ByteArrayOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.FileInputStream", 2);
        STANDARD_LIBRARY_DIT.put("FileInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.FileOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("FileOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.ObjectInputStream", 2);
        STANDARD_LIBRARY_DIT.put("ObjectInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.ObjectOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("ObjectOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.PipedInputStream", 2);
        STANDARD_LIBRARY_DIT.put("PipedInputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.PipedOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("PipedOutputStream", 2);
        STANDARD_LIBRARY_DIT.put("java.io.BufferedReader", 2);
        STANDARD_LIBRARY_DIT.put("BufferedReader", 2);
        STANDARD_LIBRARY_DIT.put("java.io.BufferedWriter", 2);
        STANDARD_LIBRARY_DIT.put("BufferedWriter", 2);
        STANDARD_LIBRARY_DIT.put("java.io.InputStreamReader", 2);
        STANDARD_LIBRARY_DIT.put("InputStreamReader", 2);
        STANDARD_LIBRARY_DIT.put("java.io.OutputStreamWriter", 2);
        STANDARD_LIBRARY_DIT.put("OutputStreamWriter", 2);
        STANDARD_LIBRARY_DIT.put("java.io.FilterReader", 2);
        STANDARD_LIBRARY_DIT.put("FilterReader", 2);
        STANDARD_LIBRARY_DIT.put("java.io.FilterWriter", 2);
        STANDARD_LIBRARY_DIT.put("FilterWriter", 2);
        STANDARD_LIBRARY_DIT.put("java.io.StringReader", 2);
        STANDARD_LIBRARY_DIT.put("StringReader", 2);
        STANDARD_LIBRARY_DIT.put("java.io.StringWriter", 2);
        STANDARD_LIBRARY_DIT.put("StringWriter", 2);
        STANDARD_LIBRARY_DIT.put("java.io.CharArrayReader", 2);
        STANDARD_LIBRARY_DIT.put("CharArrayReader", 2);
        STANDARD_LIBRARY_DIT.put("java.io.CharArrayWriter", 2);
        STANDARD_LIBRARY_DIT.put("CharArrayWriter", 2);
        STANDARD_LIBRARY_DIT.put("java.io.FileReader", 3);
        STANDARD_LIBRARY_DIT.put("FileReader", 3);
        STANDARD_LIBRARY_DIT.put("java.io.FileWriter", 3);
        STANDARD_LIBRARY_DIT.put("FileWriter", 3);
        
        // PrintStream extends FilterOutputStream (DIT = 3)
        STANDARD_LIBRARY_DIT.put("java.io.PrintStream", 3);
        STANDARD_LIBRARY_DIT.put("PrintStream", 3);
        STANDARD_LIBRARY_DIT.put("java.io.PrintWriter", 2);
        STANDARD_LIBRARY_DIT.put("PrintWriter", 2);
        
        // Collection hierarchy
        STANDARD_LIBRARY_DIT.put("java.util.AbstractCollection", 1);
        STANDARD_LIBRARY_DIT.put("AbstractCollection", 1);
        STANDARD_LIBRARY_DIT.put("java.util.AbstractMap", 1);
        STANDARD_LIBRARY_DIT.put("AbstractMap", 1);
        STANDARD_LIBRARY_DIT.put("java.util.AbstractList", 2);
        STANDARD_LIBRARY_DIT.put("AbstractList", 2);
        STANDARD_LIBRARY_DIT.put("java.util.AbstractSet", 2);
        STANDARD_LIBRARY_DIT.put("AbstractSet", 2);
        STANDARD_LIBRARY_DIT.put("java.util.AbstractSequentialList", 3);
        STANDARD_LIBRARY_DIT.put("AbstractSequentialList", 3);
        STANDARD_LIBRARY_DIT.put("java.util.ArrayList", 3);
        STANDARD_LIBRARY_DIT.put("ArrayList", 3);
        STANDARD_LIBRARY_DIT.put("java.util.Vector", 3);
        STANDARD_LIBRARY_DIT.put("Vector", 3);
        STANDARD_LIBRARY_DIT.put("java.util.Stack", 4);
        STANDARD_LIBRARY_DIT.put("Stack", 4);
        STANDARD_LIBRARY_DIT.put("java.util.LinkedList", 4);
        STANDARD_LIBRARY_DIT.put("LinkedList", 4);
        STANDARD_LIBRARY_DIT.put("java.util.HashSet", 3);
        STANDARD_LIBRARY_DIT.put("HashSet", 3);
        STANDARD_LIBRARY_DIT.put("java.util.TreeSet", 3);
        STANDARD_LIBRARY_DIT.put("TreeSet", 3);
        STANDARD_LIBRARY_DIT.put("java.util.HashMap", 2);
        STANDARD_LIBRARY_DIT.put("HashMap", 2);
        STANDARD_LIBRARY_DIT.put("java.util.TreeMap", 2);
        STANDARD_LIBRARY_DIT.put("TreeMap", 2);
        STANDARD_LIBRARY_DIT.put("java.util.Hashtable", 2);
        STANDARD_LIBRARY_DIT.put("Hashtable", 2);
        STANDARD_LIBRARY_DIT.put("java.util.Properties", 3);
        STANDARD_LIBRARY_DIT.put("Properties", 3);
        STANDARD_LIBRARY_DIT.put("java.util.Dictionary", 1);
        STANDARD_LIBRARY_DIT.put("Dictionary", 1);
        
        // Exception hierarchy
        STANDARD_LIBRARY_DIT.put("java.lang.Exception", 2);
        STANDARD_LIBRARY_DIT.put("Exception", 2);
        STANDARD_LIBRARY_DIT.put("java.lang.Error", 2);
        STANDARD_LIBRARY_DIT.put("Error", 2);
        STANDARD_LIBRARY_DIT.put("java.lang.RuntimeException", 3);
        STANDARD_LIBRARY_DIT.put("RuntimeException", 3);
        STANDARD_LIBRARY_DIT.put("java.io.IOException", 3);
        STANDARD_LIBRARY_DIT.put("IOException", 3);
        STANDARD_LIBRARY_DIT.put("java.lang.IllegalArgumentException", 4);
        STANDARD_LIBRARY_DIT.put("IllegalArgumentException", 4);
        STANDARD_LIBRARY_DIT.put("java.lang.IllegalStateException", 4);
        STANDARD_LIBRARY_DIT.put("IllegalStateException", 4);
        STANDARD_LIBRARY_DIT.put("java.lang.NullPointerException", 4);
        STANDARD_LIBRARY_DIT.put("NullPointerException", 4);
        STANDARD_LIBRARY_DIT.put("java.lang.IndexOutOfBoundsException", 4);
        STANDARD_LIBRARY_DIT.put("IndexOutOfBoundsException", 4);
        STANDARD_LIBRARY_DIT.put("java.lang.NumberFormatException", 5);
        STANDARD_LIBRARY_DIT.put("NumberFormatException", 5);
        STANDARD_LIBRARY_DIT.put("java.lang.ArrayIndexOutOfBoundsException", 5);
        STANDARD_LIBRARY_DIT.put("ArrayIndexOutOfBoundsException", 5);
        STANDARD_LIBRARY_DIT.put("java.lang.StringIndexOutOfBoundsException", 5);
        STANDARD_LIBRARY_DIT.put("StringIndexOutOfBoundsException", 5);
        STANDARD_LIBRARY_DIT.put("java.lang.ClassNotFoundException", 3);
        STANDARD_LIBRARY_DIT.put("ClassNotFoundException", 3);
        STANDARD_LIBRARY_DIT.put("java.lang.NoSuchMethodException", 3);
        STANDARD_LIBRARY_DIT.put("NoSuchMethodException", 3);
        STANDARD_LIBRARY_DIT.put("java.lang.InterruptedException", 3);
        STANDARD_LIBRARY_DIT.put("InterruptedException", 3);
        STANDARD_LIBRARY_DIT.put("java.lang.CloneNotSupportedException", 3);
        STANDARD_LIBRARY_DIT.put("CloneNotSupportedException", 3);
        STANDARD_LIBRARY_DIT.put("java.io.FileNotFoundException", 4);
        STANDARD_LIBRARY_DIT.put("FileNotFoundException", 4);
        STANDARD_LIBRARY_DIT.put("java.io.EOFException", 4);
        STANDARD_LIBRARY_DIT.put("EOFException", 4);
        STANDARD_LIBRARY_DIT.put("java.net.MalformedURLException", 4);
        STANDARD_LIBRARY_DIT.put("MalformedURLException", 4);
        STANDARD_LIBRARY_DIT.put("java.net.SocketException", 4);
        STANDARD_LIBRARY_DIT.put("SocketException", 4);
        STANDARD_LIBRARY_DIT.put("java.sql.SQLException", 3);
        STANDARD_LIBRARY_DIT.put("SQLException", 3);
        STANDARD_LIBRARY_DIT.put("org.xml.sax.SAXException", 3);
        STANDARD_LIBRARY_DIT.put("SAXException", 3);
        STANDARD_LIBRARY_DIT.put("org.xml.sax.SAXParseException", 4);
        STANDARD_LIBRARY_DIT.put("SAXParseException", 4);
        
        // AWT/Swing hierarchy
        STANDARD_LIBRARY_DIT.put("java.awt.Component", 1);
        STANDARD_LIBRARY_DIT.put("Component", 1);
        STANDARD_LIBRARY_DIT.put("java.awt.Container", 2);
        STANDARD_LIBRARY_DIT.put("Container", 2);
        STANDARD_LIBRARY_DIT.put("java.awt.Panel", 3);
        STANDARD_LIBRARY_DIT.put("Panel", 3);
        STANDARD_LIBRARY_DIT.put("java.applet.Applet", 4);
        STANDARD_LIBRARY_DIT.put("Applet", 4);
        STANDARD_LIBRARY_DIT.put("java.awt.Window", 3);
        STANDARD_LIBRARY_DIT.put("Window", 3);
        STANDARD_LIBRARY_DIT.put("java.awt.Frame", 4);
        STANDARD_LIBRARY_DIT.put("Frame", 4);
        STANDARD_LIBRARY_DIT.put("java.awt.Dialog", 4);
        STANDARD_LIBRARY_DIT.put("Dialog", 4);
        STANDARD_LIBRARY_DIT.put("javax.swing.JComponent", 3);
        STANDARD_LIBRARY_DIT.put("JComponent", 3);
        STANDARD_LIBRARY_DIT.put("javax.swing.JPanel", 4);
        STANDARD_LIBRARY_DIT.put("JPanel", 4);
        STANDARD_LIBRARY_DIT.put("javax.swing.JFrame", 5);
        STANDARD_LIBRARY_DIT.put("JFrame", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.JDialog", 5);
        STANDARD_LIBRARY_DIT.put("JDialog", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.JApplet", 5);
        STANDARD_LIBRARY_DIT.put("JApplet", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.text.JTextComponent", 4);
        STANDARD_LIBRARY_DIT.put("JTextComponent", 4);
        STANDARD_LIBRARY_DIT.put("javax.swing.JTextField", 5);
        STANDARD_LIBRARY_DIT.put("JTextField", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.JTextArea", 5);
        STANDARD_LIBRARY_DIT.put("JTextArea", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.AbstractButton", 4);
        STANDARD_LIBRARY_DIT.put("AbstractButton", 4);
        STANDARD_LIBRARY_DIT.put("javax.swing.JButton", 5);
        STANDARD_LIBRARY_DIT.put("JButton", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.JToggleButton", 5);
        STANDARD_LIBRARY_DIT.put("JToggleButton", 5);
        STANDARD_LIBRARY_DIT.put("javax.swing.JCheckBox", 6);
        STANDARD_LIBRARY_DIT.put("JCheckBox", 6);
        STANDARD_LIBRARY_DIT.put("javax.swing.JRadioButton", 6);
        STANDARD_LIBRARY_DIT.put("JRadioButton", 6);
        
        // SAX/DOM handlers
        STANDARD_LIBRARY_DIT.put("org.xml.sax.helpers.DefaultHandler", 1);
        STANDARD_LIBRARY_DIT.put("DefaultHandler", 1);
        STANDARD_LIBRARY_DIT.put("org.xml.sax.HandlerBase", 1);
        STANDARD_LIBRARY_DIT.put("HandlerBase", 1);
        
        // Networking
        STANDARD_LIBRARY_DIT.put("java.net.URLConnection", 1);
        STANDARD_LIBRARY_DIT.put("URLConnection", 1);
        STANDARD_LIBRARY_DIT.put("java.net.HttpURLConnection", 2);
        STANDARD_LIBRARY_DIT.put("HttpURLConnection", 2);
        
        // Zip/Jar streams
        STANDARD_LIBRARY_DIT.put("java.util.zip.InflaterInputStream", 3);
        STANDARD_LIBRARY_DIT.put("InflaterInputStream", 3);
        STANDARD_LIBRARY_DIT.put("java.util.zip.DeflaterOutputStream", 3);
        STANDARD_LIBRARY_DIT.put("DeflaterOutputStream", 3);
        STANDARD_LIBRARY_DIT.put("java.util.zip.ZipInputStream", 4);
        STANDARD_LIBRARY_DIT.put("ZipInputStream", 4);
        STANDARD_LIBRARY_DIT.put("java.util.zip.ZipOutputStream", 4);
        STANDARD_LIBRARY_DIT.put("ZipOutputStream", 4);
        STANDARD_LIBRARY_DIT.put("java.util.zip.GZIPInputStream", 4);
        STANDARD_LIBRARY_DIT.put("GZIPInputStream", 4);
        STANDARD_LIBRARY_DIT.put("java.util.zip.GZIPOutputStream", 4);
        STANDARD_LIBRARY_DIT.put("GZIPOutputStream", 4);
        STANDARD_LIBRARY_DIT.put("java.util.jar.JarInputStream", 5);
        STANDARD_LIBRARY_DIT.put("JarInputStream", 5);
        STANDARD_LIBRARY_DIT.put("java.util.jar.JarOutputStream", 5);
        STANDARD_LIBRARY_DIT.put("JarOutputStream", 5);
        
        // Zip entries
        STANDARD_LIBRARY_DIT.put("java.util.zip.ZipEntry", 1);
        STANDARD_LIBRARY_DIT.put("ZipEntry", 1);
        STANDARD_LIBRARY_DIT.put("java.util.jar.JarEntry", 2);
        STANDARD_LIBRARY_DIT.put("JarEntry", 2);
        STANDARD_LIBRARY_DIT.put("java.util.zip.ZipFile", 1);
        STANDARD_LIBRARY_DIT.put("ZipFile", 1);
        STANDARD_LIBRARY_DIT.put("java.util.jar.JarFile", 2);
        STANDARD_LIBRARY_DIT.put("JarFile", 2);
    }

    /**
     * Register a class and its superclass for DIT calculation.
     * This should be called during the first pass over all source files.
     *
     * @param fullyQualifiedName The fully qualified name of the class
     * @param superclassName The name of the superclass (can be simple or fully qualified)
     * @param isInterface Whether this type is an interface
     */
    public void registerClass(String fullyQualifiedName, String superclassName, boolean isInterface) {
        allClassNames.add(fullyQualifiedName);
        if (superclassName != null && !superclassName.isEmpty()) {
            classToSuperclass.put(fullyQualifiedName, superclassName);
        } else if (!isInterface) {
            // Classes without explicit superclass extend java.lang.Object
            classToSuperclass.put(fullyQualifiedName, "java.lang.Object");
        }
    }

    /**
     * Register a class and its superclass for DIT calculation.
     * Convenience method that defaults isInterface to false.
     *
     * @param fullyQualifiedName The fully qualified name of the class
     * @param superclassName The name of the superclass (can be simple or fully qualified)
     */
    public void registerClass(String fullyQualifiedName, String superclassName) {
        registerClass(fullyQualifiedName, superclassName, false);
    }

    /**
     * Calculate DIT for a specific class.
     * This should be called after all classes have been registered.
     *
     * @param fullyQualifiedName The fully qualified name of the class to calculate DIT for
     * @return Depth of Inheritance Tree (distance from java.lang.Object)
     */
    public int calculateDIT(String fullyQualifiedName) {
        return calculateDIT(fullyQualifiedName, new HashSet<>());
    }

    /**
     * Calculate DIT recursively with cycle detection.
     *
     * @param fullyQualifiedName The class to calculate DIT for
     * @param visited Set of already visited classes (for cycle detection)
     * @return Depth of Inheritance Tree
     */
    private int calculateDIT(String fullyQualifiedName, Set<String> visited) {
        // Check cache first
        if (ditCache.containsKey(fullyQualifiedName)) {
            return ditCache.get(fullyQualifiedName);
        }

        // Check if it's a known standard library class
        if (STANDARD_LIBRARY_DIT.containsKey(fullyQualifiedName)) {
            return STANDARD_LIBRARY_DIT.get(fullyQualifiedName);
        }

        // Check for cycles
        if (visited.contains(fullyQualifiedName)) {
            return 1; // Break cycle, return minimum value
        }

        visited.add(fullyQualifiedName);

        String superclassName = classToSuperclass.get(fullyQualifiedName);

        // If no superclass recorded, check if class exists in our project
        if (superclassName == null) {
            // If it's a known project class with no superclass, it extends Object implicitly
            if (allClassNames.contains(fullyQualifiedName)) {
                ditCache.put(fullyQualifiedName, 1);
                return 1;
            }
            // Unknown class - assume it extends Object
            ditCache.put(fullyQualifiedName, 1);
            return 1;
        }

        // Check if superclass is java.lang.Object
        if (superclassName.equals("java.lang.Object") || superclassName.equals("Object")) {
            ditCache.put(fullyQualifiedName, 1);
            return 1;
        }

        // Try to find the superclass in our project
        String resolvedSuperclass = resolveClassName(superclassName);

        int parentDIT;
        if (STANDARD_LIBRARY_DIT.containsKey(superclassName)) {
            parentDIT = STANDARD_LIBRARY_DIT.get(superclassName);
        } else if (STANDARD_LIBRARY_DIT.containsKey(resolvedSuperclass)) {
            parentDIT = STANDARD_LIBRARY_DIT.get(resolvedSuperclass);
        } else if (allClassNames.contains(resolvedSuperclass)) {
            // Recursively calculate DIT for parent
            parentDIT = calculateDIT(resolvedSuperclass, visited);
        } else {
            // Unknown superclass - assume it's one level above Object
            parentDIT = 1;
        }

        int dit = parentDIT + 1;
        ditCache.put(fullyQualifiedName, dit);
        return dit;
    }

    /**
     * Resolve a class name to its fully qualified form if it exists in our project.
     *
     * @param className The class name (simple or fully qualified)
     * @return The fully qualified name if found, otherwise the original name
     */
    private String resolveClassName(String className) {
        // If it's already fully qualified and exists, return it
        if (allClassNames.contains(className)) {
            return className;
        }

        // Try to find a matching class by simple name
        for (String fqn : allClassNames) {
            if (fqn.endsWith("." + className) || fqn.equals(className)) {
                return fqn;
            }
            // Handle inner classes
            if (fqn.endsWith("$" + className)) {
                return fqn;
            }
        }

        return className;
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

        // EnumDeclaration implicitly extends java.lang.Enum
        if (typeDeclaration instanceof EnumDeclaration) {
            return "java.lang.Enum";
        }

        // AnnotationTypeDeclaration implicitly extends java.lang.annotation.Annotation
        return null;
    }

    /**
     * Check if a type declaration is an interface.
     *
     * @param typeDeclaration The type declaration to check
     * @return true if it's an interface, false otherwise
     */
    public static boolean isInterface(AbstractTypeDeclaration typeDeclaration) {
        if (typeDeclaration instanceof TypeDeclaration) {
            return ((TypeDeclaration) typeDeclaration).isInterface();
        }
        return typeDeclaration instanceof AnnotationTypeDeclaration;
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
        ditCache.clear();
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

    /**
     * Get the DIT cache for debugging.
     *
     * @return Map of class to calculated DIT
     */
    public Map<String, Integer> getDitCache() {
        return Collections.unmodifiableMap(ditCache);
    }
}
