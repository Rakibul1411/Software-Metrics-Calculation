package org.metrics.aeeem.calculator.legacy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;

/**
 * Calculator for Coupling Between Objects (CBO).
 * 
 * CBO measures the number of classes to which a class is coupled.
 * This implementation uses BIDIRECTIONAL coupling (matching PROMISE dataset):
 * - CBO = CA (afferent coupling) + CE (efferent coupling)
 * - CA = number of classes that depend on this class
 * - CE = number of classes this class depends on
 * 
 * This requires a two-pass calculation:
 * 1. First pass: collect all dependencies for each class
 * 2. Second pass: calculate CBO = classes this class uses + classes that use this class
 */
public class CBOCalculator {

    // Maps class name -> set of classes it depends on (efferent/outgoing)
    private Map<String, Set<String>> efferentCoupling = new HashMap<>();
    
    // Maps class name -> set of classes that depend on it (afferent/incoming)
    private Map<String, Set<String>> afferentCoupling = new HashMap<>();
    
    // Maps class name -> superclass name (simple name)
    private Map<String, String> inheritanceMap = new HashMap<>();
    
    // All known class names in the project
    private Set<String> allClassNames = new HashSet<>();

    /**
     * Register a class and its dependencies.
     * Call this for each class in the project during first pass.
     *
     * @param className The fully qualified class name
     * @param superclassName The simple name of the superclass (or null/empty)
     * @param dependencies Set of class names this class depends on
     */
    public void registerClass(String className, String superclassName, Set<String> dependencies) {
        String simpleName = getSimpleName(className);
        allClassNames.add(simpleName);
        
        if (superclassName != null && !superclassName.isEmpty()) {
            inheritanceMap.put(simpleName, superclassName);
            // Extending a class counts as a dependency!
            // But usually this key is already in 'dependencies' set from AST.
        }
        
        // Store efferent coupling (outgoing dependencies)
        efferentCoupling.put(simpleName, new HashSet<>(dependencies));
        
        // Update afferent coupling (incoming dependencies) for each dependency
        for (String dependency : dependencies) {
            afferentCoupling.computeIfAbsent(dependency, k -> new HashSet<>()).add(simpleName);
        }
    }

    /**
     * Calculate CBO for a class (bidirectional coupling).
     * Must be called after all classes are registered.
     * 
     * NOTE: Only counts coupling to classes that are part of the analyses project (registered classes).
     * System/Library classes are excluded.
     * 
     * HEURISTIC: Implicit dependency on Project for Task subclasses.
     *
     * @param className The fully qualified class name
     * @return CBO value (CA + CE, counting unique classes)
     */
    public int calculateCBO(String className) {
        String simpleName = getSimpleName(className);
        Set<String> coupledClasses = new HashSet<>();
        
        // Add efferent coupling (classes this class uses)
        // FILTER: Only include classes that are part of the project
        Set<String> efferent = new HashSet<>(efferentCoupling.getOrDefault(simpleName, Collections.emptySet()));
        
        // CHECK HEURISTIC: Inherited Project dependency
        checkImplicitDependencies(simpleName, efferent);
        
        for (String dep : efferent) {
             // ... existing loop
            if (allClassNames.contains(dep)) {
                coupledClasses.add(dep);
            }
        }
        
        Set<String> afferent = afferentCoupling.getOrDefault(simpleName, Collections.emptySet());
        for (String dep : afferent) {
            if (allClassNames.contains(dep)) {
                coupledClasses.add(dep);
            }
        }
        
        return coupledClasses.size();
    }

    /**
     * Post-process dependencies to handle implicit inheritance.
     * Should be called after all classes are registered.
     */
    public void postProcessDependencies() {
        for (String className : new HashSet<>(allClassNames)) {
            Set<String> dependencies = efferentCoupling.get(className);
            if (dependencies == null) continue;
            
            // Check implicit dependencies via inheritance
            Set<String> addedDeps = getImplicitDependencies(className);
            if (!addedDeps.isEmpty()) {
                dependencies.addAll(addedDeps);
                
                // Also update the reverse mapping (afferent)
                for (String dep : addedDeps) {
                    afferentCoupling.computeIfAbsent(dep, k -> new HashSet<>()).add(className);
                }
            }
        }
    }

    private Set<String> getImplicitDependencies(String className) {
        // Removed hardcoded heuristics (e.g., Task -> Target) as they inflate CA/CBO
        // relative to the original CKJM bytecode analysis.
        return new HashSet<>();
    }
    
