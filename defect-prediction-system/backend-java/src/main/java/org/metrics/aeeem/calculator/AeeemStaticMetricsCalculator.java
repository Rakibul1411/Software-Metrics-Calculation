package org.metrics.aeeem.calculator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.metrics.aeeem.model.AeeemMetricResult;

/**
 * Self-contained JDT implementation of the 17 AEEEM CK/OO source metrics.
 *
 * <p>This class performs source analysis only. WCHU and LDHH are calculated
 * later from changes in these values across bi-weekly snapshots.</p>
 */
public final class AeeemStaticMetricsCalculator {

    private AeeemStaticMetricsCalculator() {
    }

    public static AeeemMetricResult calculateAeeemForType(
            CompilationUnit compilationUnit,
            AbstractTypeDeclaration typeDeclaration,
            String sourceCode) {
        ITypeBinding typeBinding = typeDeclaration.resolveBinding();
        String fullyQualifiedName = qualifiedName(compilationUnit, typeDeclaration, typeBinding);
        AeeemMetricResult metrics = new AeeemMetricResult(fullyQualifiedName);
        boolean interfaceType = typeDeclaration instanceof TypeDeclaration
                && ((TypeDeclaration) typeDeclaration).isInterface();

        int privateMethods = 0;
        int publicMethods = 0;
        int methods = 0;
        int privateAttributes = 0;
        int publicAttributes = 0;
        int attributes = 0;
        int inheritableAttributes = 0;
        int wmc = 0;
        boolean hasExplicitConstructor = false;
        Set<String> declaredMethodSignatures = new LinkedHashSet<>();
        Set<String> inheritableMethodSignatures = new LinkedHashSet<>();
        List<MethodDeclaration> cohesionMethods = new ArrayList<>();
        Set<String> declaredFieldKeys = new LinkedHashSet<>();
        Set<String> fallbackFieldNames = new LinkedHashSet<>();
        Set<String> responseSet = new LinkedHashSet<>();

        for (Object declaration : typeDeclaration.bodyDeclarations()) {
            if (declaration instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) declaration;
                wmc++;
                responseSet.add(responseKey(method.resolveBinding(),
                        method.getName().getIdentifier(), method.parameters().size(), fullyQualifiedName));
                if (method.isConstructor()) {
                    hasExplicitConstructor = true;
                    continue;
                }

                methods++;
                String signature = inheritanceSignature(method);
                declaredMethodSignatures.add(signature);
                int modifiers = method.getModifiers();
                if (Modifier.isPrivate(modifiers)) {
                    privateMethods++;
                } else {
                    inheritableMethodSignatures.add(signature);
                }
                if (Modifier.isPublic(modifiers)
                        || (interfaceType && !Modifier.isPrivate(modifiers))) {
                    publicMethods++;
                }
                cohesionMethods.add(method);
            } else if (declaration instanceof FieldDeclaration) {
                FieldDeclaration field = (FieldDeclaration) declaration;
                int fieldCount = field.fragments().size();
                attributes += fieldCount;
                if (Modifier.isPrivate(field.getModifiers())) {
                    privateAttributes += fieldCount;
                } else {
                    inheritableAttributes += fieldCount;
                }
                if (Modifier.isPublic(field.getModifiers()) || interfaceType) {
                    publicAttributes += fieldCount;
                }
                for (Object fragmentObject : field.fragments()) {
                    VariableDeclarationFragment fragment =
                            (VariableDeclarationFragment) fragmentObject;
                    fallbackFieldNames.add(fragment.getName().getIdentifier());
                    IVariableBinding binding = fragment.resolveBinding();
                    declaredFieldKeys.add(fieldKey(binding, fragment.getName().getIdentifier()));
                }
            }
        }
        if (!interfaceType && !hasExplicitConstructor) {
            wmc++;
            responseSet.add(fullyQualifiedName + "#<init>()");
        }

        Set<String> dependencies = collectDependencies(typeDeclaration, typeBinding);
        collectInvokedMethods(typeDeclaration, fullyQualifiedName, responseSet);
        int lcom = calculateLcom(cohesionMethods, declaredFieldKeys, fallbackFieldNames);

