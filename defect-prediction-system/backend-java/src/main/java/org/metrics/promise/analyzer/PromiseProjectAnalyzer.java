package org.metrics.promise.analyzer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.metrics.promise.model.PromiseMetricResult;

public class PromiseProjectAnalyzer {

    private static final String OBJECT = "java.lang.Object";

    private final List<String> diagnostics = new ArrayList<>();
    private final Map<Path, String> sourceByPath = new LinkedHashMap<>();
    private final Map<Path, CompilationUnit> unitsByPath = new LinkedHashMap<>();
    private final Map<String, TypeInfo> typesByKey = new LinkedHashMap<>();
    private final Map<String, TypeInfo> typesByName = new LinkedHashMap<>();

    public static List<PromiseMetricResult> analyzeDirectories(Collection<Path> sourceDirectories) throws IOException {
        PromiseProjectAnalyzer analyzer = new PromiseProjectAnalyzer();
        return analyzer.analyze(sourceDirectories);
    }

    public List<String> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    private List<PromiseMetricResult> analyze(Collection<Path> sourceDirectories) throws IOException {
        List<Path> javaFiles = collectJavaFiles(sourceDirectories);
        if (javaFiles.isEmpty()) {
            return Collections.emptyList();
        }

        parseProject(javaFiles, sourceDirectories);
        collectTypes();
        collectDirectMembers();
        calculateProjectLevelRelationships();

        List<PromiseMetricResult> results = typesByName.values().stream()
                .sorted(Comparator.comparing(TypeInfo::getName))
                .map(this::toMetricResult)
                .collect(Collectors.toList());

        diagnostics.forEach(message -> System.err.println("PROMISE metric warning: " + message));
        return results;
    }

    private List<Path> collectJavaFiles(Collection<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().contains("/optional/") && !path.toString().contains("\\optional\\"))
                        .filter(path -> !path.toString().contains("/test/") && !path.toString().contains("\\test\\"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
    }

    private void parseProject(List<Path> javaFiles, Collection<Path> requestedRoots) throws IOException {
        for (Path file : javaFiles) {
            sourceByPath.put(file.toAbsolutePath().normalize(), new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }

        List<String> sourcePaths = inferSourcePaths(javaFiles, requestedRoots);
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(classPath(requestedRoots), sourcePaths.toArray(new String[0]), null, true);
        parser.setCompilerOptions(compilerOptions());

        String[] fileNames = javaFiles.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toArray(String[]::new);
        String[] encodings = new String[fileNames.length];
        Arrays.fill(encodings, StandardCharsets.UTF_8.name());

        parser.createASTs(fileNames, encodings, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                Path path = Path.of(sourceFilePath).toAbsolutePath().normalize();
                unitsByPath.put(path, ast);
                reportProblems(path, ast);
            }
        }, null);
    }

    private List<String> inferSourcePaths(List<Path> javaFiles, Collection<Path> requestedRoots) {
        Set<String> roots = new LinkedHashSet<>();
        for (Path requestedRoot : requestedRoots) {
            if (requestedRoot != null && Files.isDirectory(requestedRoot)) {
                roots.add(requestedRoot.toAbsolutePath().normalize().toString());
            }
        }

        for (Path javaFile : javaFiles) {
            String packageName = readPackageName(javaFile);
            Path sourceRoot = javaFile.toAbsolutePath().normalize().getParent();
            if (packageName != null && !packageName.isEmpty()) {
                String[] parts = packageName.split("\\.");
                for (int index = parts.length - 1; index >= 0 && sourceRoot != null; index--) {
                    if (sourceRoot.getFileName() != null
                            && sourceRoot.getFileName().toString().equals(parts[index])) {
                        sourceRoot = sourceRoot.getParent();
                    } else {
                        break;
                    }
                }
            }
            if (sourceRoot != null) {
                roots.add(sourceRoot.toString());
            }
        }
        return new ArrayList<>(roots);
    }

