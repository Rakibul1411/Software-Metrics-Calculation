package org.metrics.defectlab.analysis.promise.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.metrics.defectlab.analysis.promise.calculator.AmcPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.AvgCcPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.CaPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.CamPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.CbmPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.CboPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.CePromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.CyclomaticComplexityPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.DamPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.DitPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.IcPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.Lcom3PromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.LcomPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.LocPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.MaxCcPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.MfaPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.MoaPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.NocPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.NpmPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.RfcPromiseCalculator;
import org.metrics.defectlab.analysis.promise.calculator.WmcPromiseCalculator;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;
import org.metrics.defectlab.analysis.javaparser.JavaLanguageConfiguration;
import org.metrics.defectlab.analysis.javaparser.JavaParserConfigurationResolver;
import org.metrics.defectlab.analysis.javaparser.JdtProjectEnvironment;
import org.metrics.defectlab.analysis.javaparser.ResolvedJavaProject;

public class PromiseProjectAnalyzer {

    private static final int DEFAULT_BATCH_SIZE = 96;
    private static final int MAX_DIAGNOSTICS = 50;

    private final List<String> diagnostics = new ArrayList<>();
    private int suppressedDiagnostics;
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

        diagnostics.forEach(message ->
                System.err.println("PROMISE metric warning: " + message));
        if (suppressedDiagnostics > 0) {
            System.err.println("PROMISE metric warning: "
                    + suppressedDiagnostics
                    + " additional diagnostics were suppressed.");
        }
        return results;
    }

    private List<Path> collectJavaFiles(Collection<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        ProductionSourceSelector selector = new ProductionSourceSelector();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            files.addAll(selector.select(root));
        }
        files.sort(Path::compareTo);
        return files;
    }

    private void parseProject(List<Path> javaFiles, Collection<Path> requestedRoots) throws IOException {
        Path projectRoot = commonProjectRoot(requestedRoots);
        ResolvedJavaProject configuration = JavaParserConfigurationResolver.resolve(
                projectRoot, javaFiles, null);
        for (String diagnostic : configuration.getDiagnostics()) {
            diagnostic(diagnostic);
        }
        for (Path file : javaFiles) {
            JavaLanguageConfiguration fileConfiguration =
                    configuration.configurationFor(file);
            sourceByPath.put(file.toAbsolutePath().normalize(),
                    new String(Files.readAllBytes(file),
                            fileConfiguration.getCharset()));
        }
        List<String> sourcePaths = inferSourcePaths(javaFiles, requestedRoots);
        String[] classPath = JdtProjectEnvironment.collectJarClassPath(
                projectRoot, ignored -> true);
        for (Map.Entry<JavaLanguageConfiguration, List<Path>> entry
                : configuration.getFilesByConfiguration().entrySet()) {
            List<Path> configuredFiles = entry.getValue();
            int batchSize = configuredBatchSize();
            for (int start = 0; start < configuredFiles.size(); start += batchSize) {
                int end = Math.min(configuredFiles.size(), start + batchSize);
                parseBatch(
                        configuredFiles.subList(start, end),
                        entry.getKey(),
                        classPath,
                        sourcePaths);
            }
        }
    }

    private void parseBatch(
            List<Path> javaFiles,
            JavaLanguageConfiguration configuration,
            String[] classPath,
            List<String> sourcePaths) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(classPath, sourcePaths.toArray(new String[0]), null, true);
        parser.setCompilerOptions(configuration.compilerOptions());

        String[] fileNames = javaFiles.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toArray(String[]::new);
        String[] encodings = new String[fileNames.length];
        Arrays.fill(encodings, configuration.getCharset().name());

        parser.createASTs(fileNames, encodings, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                Path path = Path.of(sourceFilePath).toAbsolutePath().normalize();
                unitsByPath.put(path, ast);
                reportProblems(path, ast);
            }
        }, null);
    }

    private Path commonProjectRoot(Collection<Path> requestedRoots) {
        Path result = null;
        for (Path requestedRoot : requestedRoots) {
            if (requestedRoot == null || !Files.isDirectory(requestedRoot)) {
                continue;
            }
            Path normalized = requestedRoot.toAbsolutePath().normalize();
            if (result == null) {
                result = normalized;
                continue;
            }
            while (result != null && !normalized.startsWith(result)) {
                result = result.getParent();
            }
        }
        if (result == null) {
            throw new IllegalArgumentException(
                    "At least one PROMISE source directory is required.");
        }
        return result;
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
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        String source = sourceByPath.get(javaFile.toAbsolutePath().normalize());
        parser.setSource((source == null ? "" : source).toCharArray());
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        PackageDeclaration declaration = unit.getPackage();
        return declaration == null ? ""
                : declaration.getName().getFullyQualifiedName();
    }

    private int configuredBatchSize() {
        String configured = System.getProperty("promise.jdt.batchSize");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("PROMISE_JDT_BATCH_SIZE");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                return Math.max(16, Math.min(512,
                        Integer.parseInt(configured.trim())));
            } catch (NumberFormatException ignored) {
                // Use the memory-safe default.
            }
        }
        return DEFAULT_BATCH_SIZE;
    }

    private void reportProblems(Path path, CompilationUnit ast) {
        for (IProblem problem : ast.getProblems()) {
            if (problem.isError()) {
                diagnostic(path + ":" + problem.getSourceLineNumber()
                        + " " + problem.getMessage());
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
                    registerTopLevelType(path, unit, source, commentFreeSource, node);
                    return false;
                }

                @Override
                public boolean visit(EnumDeclaration node) {
                    registerTopLevelType(path, unit, source, commentFreeSource, node);
                    return false;
                }

                @Override
                public boolean visit(AnnotationTypeDeclaration node) {
                    registerTopLevelType(path, unit, source, commentFreeSource, node);
                    return false;
                }
            });
        }
    }

    private void registerTopLevelType(Path sourcePath, CompilationUnit unit, String source,
                                      char[] commentFreeSource, AbstractTypeDeclaration node) {
        if (node.getParent() instanceof CompilationUnit) {
            registerType(sourcePath, unit, source, commentFreeSource, node);
        }
    }

    private void registerType(Path sourcePath, CompilationUnit unit, String source,
                              char[] commentFreeSource, AbstractTypeDeclaration node) {
        ITypeBinding binding = node.resolveBinding();
        if (binding == null) {
            diagnostic("Unresolved type binding for "
                    + node.getName().getIdentifier() + " in " + sourcePath);
        }

        String key = typeKey(binding);
        String name = typeName(binding);
        if (name == null || name.isEmpty()) {
            name = fallbackTypeName(unit, node);
        }
        if (key == null || key.isEmpty()) {
            key = name;
        }
        if (name == null || name.isEmpty() || key == null || key.isEmpty()) {
            diagnostic("Could not identify top-level type "
                    + node.getName().getIdentifier() + " in " + sourcePath);
            return;
        }
        if (typesByKey.containsKey(key) || typesByName.containsKey(name)) {
            diagnostic("Duplicate type " + name + " in " + sourcePath
                    + " was ignored; the first production source was retained.");
            return;
        }

        TypeInfo info = new TypeInfo(sourcePath, unit, source, commentFreeSource, node, binding, key, name);
        typesByKey.put(key, info);
        typesByName.put(name, info);
    }

    private String fallbackTypeName(
            CompilationUnit unit,
            AbstractTypeDeclaration node) {
        PackageDeclaration declaration = unit.getPackage();
        String simpleName = node.getName().getIdentifier();
        return declaration == null ? simpleName
                : declaration.getName().getFullyQualifiedName() + "." + simpleName;
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
                diagnostic("Unresolved field type in " + type.name + " at line "
                        + type.unit.getLineNumber(field.getStartPosition()));
            }
            for (VariableDeclarationFragment fragment : (List<VariableDeclarationFragment>) field.fragments()) {
                IVariableBinding binding = fragment.resolveBinding();
                if (binding == null) {
                    diagnostic("Unresolved field binding for "
                            + type.name + "." + fragment.getName());
                }
                String fieldKey = binding == null
                        ? type.key + "#" + fragment.getName().getIdentifier()
                        : binding.getVariableDeclaration().getKey();
                FieldInfo info = new FieldInfo(fieldKey, field, declaredType);
                collectReferencedTypeKeys(declaredType, info.referencedTypeKeys);
                type.fields.add(info);
                addTypeDependency(type, declaredType);
            }
        }
    }

    private void collectMethods(TypeInfo type) {
        boolean hasConstructor = false;
        for (Object declaration : type.node.bodyDeclarations()) {
            if (!(declaration instanceof MethodDeclaration)) {
                continue;
            }

            MethodDeclaration method = (MethodDeclaration) declaration;
            IMethodBinding binding = method.resolveBinding();
            if (binding == null) {
                diagnostic("Unresolved method binding for "
                        + type.name + "." + method.getName());
            }

            IMethodBinding declarationBinding = binding == null
                    ? null : binding.getMethodDeclaration();
            MethodInfo info = new MethodInfo(method, declarationBinding, false);
            info.signature = binding == null
                    ? syntacticMethodKey(type, method) : methodKey(binding);
            info.complexity = CyclomaticComplexityPromiseCalculator.calculate(method);
            info.loc = LocPromiseCalculator.countSourceLines(type, method);
            info.publicMethod = isPublicMethod(type, method, binding);
            if (binding != null) {
                info.parameterTypes.addAll(parameterTypeKeys(binding));
            }
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
        if (Modifier.isPublic(method.getModifiers())
                || (binding != null && Modifier.isPublic(binding.getModifiers()))) {
            return true;
        }
        return type.isInterface() && !Modifier.isPrivate(method.getModifiers());
    }

    private String syntacticMethodKey(TypeInfo type, MethodDeclaration method) {
        StringBuilder result = new StringBuilder(type.key).append('#');
        result.append(method.isConstructor()
                ? "<init>" : method.getName().getIdentifier()).append('(');
        for (int index = 0; index < method.parameters().size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append('?');
        }
        return result.append(')').toString();
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
                    diagnostic("Unresolved method call " + node.getName() + " in " + type.name
                            + " at line " + type.unit.getLineNumber(node.getStartPosition()));
                } else {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                    addInvokedMethodKey(methodInfo, binding);
                    addMethodDependency(type, binding);
                    collectInheritedCall(type, methodInfo, binding);
                }
                return true;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                IMethodBinding binding = node.resolveMethodBinding();
                if (binding == null) {
                    diagnostic("Unresolved super method call " + node.getName() + " in " + type.name
                            + " at line " + type.unit.getLineNumber(node.getStartPosition()));
                } else {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                    addInvokedMethodKey(methodInfo, binding);
                    addMethodDependency(type, binding);
                    collectInheritedCall(type, methodInfo, binding);
                }
                return true;
            }

            @Override
            public boolean visit(ClassInstanceCreation node) {
                IMethodBinding constructor = node.resolveConstructorBinding();
                if (constructor == null) {
                    diagnostic("Unresolved constructor call in " + type.name + " at line "
                            + type.unit.getLineNumber(node.getStartPosition()));
                } else {
                    methodInfo.invokedMethods.add(constructor.getMethodDeclaration());
                    addInvokedMethodKey(methodInfo, constructor);
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
                    addInvokedMethodKey(methodInfo, binding);
                } else {
                    diagnostic("Unresolved this-constructor call in " + type.name + " at line "
                            + type.unit.getLineNumber(node.getStartPosition()));
                }
                return true;
            }

            @Override
            public boolean visit(SuperConstructorInvocation node) {
                IMethodBinding binding = node.resolveConstructorBinding();
                if (binding != null) {
                    methodInfo.invokedMethods.add(binding.getMethodDeclaration());
                    addInvokedMethodKey(methodInfo, binding);
                    addMethodDependency(type, binding);
                } else {
                    diagnostic("Unresolved super-constructor call in " + type.name + " at line "
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
        if (sameType(type.binding, declaringClass)) {
            methodInfo.accessedFields.add(variable.getKey());
            if (!Modifier.isStatic(variable.getModifiers())) {
                methodInfo.accessedInstanceFields.add(variable.getKey());
            }
        }
        addTypeDependency(type, declaringClass);
        addTypeDependency(type, variable.getType());
    }

    private void addInvokedMethodKey(MethodInfo methodInfo, IMethodBinding binding) {
        String key = methodKey(binding);
        if (key != null) {
            methodInfo.invokedMethodKeys.add(key);
        }
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
        if (type.binding == null || declaringClass == null
                || sameType(type.binding, declaringClass)) {
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
        type.loc = LocPromiseCalculator.calculate(type);
        type.wmc = WmcPromiseCalculator.calculate(type);
        type.rfc = RfcPromiseCalculator.calculate(type);
        type.npm = NpmPromiseCalculator.calculate(type);
        type.lcom = LcomPromiseCalculator.calculate(type);
        type.lcom3 = Lcom3PromiseCalculator.calculate(type);
        type.dam = DamPromiseCalculator.calculate(type);
        type.moa = MoaPromiseCalculator.calculate(type, typesByKey);
        type.mfa = MfaPromiseCalculator.calculate(type);
        type.cam = CamPromiseCalculator.calculate(type);
        type.ic = IcPromiseCalculator.calculate(type);
        type.cbm = CbmPromiseCalculator.calculate(type);
        type.amc = AmcPromiseCalculator.calculate(type);
        type.maxCc = MaxCcPromiseCalculator.calculate(type);
        type.avgCc = AvgCcPromiseCalculator.calculate(type);
    }

    private void calculateProjectLevelRelationships() {
        for (TypeInfo type : typesByName.values()) {
            if (type.binding != null) {
                type.superclassKey = typeKey(type.binding.getSuperclass());
                type.interfaceKeys.addAll(interfaceKeys(type.binding));
            } else {
                type.superclassKey = syntacticSuperclassKey(type);
            }
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
            type.ce = CePromiseCalculator.calculate(type, typesByKey);
            type.ca = CaPromiseCalculator.calculate(type, typesByKey);
            type.cbo = CboPromiseCalculator.calculate(type, typesByKey);
            type.dit = DitPromiseCalculator.calculate(type, diagnostics);
            type.noc = NocPromiseCalculator.calculate(type, typesByKey);
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
        if (binding == null) {
            return keys;
        }
        for (ITypeBinding iface : binding.getInterfaces()) {
            String key = typeKey(iface);
            if (key != null) {
                keys.add(key);
            }
            keys.addAll(interfaceKeys(iface));
        }
        return keys;
    }

    private String syntacticSuperclassKey(TypeInfo type) {
        if (!(type.node instanceof TypeDeclaration)) {
            return null;
        }
        TypeDeclaration declaration = (TypeDeclaration) type.node;
        if (declaration.getSuperclassType() == null) {
            return type.isInterface() ? null : "java.lang.Object";
        }
        ITypeBinding resolved = declaration.getSuperclassType().resolveBinding();
        String key = typeKey(resolved);
        if (key != null) {
            return key;
        }
        String writtenName = declaration.getSuperclassType().toString();
        if (typesByName.containsKey(writtenName)) {
            return typesByName.get(writtenName).key;
        }
        String packageName = type.unit.getPackage() == null ? ""
                : type.unit.getPackage().getName().getFullyQualifiedName();
        String qualified = packageName.isEmpty()
                ? writtenName : packageName + "." + writtenName;
        return typesByName.containsKey(qualified)
                ? typesByName.get(qualified).key : writtenName;
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
        if (child == null || possibleAncestor == null) {
            return false;
        }
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
            diagnostic("Could not resolve type erasure: " + ex.getMessage());
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
        if (owner == null) {
            diagnostic("Unresolved declaring type for method " + binding.getName());
            return null;
        }
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

    private void diagnostic(String message) {
        if (diagnostics.size() < MAX_DIAGNOSTICS) {
            diagnostics.add(message);
        } else {
            suppressedDiagnostics++;
        }
    }

    public static class TypeInfo {
        public final Path sourcePath;
        public final CompilationUnit unit;
        public final String source;
        public final char[] commentFreeSource;
        public final AbstractTypeDeclaration node;
        public final ITypeBinding binding;
        public final String key;
        public final String name;
        public final List<FieldInfo> fields = new ArrayList<>();
        public final List<MethodInfo> methods = new ArrayList<>();
        public final Set<String> outgoingTypeKeys = new HashSet<>();
        public final Set<String> incomingTypeKeys = new HashSet<>();
        public final Set<String> interfaceKeys = new HashSet<>();
        public String superclassKey;
        public int wmc;
        public int dit;
        public int noc;
        public int cbo;
        public int rfc;
        public int lcom;
        public int ca;
        public int ce;
        public int npm;
        public int loc;
        public int moa;
        public int ic;
        public int cbm;
        public int maxCc;
        public double avgCc;
        public double lcom3;
        public double dam;
        public double mfa;
        public double cam;
        public double amc;

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

        public String getName() {
            return name;
        }

        public boolean isInterface() {
            return binding == null
                    ? node instanceof TypeDeclaration
                        && ((TypeDeclaration) node).isInterface()
                    : binding.isInterface();
        }
    }

    public static class FieldInfo {
        public final String key;
        public final FieldDeclaration declaration;
        public final ITypeBinding type;
        public final Set<String> referencedTypeKeys = new HashSet<>();

        FieldInfo(String key, FieldDeclaration declaration, ITypeBinding type) {
            this.key = key;
            this.declaration = declaration;
            this.type = type;
        }
    }

    public static class MethodInfo {
        public final MethodDeclaration node;
        public final IMethodBinding binding;
        public final boolean implicit;
        public final boolean constructor;
        public final String name;
        public final Set<String> accessedFields = new HashSet<>();
        public final Set<String> accessedInstanceFields = new HashSet<>();
        public final Set<IMethodBinding> invokedMethods = new HashSet<>();
        public final Set<String> invokedMethodKeys = new HashSet<>();
        public final Set<String> inheritedCallAncestors = new HashSet<>();
        public final Set<String> inheritedCallPairs = new HashSet<>();
        public final List<String> parameterTypes = new ArrayList<>();
        public String signature;
        public int complexity;
        public int loc;
        public boolean publicMethod;

        MethodInfo(MethodDeclaration node, IMethodBinding binding, boolean implicit) {
            this.node = node;
            this.binding = binding;
            this.implicit = implicit;
            this.constructor = node != null && node.isConstructor();
            this.name = binding != null
                    ? binding.getName()
                    : node == null || node.isConstructor()
                        ? "<init>" : node.getName().getIdentifier();
        }

        static MethodInfo implicitDefaultConstructor(TypeInfo owner) {
            MethodInfo info = new MethodInfo(null, null, true);
            info.signature = owner.key + "#<init>()";
            info.complexity = 0;
            info.loc = 1;
            info.publicMethod = Modifier.isPublic(owner.node.getModifiers());
            return info;
        }
    }
}
