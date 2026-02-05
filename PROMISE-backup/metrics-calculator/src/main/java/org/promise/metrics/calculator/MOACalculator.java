package org.promise.metrics.calculator;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for Measure of Aggregation (MOA).
 * 
 * MOA is the count of the number of class fields (attributes) whose types
 * are user-defined classes. This measures the extent of the part-whole 
 * relationship (aggregation/composition) realized by using attributes.
 * 
 * Fields with primitive types (int, boolean, etc.) and standard Java library
 * types (String, Integer, etc.) are NOT counted.
 * 
 * Only fields whose types are user-defined classes are counted.
 */
public class MOACalculator {

    // Common Java library types that should NOT be counted as user-defined
    private static final Set<String> STANDARD_TYPES = new HashSet<>();
    static {
        // Primitive wrappers
        STANDARD_TYPES.add("Boolean");
        STANDARD_TYPES.add("Byte");
        STANDARD_TYPES.add("Character");
        STANDARD_TYPES.add("Short");
        STANDARD_TYPES.add("Integer");
        STANDARD_TYPES.add("Long");
        STANDARD_TYPES.add("Float");
        STANDARD_TYPES.add("Double");
        STANDARD_TYPES.add("Void");
        STANDARD_TYPES.add("Number");
        
        // Common String types
        STANDARD_TYPES.add("String");
        STANDARD_TYPES.add("StringBuffer");
        STANDARD_TYPES.add("StringBuilder");
        STANDARD_TYPES.add("CharSequence");
        
        // Common collection types
        STANDARD_TYPES.add("Object");
        STANDARD_TYPES.add("Class");
        STANDARD_TYPES.add("Array");
        STANDARD_TYPES.add("Collection");
        STANDARD_TYPES.add("List");
        STANDARD_TYPES.add("ArrayList");
        STANDARD_TYPES.add("LinkedList");
        STANDARD_TYPES.add("Set");
        STANDARD_TYPES.add("HashSet");
        STANDARD_TYPES.add("TreeSet");
        STANDARD_TYPES.add("Map");
        STANDARD_TYPES.add("HashMap");
        STANDARD_TYPES.add("TreeMap");
        STANDARD_TYPES.add("Hashtable");
        STANDARD_TYPES.add("Vector");
        STANDARD_TYPES.add("Stack");
        STANDARD_TYPES.add("Queue");
        STANDARD_TYPES.add("Deque");
        STANDARD_TYPES.add("Iterator");
        STANDARD_TYPES.add("Enumeration");
        STANDARD_TYPES.add("Properties");
        STANDARD_TYPES.add("Dictionary");
        
        // Common I/O types
        STANDARD_TYPES.add("File");
        STANDARD_TYPES.add("InputStream");
        STANDARD_TYPES.add("OutputStream");
        STANDARD_TYPES.add("Reader");
        STANDARD_TYPES.add("Writer");
        STANDARD_TYPES.add("BufferedReader");
        STANDARD_TYPES.add("BufferedWriter");
        STANDARD_TYPES.add("PrintStream");
        STANDARD_TYPES.add("PrintWriter");
        STANDARD_TYPES.add("FileInputStream");
        STANDARD_TYPES.add("FileOutputStream");
        STANDARD_TYPES.add("FileReader");
        STANDARD_TYPES.add("FileWriter");
        STANDARD_TYPES.add("DataInputStream");
        STANDARD_TYPES.add("DataOutputStream");
        STANDARD_TYPES.add("ObjectInputStream");
        STANDARD_TYPES.add("ObjectOutputStream");
        STANDARD_TYPES.add("ByteArrayInputStream");
        STANDARD_TYPES.add("ByteArrayOutputStream");
        STANDARD_TYPES.add("StringReader");
        STANDARD_TYPES.add("StringWriter");
        STANDARD_TYPES.add("InputStreamReader");
        STANDARD_TYPES.add("OutputStreamWriter");
        STANDARD_TYPES.add("RandomAccessFile");
        
        // Date/Time types
        STANDARD_TYPES.add("Date");
        STANDARD_TYPES.add("Calendar");
        STANDARD_TYPES.add("GregorianCalendar");
        STANDARD_TYPES.add("TimeZone");
        STANDARD_TYPES.add("Locale");
        
        // Exception types
        STANDARD_TYPES.add("Exception");
        STANDARD_TYPES.add("RuntimeException");
        STANDARD_TYPES.add("Throwable");
        STANDARD_TYPES.add("Error");
        
        // Other common types
        STANDARD_TYPES.add("Thread");
        STANDARD_TYPES.add("Runnable");
        STANDARD_TYPES.add("URL");
        STANDARD_TYPES.add("URI");
        STANDARD_TYPES.add("Pattern");
        STANDARD_TYPES.add("Matcher");
        STANDARD_TYPES.add("Comparator");
        STANDARD_TYPES.add("Comparable");
        STANDARD_TYPES.add("Serializable");
        STANDARD_TYPES.add("Cloneable");
    }

