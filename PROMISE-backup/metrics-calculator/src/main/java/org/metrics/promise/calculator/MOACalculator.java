package org.metrics.promise.calculator;

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

    public static java.util.List<String> extractFieldTypes(AbstractTypeDeclaration typeDeclaration) {
        MOAVisitor visitor = new MOAVisitor();
        typeDeclaration.accept(visitor);
        return visitor.fieldTypes;
    }

    private static class MOAVisitor extends ASTVisitor {
        java.util.List<String> fieldTypes = new java.util.ArrayList<>();
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
                String typeName = getSimpleTypeName(fieldType);
                if (typeName != null) {
                    int count = node.fragments().size();
                    for (int i = 0; i < count; i++) {
                        fieldTypes.add(typeName);
                    }
                }
            }
            return false; // Don't need to visit field contents
        }

        private String getSimpleTypeName(Type type) {
            if (type == null || type.isPrimitiveType()) {
                return null;
            }
            if (type.isArrayType()) {
                ArrayType arrayType = (ArrayType) type;
                return getSimpleTypeName(arrayType.getElementType());
            }
            if (type.isParameterizedType()) {
                ParameterizedType paramType = (ParameterizedType) type;
                return getSimpleTypeName(paramType.getType());
            }
            if (type.isSimpleType()) {
                SimpleType simpleType = (SimpleType) type;
                String typeName = simpleType.getName().toString();
                int lastDot = typeName.lastIndexOf('.');
                if (lastDot >= 0) {
                    typeName = typeName.substring(lastDot + 1);
                }
                return typeName;
            }
            return null;
        }
    }
}