    private String readPackageName(Path javaFile) {
        try {
            ASTParser parser = ASTParser.newParser(AST.JLS17);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8).toCharArray());
            CompilationUnit unit = (CompilationUnit) parser.createAST(null);
            PackageDeclaration declaration = unit.getPackage();
            return declaration == null ? "" : declaration.getName().getFullyQualifiedName();
        } catch (IOException ex) {
            diagnostics.add("Could not infer source root for " + javaFile + ": " + ex.getMessage());
            return "";
        }
    }

    private String[] classPath(Collection<Path> requestedRoots) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        String value = System.getProperty("java.class.path", "");
        if (!value.trim().isEmpty()) {
            entries.addAll(Arrays.asList(value.split(File.pathSeparator)));
        }

        for (Path root : classpathSearchRoots(requestedRoots)) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .forEach(entries::add);
            }
        }
        return entries.toArray(new String[0]);
    }

    private Set<Path> classpathSearchRoots(Collection<Path> requestedRoots) {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path requestedRoot : requestedRoots) {
            if (requestedRoot == null) {
                continue;
            }
            Path current = requestedRoot.toAbsolutePath().normalize();
            roots.add(current);
            while (current != null && current.getFileName() != null && isSourceLayoutSegment(current.getFileName().toString())) {
                current = current.getParent();
                if (current != null) {
                    roots.add(current);
                }
            }
        }
        return roots;
    }

    private boolean isSourceLayoutSegment(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return "java".equals(normalized) || "main".equals(normalized) || "src".equals(normalized);
    }

    private Map<String, String> compilerOptions() {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_17);
        options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
        return options;
    }

    private void reportProblems(Path path, CompilationUnit ast) {
        for (IProblem problem : ast.getProblems()) {
            if (problem.isError()) {
                diagnostics.add(path + ":" + problem.getSourceLineNumber() + " " + problem.getMessage());
            }
        }
    }

    private void collectTypes() {
        for (Map.Entry<Path, CompilationUnit> entry : unitsByPath.entrySet()) {
            Path path = entry.getKey();
            CompilationUnit unit = entry.getValue();
            String source = sourceByPath.get(path);
            char[] commentFreeSource = stripComments(source, unit);

            unit.accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    registerType(path, unit, source, commentFreeSource, node);
                    return true;
                }

                @Override
                public boolean visit(EnumDeclaration node) {
                    registerType(path, unit, source, commentFreeSource, node);
                    return true;
                }
            });
        }
    }

    private void registerType(Path sourcePath, CompilationUnit unit, String source,
                              char[] commentFreeSource, AbstractTypeDeclaration node) {
        ITypeBinding binding = node.resolveBinding();
        if (binding == null) {
            diagnostics.add("Unresolved type binding for " + node.getName().getIdentifier() + " in " + sourcePath);
            return;
        }

        String key = typeKey(binding);
        String name = typeName(binding);
        if (key == null || name == null || name.isEmpty() || typesByKey.containsKey(key)) {
            return;
        }

        TypeInfo info = new TypeInfo(sourcePath, unit, source, commentFreeSource, node, binding, key, name);
        typesByKey.put(key, info);
        typesByName.put(name, info);
    }

    private void collectDirectMembers() {
        for (TypeInfo type : typesByName.values()) {
            collectFields(type);
            collectMethods(type);
            calculateLocalMetrics(type);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFields(TypeInfo type) {
        for (Object declaration : type.node.bodyDeclarations()) {
            if (!(declaration instanceof FieldDeclaration)) {
                continue;
            }
            FieldDeclaration field = (FieldDeclaration) declaration;
            ITypeBinding declaredType = field.getType().resolveBinding();
            if (declaredType == null) {
                diagnostics.add("Unresolved field type in " + type.name + " at line "
                        + type.unit.getLineNumber(field.getStartPosition()));
            }
            for (VariableDeclarationFragment fragment : (List<VariableDeclarationFragment>) field.fragments()) {
                IVariableBinding binding = fragment.resolveBinding();
                if (binding == null) {
                    diagnostics.add("Unresolved field binding for " + type.name + "." + fragment.getName());
                    continue;
                }
                type.fields.add(new FieldInfo(binding.getVariableDeclaration().getKey(), field, declaredType));
                addTypeDependency(type, declaredType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectMethods(TypeInfo type) {
        boolean hasConstructor = false;
        for (Object declaration : type.node.bodyDeclarations()) {
            if (!(declaration instanceof MethodDeclaration)) {
                continue;
            }

            MethodDeclaration method = (MethodDeclaration) declaration;
            IMethodBinding binding = method.resolveBinding();
            if (binding == null) {
                diagnostics.add("Unresolved method binding for " + type.name + "." + method.getName());
                continue;
            }

            MethodInfo info = new MethodInfo(method, binding.getMethodDeclaration(), false);
            info.signature = methodKey(binding);
            info.complexity = calculateCyclomaticComplexity(method);
            info.loc = countSourceLines(type, method);
            info.publicMethod = isPublicMethod(type, method, binding);
            info.parameterTypes.addAll(parameterTypeKeys(binding));
            type.methods.add(info);

            if (method.isConstructor()) {
                hasConstructor = true;
            }

            collectMethodBodyData(type, info);
        }

        if (!hasConstructor && !type.isInterface()) {
            MethodInfo defaultConstructor = MethodInfo.implicitDefaultConstructor(type);
            type.methods.add(defaultConstructor);
        }
    }

    private boolean isPublicMethod(TypeInfo type, MethodDeclaration method, IMethodBinding binding) {
        if (Modifier.isPublic(method.getModifiers()) || Modifier.isPublic(binding.getModifiers())) {
            return true;
        }
        return type.isInterface() && !Modifier.isPrivate(method.getModifiers());
    }

    private List<String> parameterTypeKeys(IMethodBinding binding) {
        List<String> result = new ArrayList<>();
        for (ITypeBinding parameter : binding.getParameterTypes()) {
            ITypeBinding erasure = safeErasure(parameter);
            String key = typeKey(erasure);
            if (key != null) {
                result.add(key);
            }
        }
        return result;
    }

    private void collectMethodBodyData(TypeInfo type, MethodInfo methodInfo) {
        MethodDeclaration method = methodInfo.node;
        if (method.getBody() == null) {
            return;
        }

        method.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                return false;
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                return false;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                return false;
            }

            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding binding = node.resolveMethodBinding();
                if (binding == null) {
                    diagnostics.add("Unresolved method call " + node.getName() + " in " + type.name
                            + " at line " + type.unit.getLineNumber(node.getStartPosition()));
                } else {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                    addMethodDependency(type, binding);
                    collectInheritedCall(type, methodInfo, binding);
                }
                return true;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                IMethodBinding binding = node.resolveMethodBinding();
                if (binding == null) {
                    diagnostics.add("Unresolved super method call " + node.getName() + " in " + type.name
                            + " at line " + type.unit.getLineNumber(node.getStartPosition()));
                } else {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                    addMethodDependency(type, binding);
                    collectInheritedCall(type, methodInfo, binding);
                }
                return true;
            }

            @Override
            public boolean visit(ClassInstanceCreation node) {
                IMethodBinding constructor = node.resolveConstructorBinding();
                if (constructor == null) {
                    diagnostics.add("Unresolved constructor call in " + type.name + " at line "
                            + type.unit.getLineNumber(node.getStartPosition()));
                } else {
                    methodInfo.invokedMethods.add(constructor.getMethodDeclaration());
                    addMethodDependency(type, constructor);
                }
                addTypeDependency(type, safeErasure(node.getType().resolveBinding()));
                return true;
            }

            @Override
            public boolean visit(ConstructorInvocation node) {
                IMethodBinding binding = node.resolveConstructorBinding();
                if (binding != null) {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                } else {
                    diagnostics.add("Unresolved this-constructor call in " + type.name + " at line "
                            + type.unit.getLineNumber(node.getStartPosition()));
                }
                return true;
            }

            @Override
            public boolean visit(SuperConstructorInvocation node) {
                IMethodBinding binding = node.resolveConstructorBinding();
                if (binding != null) {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                    addMethodDependency(type, binding);
                } else {
                    diagnostics.add("Unresolved super-constructor call in " + type.name + " at line "
                            + type.unit.getLineNumber(node.getStartPosition()));
                }
                return true;
            }

            @Override
            public boolean visit(SimpleName node) {
                collectFieldAccess(type, methodInfo, node.resolveBinding());
                return true;
            }

            @Override
            public boolean visit(QualifiedName node) {
                collectFieldAccess(type, methodInfo, node.resolveBinding());
                return true;
            }

            @Override
            public boolean visit(FieldAccess node) {
                collectFieldAccess(type, methodInfo, node.resolveFieldBinding());
                return true;
            }

            @Override
            public boolean visit(SuperFieldAccess node) {
                collectFieldAccess(type, methodInfo, node.resolveFieldBinding());
                return true;
            }
        });
    }

    private void collectFieldAccess(TypeInfo type, MethodInfo methodInfo, IBinding binding) {
        if (!(binding instanceof IVariableBinding)) {
            return;
        }
        IVariableBinding variable = ((IVariableBinding) binding).getVariableDeclaration();
        if (!variable.isField()) {
            return;
        }

        ITypeBinding declaringClass = variable.getDeclaringClass();
        if (sameType(type.binding, declaringClass) && !Modifier.isStatic(variable.getModifiers())) {
            methodInfo.accessedInstanceFields.add(variable.getKey());
        }
        addTypeDependency(type, declaringClass);
        addTypeDependency(type, variable.getType());
    }

    private void addMethodDependency(TypeInfo type, IMethodBinding binding) {
        ITypeBinding declaringClass = binding.getDeclaringClass();
        addTypeDependency(type, declaringClass);
        addTypeDependency(type, binding.getReturnType());
        for (ITypeBinding parameterType : binding.getParameterTypes()) {
            addTypeDependency(type, parameterType);
        }
    }

    private void collectInheritedCall(TypeInfo type, MethodInfo caller, IMethodBinding calledMethod) {
        ITypeBinding declaringClass = calledMethod.getDeclaringClass();
        if (declaringClass == null || sameType(type.binding, declaringClass)) {
            return;
        }
        if (isAncestor(type.binding, declaringClass)) {
            String ancestorKey = typeKey(declaringClass);
            if (ancestorKey != null) {
                caller.inheritedCallAncestors.add(ancestorKey);
                caller.inheritedCallPairs.add(caller.signature + "->" + methodKey(calledMethod));
            }
        }
    }

    private void addTypeDependency(TypeInfo owner, ITypeBinding binding) {
        if (binding == null) {
            return;
        }
        collectReferencedTypeKeys(binding, owner.outgoingTypeKeys);
    }

    private void collectReferencedTypeKeys(ITypeBinding binding, Set<String> target) {
        if (binding == null) {
            return;
        }
        for (ITypeBinding argument : binding.getTypeArguments()) {
            collectReferencedTypeKeys(argument, target);
        }

        ITypeBinding erasure = safeErasure(binding);
        if (erasure == null) {
            return;
        }
        if (erasure.isArray()) {
            collectReferencedTypeKeys(erasure.getElementType(), target);
            return;
        }
        if (erasure.isPrimitive() || erasure.isNullType() || erasure.isTypeVariable() || erasure.isWildcardType()) {
            return;
        }

        String key = typeKey(erasure);
        if (key != null) {
            target.add(key);
        }
    }

    private void calculateLocalMetrics(TypeInfo type) {
        type.loc = countSourceLines(type, type.node);
        type.wmc = type.methods.stream().mapToInt(method -> method.complexity).sum();
        type.rfc = calculateRFC(type);
        type.npm = (int) type.methods.stream().filter(method -> method.publicMethod).count();
        type.lcom = calculateLCOM(type);
        type.lcom3 = calculateLCOM3(type);
        type.dam = calculateDAM(type);
        type.moa = calculateMOA(type);
        type.mfa = calculateMFA(type);
        type.cam = calculateCAM(type);
        type.ic = calculateIC(type);
        type.cbm = calculateCBM(type);
        type.amc = calculateAMC(type);
        type.maxCc = type.methods.stream().mapToInt(method -> method.complexity).max().orElse(0);
        type.avgCc = type.methods.isEmpty()
                ? 0.0
                : type.methods.stream().mapToInt(method -> method.complexity).average().orElse(0.0);
    }

    private int calculateRFC(TypeInfo type) {
        Set<String> responseSet = new HashSet<>();
        for (MethodInfo method : type.methods) {
            responseSet.add(method.signature);
            for (IMethodBinding invoked : method.invokedMethods) {
                String key = methodKey(invoked);
                if (key != null) {
                    responseSet.add(key);
                }
            }
        }
        return responseSet.size();
    }

    private int calculateLCOM(TypeInfo type) {
        if (type.methods.size() < 2) {
            return 0;
        }

        int disjointPairs = 0;
        int sharedPairs = 0;
        for (int i = 0; i < type.methods.size(); i++) {
            for (int j = i + 1; j < type.methods.size(); j++) {
                if (sharesAnyField(type.methods.get(i), type.methods.get(j))) {
                    sharedPairs++;
                } else {
                    disjointPairs++;
                }
            }
        }
        return Math.max(disjointPairs - sharedPairs, 0);
    }

    private boolean sharesAnyField(MethodInfo first, MethodInfo second) {
        for (String field : first.accessedInstanceFields) {
            if (second.accessedInstanceFields.contains(field)) {
                return true;
            }
        }
        return false;
    }

    private double calculateLCOM3(TypeInfo type) {
        int attributes = (int) type.fields.stream()
                .filter(field -> !Modifier.isStatic(field.declaration.getModifiers()))
                .count();
        int methods = type.methods.size();
        if (attributes == 0 || methods <= 1) {
            return 0.0;
        }

        int accessSum = type.methods.stream()
                .mapToInt(method -> method.accessedInstanceFields.size())
                .sum();
        double value = (methods - ((double) accessSum / attributes)) / (methods - 1);
        return Math.max(0.0, value);
    }

    private double calculateDAM(TypeInfo type) {
        if (type.fields.isEmpty()) {
            return 0.0;
        }
        long encapsulated = type.fields.stream()
                .filter(field -> Modifier.isPrivate(field.declaration.getModifiers())
                        || Modifier.isProtected(field.declaration.getModifiers()))
                .count();
        return (double) encapsulated / type.fields.size();
    }

    private int calculateMOA(TypeInfo type) {
        int count = 0;
        for (FieldInfo field : type.fields) {
            Set<String> fieldTypes = new HashSet<>();
            collectReferencedTypeKeys(field.type, fieldTypes);
            for (String key : fieldTypes) {
                if (typesByKey.containsKey(key) && !key.equals(type.key)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private double calculateMFA(TypeInfo type) {
        Set<String> declared = type.methods.stream()
                .filter(method -> !method.constructor)
                .map(method -> methodSubsignature(method.binding))
                .collect(Collectors.toSet());

        Set<String> inherited = inheritedMethodSubsignatures(type.binding);
        inherited.removeAll(declared);
        int total = declared.size() + inherited.size();
        return total == 0 ? 0.0 : (double) inherited.size() / total;
    }

    private Set<String> inheritedMethodSubsignatures(ITypeBinding binding) {
        Set<String> inherited = new HashSet<>();
        Set<String> overridden = new HashSet<>();
        ITypeBinding current = binding.getSuperclass();
        while (current != null && !OBJECT.equals(typeName(current))) {
            for (IMethodBinding method : current.getDeclaredMethods()) {
                if (isInheritable(method)) {
                    String signature = methodSubsignature(method);
                    if (!overridden.contains(signature)) {
                        inherited.add(signature);
                    }
                }
            }
            for (IMethodBinding method : current.getDeclaredMethods()) {
                overridden.add(methodSubsignature(method));
            }
            current = current.getSuperclass();
        }
        return inherited;
    }

    private boolean isInheritable(IMethodBinding method) {
        int modifiers = method.getModifiers();
        return !method.isConstructor()
                && !Modifier.isPrivate(modifiers)
                && !Modifier.isStatic(modifiers);
    }

    private double calculateCAM(TypeInfo type) {
        List<MethodInfo> methods = type.methods.stream()
                .filter(method -> !method.constructor)
                .collect(Collectors.toList());
        if (methods.isEmpty()) {
            return 0.0;
        }

        Set<String> allParameterTypes = new HashSet<>();
        for (MethodInfo method : methods) {
            allParameterTypes.addAll(method.parameterTypes);
        }
        if (allParameterTypes.isEmpty()) {
            return 0.0;
        }

        int sum = 0;
        for (MethodInfo method : methods) {
            Set<String> distinct = new HashSet<>(method.parameterTypes);
            sum += distinct.size();
        }
        return (double) sum / (methods.size() * allParameterTypes.size());
    }

    private int calculateIC(TypeInfo type) {
        Set<String> ancestors = new HashSet<>();
        for (MethodInfo method : type.methods) {
            ancestors.addAll(method.inheritedCallAncestors);
        }
        return ancestors.size();
    }

    private int calculateCBM(TypeInfo type) {
        Set<String> pairs = new HashSet<>();
        for (MethodInfo method : type.methods) {
            pairs.addAll(method.inheritedCallPairs);
        }
        return pairs.size();
    }

    private double calculateAMC(TypeInfo type) {
        if (type.methods.isEmpty()) {
            return 0.0;
        }
        return type.methods.stream().mapToInt(method -> method.loc).average().orElse(0.0);
    }

    private void calculateProjectLevelRelationships() {
        for (TypeInfo type : typesByName.values()) {
            type.superclassKey = typeKey(type.binding.getSuperclass());
            type.interfaceKeys.addAll(interfaceKeys(type.binding));
            addInheritanceDependencies(type);
            type.outgoingTypeKeys.remove(type.key);
        }

        for (TypeInfo type : typesByName.values()) {
            for (String dependency : type.outgoingTypeKeys) {
                TypeInfo target = typesByKey.get(dependency);
                if (target != null && !target.key.equals(type.key)) {
                    target.incomingTypeKeys.add(type.key);
                }
            }
        }

        for (TypeInfo type : typesByName.values()) {
            type.ce = projectDependencyCount(type.outgoingTypeKeys);
            type.ca = projectDependencyCount(type.incomingTypeKeys);
            Set<String> cbo = new HashSet<>();
            cbo.addAll(projectDependencies(type.outgoingTypeKeys));
            cbo.addAll(projectDependencies(type.incomingTypeKeys));
            cbo.remove(type.key);
            type.cbo = cbo.size();
            type.dit = calculateDIT(type);
        }

        for (TypeInfo possibleChild : typesByName.values()) {
            if (possibleChild.superclassKey != null) {
                TypeInfo parent = typesByKey.get(possibleChild.superclassKey);
                if (parent != null) {
                    parent.noc++;
                }
            }
            for (String interfaceKey : possibleChild.interfaceKeys) {
                TypeInfo parent = typesByKey.get(interfaceKey);
                if (parent != null) {
                    parent.noc++;
                }
            }
        }
    }

    private void addInheritanceDependencies(TypeInfo type) {
        if (type.superclassKey != null) {
            type.outgoingTypeKeys.add(type.superclassKey);
        }
        type.outgoingTypeKeys.addAll(type.interfaceKeys);
    }

    private Set<String> interfaceKeys(ITypeBinding binding) {
        Set<String> keys = new HashSet<>();
        for (ITypeBinding iface : binding.getInterfaces()) {
            String key = typeKey(iface);
            if (key != null) {
                keys.add(key);
            }
            keys.addAll(interfaceKeys(iface));
        }
        return keys;
    }

    private int projectDependencyCount(Set<String> keys) {
        return projectDependencies(keys).size();
    }

    private Set<String> projectDependencies(Set<String> keys) {
        return keys.stream()
                .filter(typesByKey::containsKey)
                .collect(Collectors.toSet());
    }

    private int calculateDIT(TypeInfo type) {
        if (type.isInterface()) {
            return 0;
        }

        int depth = 0;
        Set<String> visited = new HashSet<>();
        ITypeBinding current = type.binding;
        while (current != null) {
            ITypeBinding parent = current.getSuperclass();
            if (parent == null) {
                break;
            }
            String parentName = typeName(parent);
            if (parentName == null || OBJECT.equals(parentName)) {
                depth++;
                break;
            }
            String key = typeKey(parent);
            if (key != null && !visited.add(key)) {
                diagnostics.add("Inheritance cycle detected while calculating DIT for " + type.name);
                break;
            }
            depth++;
            current = parent;
        }
        return depth;
    }

    private PromiseMetricResult toMetricResult(TypeInfo type) {
        PromiseMetricResult result = new PromiseMetricResult(type.name);
        result.setWmc(type.wmc);
        result.setDit(type.dit);
        result.setNoc(type.noc);
        result.setCbo(type.cbo);
        result.setRfc(type.rfc);
        result.setLcom(type.lcom);
        result.setCa(type.ca);
        result.setCe(type.ce);
        result.setNpm(type.npm);
        result.setLcom3(type.lcom3);
        result.setLoc(type.loc);
        result.setDam(type.dam);
        result.setMoa(type.moa);
        result.setMfa(type.mfa);
        result.setCam(type.cam);
        result.setIc(type.ic);
        result.setCbm(type.cbm);
        result.setAmc(type.amc);
        result.setMaxCc(type.maxCc);
        result.setAvgCc(type.avgCc);
        result.setInterface(type.isInterface());
        result.setSuperclassName(type.superclassKey);
        result.setDependencies(new HashSet<>(type.outgoingTypeKeys));
        result.setMethodNames(type.methods.stream().map(method -> method.name).collect(Collectors.toSet()));
        return result;
    }

    private int calculateCyclomaticComplexity(MethodDeclaration method) {
        if (method.getBody() == null) {
            return 1;
        }
        ComplexityVisitor visitor = new ComplexityVisitor();
        method.getBody().accept(visitor);
        return visitor.complexity;
    }

    private int countSourceLines(TypeInfo type, ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        int count = 0;
        boolean hasCode = false;
        for (int index = start; index < end && index < type.commentFreeSource.length; index++) {
            char current = type.commentFreeSource[index];
            if (current == '\n' || current == '\r') {
                if (hasCode) {
                    count++;
                    hasCode = false;
                }
            } else if (!Character.isWhitespace(current)) {
                hasCode = true;
            }
        }
        if (hasCode) {
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private char[] stripComments(String source, CompilationUnit unit) {
        char[] chars = source.toCharArray();
        for (Object object : unit.getCommentList()) {
            ASTNode comment = (ASTNode) object;
            if (comment instanceof LineComment || comment instanceof BlockComment || comment instanceof Javadoc) {
                int start = comment.getStartPosition();
                int end = Math.min(chars.length, start + comment.getLength());
                for (int index = start; index < end; index++) {
                    if (chars[index] != '\n' && chars[index] != '\r') {
                        chars[index] = ' ';
                    }
                }
            }
        }
        return chars;
    }

    private boolean isAncestor(ITypeBinding child, ITypeBinding possibleAncestor) {
        String ancestorKey = typeKey(possibleAncestor);
        if (ancestorKey == null) {
            return false;
        }

        ITypeBinding current = child.getSuperclass();
        while (current != null) {
            String currentKey = typeKey(current);
            if (ancestorKey.equals(currentKey)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private boolean sameType(ITypeBinding first, ITypeBinding second) {
        String firstKey = typeKey(first);
        String secondKey = typeKey(second);
        return firstKey != null && firstKey.equals(secondKey);
    }

    private ITypeBinding safeErasure(ITypeBinding binding) {
        if (binding == null) {
            return null;
        }
        try {
            return binding.getErasure();
        } catch (RuntimeException ex) {
            diagnostics.add("Could not resolve type erasure: " + ex.getMessage());
            return binding;
        }
    }

    private String typeKey(ITypeBinding binding) {
        if (binding == null) {
            return null;
        }
        ITypeBinding declaration = binding.getTypeDeclaration();
        if (declaration == null) {
            declaration = binding;
        }
        String key = declaration.getKey();
        if (key != null && !key.isEmpty()) {
            return key;
        }
        String name = typeName(declaration);
        return name == null || name.isEmpty() ? null : name;
    }

    private String typeName(ITypeBinding binding) {
        if (binding == null) {
            return null;
        }
        ITypeBinding declaration = binding.getTypeDeclaration();
        if (declaration == null) {
            declaration = binding;
        }
        String qualified = declaration.getQualifiedName();
        if (qualified != null && !qualified.isEmpty()) {
            return qualified;
        }
        return declaration.getName();
    }

    private String methodKey(IMethodBinding binding) {
        if (binding == null) {
            return null;
        }
        IMethodBinding declaration = binding.getMethodDeclaration();
        String owner = typeKey(declaration.getDeclaringClass());
        return owner + "#" + methodSubsignature(declaration);
    }

    private String methodSubsignature(IMethodBinding binding) {
        if (binding == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(binding.isConstructor() ? "<init>" : binding.getName()).append('(');
        ITypeBinding[] parameters = binding.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            String key = typeKey(safeErasure(parameters[index]));
            builder.append(key == null ? "?" : key);
        }
        builder.append(')');
        return builder.toString();
    }

    private static class ComplexityVisitor extends ASTVisitor {
        int complexity = 1;

        @Override
        public boolean visit(TypeDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(IfStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(CatchClause node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(ConditionalExpression node) {
            complexity++;
            return true;
        }

        @Override
        public boolean visit(SwitchStatement node) {
            return true;
        }

        @Override
        public boolean visit(SwitchExpression node) {
            return true;
        }

        @Override
        public boolean visit(SwitchCase node) {
            if (!node.isDefault()) {
                complexity++;
            }
            return true;
        }

        @Override
        public boolean visit(InfixExpression node) {
            InfixExpression.Operator operator = node.getOperator();
            if (InfixExpression.Operator.CONDITIONAL_AND.equals(operator)
                    || InfixExpression.Operator.CONDITIONAL_OR.equals(operator)) {
                complexity += 1 + node.extendedOperands().size();
            }
            return true;
        }
    }

    private static class TypeInfo {
        final Path sourcePath;
        final CompilationUnit unit;
        final String source;
        final char[] commentFreeSource;
        final AbstractTypeDeclaration node;
        final ITypeBinding binding;
        final String key;
        final String name;
        final List<FieldInfo> fields = new ArrayList<>();
        final List<MethodInfo> methods = new ArrayList<>();
        final Set<String> outgoingTypeKeys = new HashSet<>();
        final Set<String> incomingTypeKeys = new HashSet<>();
        final Set<String> interfaceKeys = new HashSet<>();
        String superclassKey;
        int wmc;
        int dit;
        int noc;
        int cbo;
        int rfc;
        int lcom;
        int ca;
        int ce;
        int npm;
        int loc;
        int moa;
        int ic;
        int cbm;
        int maxCc;
        double avgCc;
        double lcom3;
        double dam;
        double mfa;
        double cam;
        double amc;

        TypeInfo(Path sourcePath, CompilationUnit unit, String source, char[] commentFreeSource,
                 AbstractTypeDeclaration node, ITypeBinding binding, String key, String name) {
            this.sourcePath = sourcePath;
            this.unit = unit;
            this.source = source;
            this.commentFreeSource = commentFreeSource;
            this.node = node;
            this.binding = binding;
            this.key = key;
            this.name = name;
        }

        String getName() {
            return name;
        }

        boolean isInterface() {
            return binding.isInterface();
        }
    }

    private static class FieldInfo {
        final String key;
        final FieldDeclaration declaration;
        final ITypeBinding type;

        FieldInfo(String key, FieldDeclaration declaration, ITypeBinding type) {
            this.key = key;
            this.declaration = declaration;
            this.type = type;
        }
    }

    private static class MethodInfo {
        final MethodDeclaration node;
        final IMethodBinding binding;
        final boolean implicit;
        final boolean constructor;
        final String name;
        final Set<String> accessedInstanceFields = new HashSet<>();
        final Set<IMethodBinding> invokedMethods = new HashSet<>();
        final Set<String> inheritedCallAncestors = new HashSet<>();
        final Set<String> inheritedCallPairs = new HashSet<>();
        final List<String> parameterTypes = new ArrayList<>();
        String signature;
        int complexity;
        int loc;
        boolean publicMethod;

        MethodInfo(MethodDeclaration node, IMethodBinding binding, boolean implicit) {
            this.node = node;
            this.binding = binding;
            this.implicit = implicit;
            this.constructor = node != null && node.isConstructor();
            this.name = binding == null ? "<init>" : binding.getName();
        }

        static MethodInfo implicitDefaultConstructor(TypeInfo owner) {
            MethodInfo info = new MethodInfo(null, null, true);
            info.signature = owner.key + "#<init>()";
            info.complexity = 1;
            info.loc = 1;
            info.publicMethod = Modifier.isPublic(owner.node.getModifiers());
            return info;
        }
    }
}