    /**
     * Calculate MOA for a compilation unit (entire file).
     *
     * @param compilationUnit The parsed Java file
     * @return MOA count for all types in the file
     */
    public static int calculateMOA(CompilationUnit compilationUnit) {
        MOAVisitor visitor = new MOAVisitor();
        compilationUnit.accept(visitor);
        return visitor.moaCount;
    }

    /**
     * Calculate MOA for a specific type declaration.
     *
     * @param typeDeclaration The type to analyze
     * @return MOA count for the type
     */
    public static int calculateMOAForType(AbstractTypeDeclaration typeDeclaration) {
        MOAVisitor visitor = new MOAVisitor();
        typeDeclaration.accept(visitor);
        return visitor.moaCount;
    }

    /**
     * AST Visitor to count fields with user-defined types.
     * Only counts fields directly in the top-level type, NOT in nested/inner classes.
     */
    private static class MOAVisitor extends ASTVisitor {
        int moaCount = 0;
        int nestingLevel = 0;

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return nestingLevel == 1; // Only visit top-level type
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
            return false; // Don't visit anonymous class contents
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(FieldDeclaration node) {
            // Only count fields at the top level
            if (nestingLevel == 1) {
                Type fieldType = node.getType();
                if (isUserDefinedType(fieldType)) {
                    // Count the number of variable declarators (fields) in this declaration
                    // e.g., "UserClass a, b, c;" counts as 3
                    moaCount += node.fragments().size();
                }
            }
            return false; // Don't need to visit field contents
        }

        /**
         * Check if a type is a user-defined type (not primitive or standard library).
         */
        private boolean isUserDefinedType(Type type) {
            if (type == null) {
                return false;
            }

            // Handle primitive types - not user-defined
            if (type.isPrimitiveType()) {
                return false;
            }

            // Handle array types - check the element type
            if (type.isArrayType()) {
                ArrayType arrayType = (ArrayType) type;
                return isUserDefinedType(arrayType.getElementType());
            }

            // Handle parameterized types (generics) - check the base type
            if (type.isParameterizedType()) {
                ParameterizedType paramType = (ParameterizedType) type;
                return isUserDefinedType(paramType.getType());
            }

            // Handle simple types
            if (type.isSimpleType()) {
                SimpleType simpleType = (SimpleType) type;
                String typeName = simpleType.getName().toString();
                
                // Extract simple name if it's a qualified name
                int lastDot = typeName.lastIndexOf('.');
                if (lastDot >= 0) {
                    typeName = typeName.substring(lastDot + 1);
                }

                // Check if it's a standard Java type
                if (STANDARD_TYPES.contains(typeName)) {
                    return false;
                }

                // Check if it starts with java. or javax. package
                String fullName = simpleType.getName().getFullyQualifiedName();
                if (fullName.startsWith("java.") || fullName.startsWith("javax.")) {
                    return false;
                }

                // Otherwise, consider it a user-defined type
                return true;
            }

            // Handle qualified types
            if (type.isQualifiedType()) {
                // Qualified types are usually standard library types
                return false;
            }

            // Handle wildcard types - not user-defined by themselves
            if (type.isWildcardType()) {
                return false;
            }

            return false;
        }
    }
}