    // Helper used inside calculateCBO above (kept for compatibility if method structure changed)
    private void checkImplicitDependencies(String className, Set<String> efferent) {
        // This is now redundant if postProcessDependencies is called, 
        // but harmless as it adds to a Set.
        efferent.addAll(getImplicitDependencies(className));
    }

    /**
     * Calculate CA (afferent coupling) for a class.
     * Number of classes that depend on this class.
     *
     * @param className The fully qualified class name
     * @return CA value
     */
    public int calculateCA(String className) {
        String simpleName = getSimpleName(className);
        Set<String> afferent = afferentCoupling.getOrDefault(simpleName, Collections.emptySet());
        int count = 0;
        for (String dep : afferent) {
            if (allClassNames.contains(dep)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculate CE (efferent coupling) for a class.
     * Number of classes this class depends on.
     *
     * @param className The fully qualified class name
     * @return CE value
     */
    public int calculateCE(String className) {
        String simpleName = getSimpleName(className);
        Set<String> efferent = efferentCoupling.getOrDefault(simpleName, Collections.emptySet());
        int count = 0;
        for (String dep : efferent) {
            if (allClassNames.contains(dep)) {
                count++;
            }
        }
        return count;
    }

    private static String getSimpleName(String fullName) {
        if (fullName == null) return "";
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    /**
     * Extract dependencies from a type declaration.
     * Returns the set of class names this type depends on.
     *
     * @param typeDeclaration The type to analyze
     * @param currentClassName The fully qualified name of the current class
     * @param imports List of imports in the compilation unit
     * @return Set of class names this class depends on
     */
    public static Set<String> extractDependencies(AbstractTypeDeclaration typeDeclaration, String currentClassName, List<ImportDeclaration> imports) {
        DependencyVisitor visitor = new DependencyVisitor(currentClassName);
        typeDeclaration.accept(visitor);
        
        // Add dependencies from imports
        if (imports != null) {
            for (Object obj : imports) {
                if (obj instanceof ImportDeclaration) {
                    ImportDeclaration imp = (ImportDeclaration) obj;
                    if (!imp.isStatic()) {
                        String name = imp.getName().getFullyQualifiedName();
                        visitor.addDependency(name);
                    }
                }
            }
        }

        return visitor.getDependencies();
    }

    /**
     * AST Visitor to collect all type dependencies.
     */
    private static class DependencyVisitor extends ASTVisitor {
        private final Set<String> dependencies = new HashSet<>();
        private final String currentClassName;
        private final String currentSimpleName;
        private int nestingLevel = 0;

        // Common Java types to exclude from coupling count
        private static final Set<String> EXCLUDED_TYPES = new HashSet<>();
        static {
            // Primitive types and their wrappers
            EXCLUDED_TYPES.add("void");
            EXCLUDED_TYPES.add("boolean");
            EXCLUDED_TYPES.add("byte");
            EXCLUDED_TYPES.add("char");
            EXCLUDED_TYPES.add("short");
            EXCLUDED_TYPES.add("int");
            EXCLUDED_TYPES.add("long");
            EXCLUDED_TYPES.add("float");
            EXCLUDED_TYPES.add("double");
            EXCLUDED_TYPES.add("Boolean");
            EXCLUDED_TYPES.add("Byte");
            EXCLUDED_TYPES.add("Character");
            EXCLUDED_TYPES.add("Short");
            EXCLUDED_TYPES.add("Integer");
            EXCLUDED_TYPES.add("Long");
            EXCLUDED_TYPES.add("Float");
            EXCLUDED_TYPES.add("Double");
            // Common Java types
            EXCLUDED_TYPES.add("String");
            EXCLUDED_TYPES.add("Object");
            EXCLUDED_TYPES.add("Class");
            EXCLUDED_TYPES.add("Void");
        }

        public DependencyVisitor(String currentClassName) {
            this.currentClassName = currentClassName;
            this.currentSimpleName = getSimpleName(currentClassName);
        }

        public Set<String> getDependencies() {
            return dependencies;
        }

        private static String getSimpleName(String fullName) {
            if (fullName == null) return "";
            int lastDot = fullName.lastIndexOf('.');
            return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
        }

        // Track superclass and interfaces
        @Override
        public boolean visit(TypeDeclaration node) {
            nestingLevel++;
            if (nestingLevel >= 1) {
                // Check superclass
                Type superclassType = node.getSuperclassType();
                if (superclassType != null) {
                    addTypeReference(superclassType);
                }
                // Check implemented interfaces
                for (Object iface : node.superInterfaceTypes()) {
                    addTypeReference((Type) iface);
                }
            }
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

        // Track field types
        @Override
        public boolean visit(FieldDeclaration node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getType());
            }
            return true;
        }

        // Track method return types and parameter types
        @Override
        public boolean visit(MethodDeclaration node) {
            if (nestingLevel >= 1) {
                // Return type
                Type returnType = node.getReturnType2();
                if (returnType != null) {
                    addTypeReference(returnType);
                }
                // Parameters
                for (Object param : node.parameters()) {
                    SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
                    addTypeReference(svd.getType());
                }
                // Thrown exceptions
                for (Object exc : node.thrownExceptionTypes()) {
                    addTypeReference((Type) exc);
                }
            }
            return true;
        }

        // Track local variable types
        @Override
        public boolean visit(VariableDeclarationStatement node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getType());
            }
            return true;
        }

        // Track variable declarations in for loops
        @Override
        public boolean visit(VariableDeclarationExpression node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getType());
            }
            return true;
        }

