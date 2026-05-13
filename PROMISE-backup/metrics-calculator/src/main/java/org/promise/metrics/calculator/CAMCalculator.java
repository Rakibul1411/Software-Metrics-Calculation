package org.promise.metrics.calculator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Calculator for CAM (Cohesion Among Methods of class).
 *
 * CAM measures the relatedness of methods based on their parameter types.
 *
 * Algorithm:
 *   1. For each method, collect the set of parameter types (including 'this' type implicitly).
 *   2. Compute the union of all parameter types across all methods = L (total distinct types).
 *   3. For each method, count how many of the L types appear in its parameter list.
 *   4. CAM = sum of these counts / (number of methods * L)
 *
 * The 'return type' of each method is also included in PROMISE's version of CAM.
 *
 * Special cases:
 *   - If there are no methods, CAM = 0
 *   - If L = 0 (no parameter types at all), CAM = 0
 *
 * CAM ranges from 0.0 to 1.0:
 *   - 1.0 = all methods share the same parameter types (high cohesion)
 *   - 0.0 = methods share no parameter types (no cohesion)
 */
public class CAMCalculator {

    /**
     * Calculate CAM for a specific type declaration.
     * Includes implicit default constructor if no explicit constructor exists.
     *
     * @param typeDeclaration The type to analyze
     * @return CAM value
     */
    public static double calculateCAMForType(AbstractTypeDeclaration typeDeclaration) {
        // Collect all top-level methods
        List<MethodDeclaration> methods = new ArrayList<>();
        MethodCollectorVisitor collector = new MethodCollectorVisitor(methods);
        typeDeclaration.accept(collector);

        // Check for explicit constructor
        boolean hasExplicitConstructor = false;
        for (MethodDeclaration method : methods) {
            if (method.isConstructor()) {
                hasExplicitConstructor = true;
                break;
            }
        }

        boolean isInterface = WMCCalculator.isInterfaceType(typeDeclaration);
        int totalMethods = methods.size();

        // Add implicit default constructor
        if (!hasExplicitConstructor && !isInterface) {
            totalMethods++;
        }

        if (totalMethods == 0) {
            return 0.0;
        }

        // For each method, collect parameter types (as simplified type name strings)
        List<Set<String>> methodParamTypes = new ArrayList<>();
        Set<String> allTypes = new HashSet<>();

        String className = typeDeclaration.getName().getIdentifier();

        for (MethodDeclaration method : methods) {
            Set<String> paramTypes = extractParameterTypes(method);
            // Add the class itself as an implicit parameter (simulating 'this' pointer in CKJM)
            paramTypes.add(className);
            
            methodParamTypes.add(paramTypes);
            allTypes.addAll(paramTypes);
        }

        // Default constructor has no explicit parameters, but still has 'this'
        if (!hasExplicitConstructor && !isInterface) {
            Set<String> defaultConstructorParams = new HashSet<>();
            defaultConstructorParams.add(className);
            methodParamTypes.add(defaultConstructorParams);
            allTypes.add(className);
        }

        int l = allTypes.size(); // total distinct parameter types
        if (l == 0) {
            return 0.0;
        }

        int m = totalMethods;

        // Sum: for each method, count how many of the L types appear
        double sum = 0.0;
        for (Set<String> paramTypes : methodParamTypes) {
            int count = 0;
            for (String type : allTypes) {
                if (paramTypes.contains(type)) {
                    count++;
                }
            }
            sum += count;
        }

        return sum / (m * l);
    }

    /**
     * Extract the set of parameter type names from a method declaration.
     * Only includes parameter types (not return type), matching ckjm-extended/QMOOD definition.
     *
     * @param method The method declaration
     * @return Set of type name strings
     */
    @SuppressWarnings("unchecked")
    private static Set<String> extractParameterTypes(MethodDeclaration method) {
        Set<String> types = new HashSet<>();

        // Collect parameter types
        List<SingleVariableDeclaration> params = method.parameters();
        for (SingleVariableDeclaration param : params) {
            String typeName = resolveTypeName(param.getType());
            if (typeName != null && !typeName.isEmpty()) {
                types.add(typeName);
            }
        }

        // Add return type if not void
        Type returnType = method.getReturnType2();
        if (returnType != null) {
            String rName = resolveTypeName(returnType);
            if (rName != null && !rName.isEmpty() && !rName.equals("void")) {
                types.add(rName);
            }
        }

        return types;
    }

    /**
     * Resolve a Type node to a simplified type name string.
     * Arrays are resolved to their element type.
     * Parameterized types are resolved to their raw type.
     */
    private static String resolveTypeName(Type type) {
        if (type == null) {
            return null;
        }

        if (type.isPrimitiveType()) {
            return ((PrimitiveType) type).getPrimitiveTypeCode().toString();
        }

        if (type.isSimpleType()) {
            return ((SimpleType) type).getName().getFullyQualifiedName();
        }

        if (type.isArrayType()) {
            return resolveTypeName(((ArrayType) type).getElementType());
        }

        if (type.isParameterizedType()) {
            return resolveTypeName(((ParameterizedType) type).getType());
        }

        // Fallback: use toString
        return type.toString();
    }

    /**
     * Visitor that collects only top-level methods (not methods in nested/inner classes).
     */
    private static class MethodCollectorVisitor extends ASTVisitor {
        private final List<MethodDeclaration> methods;
        private int nestingLevel = 0;

        MethodCollectorVisitor(List<MethodDeclaration> methods) {
            this.methods = methods;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            nestingLevel++;
            return true;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            nestingLevel--;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (nestingLevel == 1) {
                methods.add(node);
            }
            return false;
        }
    }
}