        metrics.setInterface(interfaceType);
        metrics.setDependencies(dependencies);
        metrics.setDeclaredMethodSignatures(declaredMethodSignatures);
        metrics.setInheritableMethodSignatures(inheritableMethodSignatures);
        metrics.setDeclaredAttributeCount(attributes);
        metrics.setInheritableDeclaredAttributeCount(inheritableAttributes);
        metrics.setSuperclassName(superclassName(typeDeclaration, typeBinding));

        metrics.setCkOoNumberOfPrivateMethods(privateMethods);
        metrics.setCkOoNumberOfPublicMethods(publicMethods);
        metrics.setCkOoNumberOfMethods(methods);
        metrics.setCkOoNumberOfPrivateAttributes(privateAttributes);
        metrics.setCkOoNumberOfPublicAttributes(publicAttributes);
        metrics.setCkOoNumberOfAttributes(attributes);
        metrics.setCkOoWmc(wmc);
        metrics.setCkOoNumberOfLinesOfCode(
                countSourceLines(compilationUnit, typeDeclaration, sourceCode));
        metrics.setCkOoLcom(lcom);
        metrics.setCkOoRfc(responseSet.size());
        metrics.setCkOoDit(inheritanceDepth(typeBinding));

        // Project-level NOC, FanIn, FanOut, CBO and inherited-member counts are
        // filled after every class in the snapshot has been parsed.
        return metrics;
    }

    private static String qualifiedName(CompilationUnit unit, AbstractTypeDeclaration type,
                                        ITypeBinding binding) {
        if (binding != null && !binding.getQualifiedName().isEmpty()) {
            return binding.getQualifiedName();
        }
        List<String> names = new ArrayList<>();
        ASTNode current = type;
        while (current != null) {
            if (current instanceof AbstractTypeDeclaration) {
                names.add(0, ((AbstractTypeDeclaration) current).getName().getIdentifier());
            }
            current = current.getParent();
        }
        String packageName = unit.getPackage() == null
                ? "" : unit.getPackage().getName().getFullyQualifiedName();
        String localName = String.join(".", names);
        return packageName.isEmpty() ? localName : packageName + "." + localName;
    }

    private static String superclassName(AbstractTypeDeclaration type, ITypeBinding binding) {
        ITypeBinding superclass = binding == null ? null : binding.getSuperclass();
        if (superclass != null && !superclass.getQualifiedName().isEmpty()) {
            return superclass.getTypeDeclaration().getQualifiedName();
        }
        if (type instanceof TypeDeclaration) {
            org.eclipse.jdt.core.dom.Type raw = ((TypeDeclaration) type).getSuperclassType();
            return raw == null ? null : raw.toString();
        }
        return null;
    }

    private static int inheritanceDepth(ITypeBinding binding) {
        if (binding == null || binding.isInterface()) {
            return 0;
        }
        int depth = 0;
        Set<String> visited = new HashSet<>();
        ITypeBinding current = binding.getSuperclass();
        while (current != null) {
            ITypeBinding declaration = current.getTypeDeclaration();
            String key = declaration.getKey();
            if (key == null || !visited.add(key)) {
                break;
            }
            depth++;
            current = declaration.getSuperclass();
        }
        return depth;
    }

    private static Set<String> collectDependencies(
            AbstractTypeDeclaration owner,
            ITypeBinding ownerBinding) {
        Set<String> dependencies = new LinkedHashSet<>();
        String ownerName = ownerBinding == null
                ? "" : ownerBinding.getTypeDeclaration().getQualifiedName();
        owner.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                return node == owner;
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                return node == owner;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                return false;
            }

            @Override
            public boolean visit(SimpleName node) {
                IBinding binding = node.resolveBinding();
                if (binding instanceof ITypeBinding) {
                    addType((ITypeBinding) binding, ownerName, dependencies);
                } else if (binding instanceof IVariableBinding) {
                    IVariableBinding variable = (IVariableBinding) binding;
                    addType(variable.getType(), ownerName, dependencies);
                    addType(variable.getDeclaringClass(), ownerName, dependencies);
                } else if (binding instanceof IMethodBinding) {
                    IMethodBinding method = ((IMethodBinding) binding).getMethodDeclaration();
                    addType(method.getDeclaringClass(), ownerName, dependencies);
                    addType(method.getReturnType(), ownerName, dependencies);
                    for (ITypeBinding parameter : method.getParameterTypes()) {
                        addType(parameter, ownerName, dependencies);
                    }
                }
                return true;
            }

            @Override
            public boolean visit(SimpleType node) {
                addUnresolvedType(node.resolveBinding(), node.getName().getFullyQualifiedName(),
                        ownerName, dependencies);
                return true;
            }

            @Override
            public boolean visit(QualifiedType node) {
                addUnresolvedType(node.resolveBinding(), node.toString(), ownerName, dependencies);
                return true;
            }

            @Override
            public boolean visit(NameQualifiedType node) {
                addUnresolvedType(node.resolveBinding(), node.toString(), ownerName, dependencies);
                return true;
            }
        });
        return dependencies;
    }

    private static void addUnresolvedType(ITypeBinding binding, String fallback,
                                          String ownerName, Set<String> dependencies) {
        if (binding != null) {
            addType(binding, ownerName, dependencies);
        } else if (fallback != null && !fallback.isEmpty() && !fallback.equals(ownerName)) {
            dependencies.add(stripTypeArguments(fallback));
        }
    }

    private static void addType(ITypeBinding binding, String ownerName, Set<String> dependencies) {
        if (binding == null) {
            return;
        }
        ITypeBinding type = binding;
        while (type.isArray()) {
            type = type.getElementType();
        }
        if (type.isPrimitive() || type.isTypeVariable() || type.isWildcardType()
                || type.isCapture()) {
            return;
        }
        ITypeBinding declaration = type.getTypeDeclaration();
        String name = declaration.getQualifiedName();
        if (!name.isEmpty() && !name.equals(ownerName)) {
            dependencies.add(name);
        }
        for (ITypeBinding argument : type.getTypeArguments()) {
            addType(argument, ownerName, dependencies);
        }
    }

    private static String stripTypeArguments(String name) {
        String value = name.trim();
        int generic = value.indexOf('<');
        return generic < 0 ? value : value.substring(0, generic);
    }

    private static void collectInvokedMethods(
            AbstractTypeDeclaration owner,
            String ownerName,
            Set<String> responseSet) {
        owner.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                return node == owner;
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                return node == owner;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                return false;
            }

            @Override
            public boolean visit(MethodInvocation node) {
                responseSet.add(responseKey(node.resolveMethodBinding(),
                        node.getName().getIdentifier(), node.arguments().size(), ownerName));
                return true;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                responseSet.add(responseKey(node.resolveMethodBinding(),
                        node.getName().getIdentifier(), node.arguments().size(), ownerName));
                return true;
            }

            @Override
            public boolean visit(ClassInstanceCreation node) {
                responseSet.add(responseKey(node.resolveConstructorBinding(),
                        "<init>", node.arguments().size(), node.getType().toString()));
                return true;
            }

            @Override
            public boolean visit(ConstructorInvocation node) {
                responseSet.add(responseKey(node.resolveConstructorBinding(),
                        "<init>", node.arguments().size(), ownerName));
                return true;
            }

            @Override
            public boolean visit(SuperConstructorInvocation node) {
                responseSet.add(responseKey(node.resolveConstructorBinding(),
                        "<init>", node.arguments().size(), "super"));
                return true;
            }
        });
    }

    private static String responseKey(IMethodBinding binding, String fallbackName,
                                      int fallbackArity, String fallbackOwner) {
        if (binding == null) {
            return fallbackOwner + "#" + fallbackName + "/" + fallbackArity;
        }
        IMethodBinding method = binding.getMethodDeclaration();
        ITypeBinding declaringType = method.getDeclaringClass();
        String owner = declaringType == null || declaringType.getQualifiedName().isEmpty()
                ? fallbackOwner : declaringType.getTypeDeclaration().getQualifiedName();
        StringBuilder result = new StringBuilder(owner)
                .append('#')
                .append(method.isConstructor() ? "<init>" : method.getName())
                .append('(');
        ITypeBinding[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(typeName(parameters[index]));
        }
        return result.append(')').toString();
    }

    private static String inheritanceSignature(MethodDeclaration method) {
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            return method.getName().getIdentifier() + "/" + method.parameters().size();
        }
        IMethodBinding declaration = binding.getMethodDeclaration();
        StringBuilder result = new StringBuilder(declaration.getName()).append('(');
        ITypeBinding[] parameters = declaration.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(typeName(parameters[index]));
        }
        return result.append(')').toString();
    }

    private static String typeName(ITypeBinding binding) {
        if (binding == null) {
            return "?";
        }
        ITypeBinding type = binding;
        int dimensions = 0;
        while (type.isArray()) {
            dimensions++;
            type = type.getElementType();
        }
        ITypeBinding erasure = type.getErasure();
        String name = erasure.getQualifiedName();
        if (name.isEmpty()) {
            name = erasure.getName();
        }
        StringBuilder result = new StringBuilder(name);
        for (int index = 0; index < dimensions; index++) {
            result.append("[]");
        }
        return result.toString();
    }

    private static int calculateLcom(
            List<MethodDeclaration> methods,
            Set<String> declaredFieldKeys,
            Set<String> fallbackFieldNames) {
        if (methods.size() < 2) {
            return 0;
        }
        List<Set<String>> fieldsByMethod = new ArrayList<>();
        for (MethodDeclaration method : methods) {
            Set<String> accessed = new LinkedHashSet<>();
            if (method.getBody() != null) {
                method.getBody().accept(new ASTVisitor() {
                    @Override
                    public boolean visit(AnonymousClassDeclaration node) {
                        return false;
                    }

                    @Override
                    public boolean visit(TypeDeclaration node) {
                        return false;
                    }

                    @Override
                    public boolean visit(EnumDeclaration node) {
                        return false;
                    }

                    @Override
                    public boolean visit(SimpleName node) {
                        IBinding binding = node.resolveBinding();
                        if (binding instanceof IVariableBinding) {
                            IVariableBinding variable = ((IVariableBinding) binding)
                                    .getVariableDeclaration();
                            if (variable.isField()) {
                                String key = fieldKey(variable, node.getIdentifier());
                                if (declaredFieldKeys.contains(key)) {
                                    accessed.add(key);
                                }
                            }
                        } else if (fallbackFieldNames.contains(node.getIdentifier())) {
                            accessed.add(node.getIdentifier());
                        }
                        return true;
                    }
                });
            }
            fieldsByMethod.add(accessed);
        }

        int disjointPairs = 0;
        int sharingPairs = 0;
        for (int left = 0; left < fieldsByMethod.size(); left++) {
            for (int right = left + 1; right < fieldsByMethod.size(); right++) {
                if (sharesField(fieldsByMethod.get(left), fieldsByMethod.get(right))) {
                    sharingPairs++;
                } else {
                    disjointPairs++;
                }
            }
        }
        return Math.max(0, disjointPairs - sharingPairs);
    }

    private static String fieldKey(IVariableBinding binding, String fallback) {
        if (binding == null) {
            return fallback;
        }
        String key = binding.getVariableDeclaration().getKey();
        return key == null || key.isEmpty() ? fallback : key;
    }

    private static boolean sharesField(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static int countSourceLines(
            CompilationUnit unit,
            AbstractTypeDeclaration owner,
            String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return 0;
        }
        int start = Math.max(0, owner.getStartPosition());
        int end = Math.min(sourceCode.length(), start + owner.getLength());
        if (start >= end) {
            return 0;
        }
        char[] source = sourceCode.substring(start, end).toCharArray();

        @SuppressWarnings("unchecked")
        List<Comment> comments = unit.getCommentList();
        for (Comment comment : comments) {
            blankRange(source, start, comment.getStartPosition(),
                    comment.getStartPosition() + comment.getLength());
        }
        owner.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                if (node != owner) {
                    blankRange(source, start, node.getStartPosition(),
                            node.getStartPosition() + node.getLength());
                    return false;
                }
                return true;
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                if (node != owner) {
                    blankRange(source, start, node.getStartPosition(),
                            node.getStartPosition() + node.getLength());
                    return false;
                }
                return true;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                blankRange(source, start, node.getStartPosition(),
                        node.getStartPosition() + node.getLength());
                return false;
            }
        });

        int lines = 0;
        boolean hasCode = false;
        for (char character : source) {
            if (character == '\n' || character == '\r') {
                if (hasCode) {
                    lines++;
                    hasCode = false;
                }
            } else if (!Character.isWhitespace(character)) {
                hasCode = true;
            }
        }
        return lines + (hasCode ? 1 : 0);
    }

    private static void blankRange(char[] source, int sourceOffset, int absoluteStart,
                                   int absoluteEnd) {
        int start = Math.max(0, absoluteStart - sourceOffset);
        int end = Math.min(source.length, absoluteEnd - sourceOffset);
        for (int index = start; index < end; index++) {
            if (source[index] != '\n' && source[index] != '\r') {
                source[index] = ' ';
            }
        }
    }
}