        // Track class instance creation (new SomeClass())
        @Override
        public boolean visit(ClassInstanceCreation node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getType());
            }
            return true;
        }

        // Track cast expressions
        @Override
        public boolean visit(CastExpression node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getType());
            }
            return true;
        }

        // Track instanceof expressions
        @Override
        public boolean visit(InstanceofExpression node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getRightOperand());
            }
            return true;
        }

        // Track catch clause exception types
        @Override
        public boolean visit(CatchClause node) {
            if (nestingLevel >= 1) {
                addTypeReference(node.getException().getType());
            }
            return true;
        }

        // Track method invocations on other objects (static calls)
        @Override
        public boolean visit(MethodInvocation node) {
            if (nestingLevel >= 1) {
                Expression expr = node.getExpression();
                if (expr != null && expr instanceof SimpleName) {
                    String name = ((SimpleName) expr).getIdentifier();
                    // Heuristic: if starts with uppercase, likely a class (static call)
                    if (Character.isUpperCase(name.charAt(0))) {
                        addDependency(name);
                    }
                }
            }
            return true;
        }

        // Track static field access
        @Override
        public boolean visit(QualifiedName node) {
            if (nestingLevel >= 1) {
                Name qualifier = node.getQualifier();
                if (qualifier instanceof SimpleName) {
                    String name = ((SimpleName) qualifier).getIdentifier();
                    if (Character.isUpperCase(name.charAt(0)) && !EXCLUDED_TYPES.contains(name)) {
                        addDependency(name);
                    }
                }
            }
            return true;
        }

        /**
         * Add a type reference to dependencies.
         */
        private void addTypeReference(Type type) {
            if (type == null) {
                return;
            }

            if (type.isSimpleType()) {
                SimpleType simpleType = (SimpleType) type;
                String typeName = simpleType.getName().getFullyQualifiedName();
                addDependency(typeName);
            } else if (type.isQualifiedType()) {
                QualifiedType qualifiedType = (QualifiedType) type;
                String typeName = qualifiedType.getName().getIdentifier();
                addDependency(typeName);
            } else if (type.isParameterizedType()) {
                ParameterizedType paramType = (ParameterizedType) type;
                addTypeReference(paramType.getType());
                for (Object arg : paramType.typeArguments()) {
                    addTypeReference((Type) arg);
                }
            } else if (type.isArrayType()) {
                ArrayType arrayType = (ArrayType) type;
                addTypeReference(arrayType.getElementType());
            } else if (type.isWildcardType()) {
                WildcardType wildcardType = (WildcardType) type;
                Type bound = wildcardType.getBound();
                if (bound != null) {
                    addTypeReference(bound);
                }
            }
            // Primitive types are ignored
        }

        /**
         * Add a class name to dependencies.
         */
        private void addDependency(String className) {
            if (className == null || className.isEmpty()) {
                return;
            }

            String simpleName = getSimpleName(className);

            // Exclude current class
            if (simpleName.equals(currentSimpleName)) {
                return;
            }

            // Exclude common types
            if (EXCLUDED_TYPES.contains(simpleName)) {
                return;
            }

            dependencies.add(simpleName);
        }
    }
}
