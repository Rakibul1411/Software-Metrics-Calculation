package org.metrics.aeeem.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.metrics.aeeem.calculator.AeeemStaticMetricsCalculator;
import org.metrics.aeeem.history.AeeemBenchmarkProfile;
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.jdt.JavaLanguageConfiguration;
import org.metrics.jdt.JavaParserConfigurationResolver;
import org.metrics.jdt.JdtProjectEnvironment;
import org.metrics.jdt.ResolvedJavaProject;

public final class AeeemJavaSourceParser {

    private static final int MAX_DIAGNOSTICS = 5;
    private static final int DEFAULT_BATCH_SIZE = 96;
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "(?m)^\\s*package\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(?:\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*)\\s*;");

    private AeeemJavaSourceParser() {
    }

    public static List<AeeemMetricResult> parseProject(Path projectRoot) throws IOException {
        return parseProject(projectRoot, projectRoot, AeeemBenchmarkProfile.CURRENT);
    }

    /**
     * Parses only {@code sourceScope}, while keeping repository-relative paths
     * anchored at {@code projectRoot}. This prevents sibling modules from
     * becoming output rows without breaking Git file-to-class matching.
     */
    public static List<AeeemMetricResult> parseProject(
            Path projectRoot,
            Path sourceScope) throws IOException {
        return parseProject(projectRoot, sourceScope, AeeemBenchmarkProfile.CURRENT);
    }

    public static List<AeeemMetricResult> parseProject(
            Path projectRoot,
            Path sourceScope,
            AeeemBenchmarkProfile profile) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedScope = sourceScope.toAbsolutePath().normalize();
        if (!normalizedScope.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "AEEEM source scope must be inside the Git repository.");
        }
        if (!Files.isDirectory(normalizedScope)) {
            return java.util.Collections.emptyList();
        }
        List<Path> javaFiles = ProductionSourceSelector.collectJavaFiles(normalizedScope);
        if (javaFiles.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        AeeemBenchmarkProfile effectiveProfile = profile == null
                ? AeeemBenchmarkProfile.CURRENT : profile;
        ResolvedJavaProject configuration = JavaParserConfigurationResolver.resolve(
                normalizedRoot, javaFiles, languageFallback(effectiveProfile));
        for (String diagnostic : configuration.getDiagnostics()) {
            System.err.println("AEEEM JDT configuration warning: " + diagnostic);
        }
        String[] classPath = JdtProjectEnvironment.collectJarClassPath(
                normalizedRoot,
                path -> !ProductionSourceSelector.isExcludedPath(
                        normalizedRoot, path));
        String[] sourceRoots = inferSourceRoots(javaFiles, configuration)
                .toArray(new String[0]);
        List<AeeemMetricResult> results = new ArrayList<>();
        int[] diagnosticCounts = new int[2];
        int batchSize = configuredBatchSize();
        for (Map.Entry<JavaLanguageConfiguration, List<Path>> entry
                : configuration.getFilesByConfiguration().entrySet()) {
            List<Path> configuredFiles = entry.getValue();
            for (int start = 0; start < configuredFiles.size(); start += batchSize) {
                int end = Math.min(configuredFiles.size(), start + batchSize);
                parseBatch(
                        normalizedRoot,
                        configuredFiles.subList(start, end),
                        classPath,
                        sourceRoots,
                        entry.getKey(),
                        effectiveProfile.isBenchmark(),
                        results,
                        diagnosticCounts);
            }
        }
        if (diagnosticCounts[1] > 0) {
            System.err.println("AEEEM JDT warning: " + diagnosticCounts[1]
                    + " additional diagnostics were suppressed for this snapshot.");
        }
        Map<String, AeeemMetricResult> unique = new LinkedHashMap<>();
        results.stream()
                .filter(value -> value.getFullyQualifiedName() != null)
                .sorted(Comparator.comparing(AeeemMetricResult::getFullyQualifiedName))
                .forEach(value -> unique.putIfAbsent(
                        value.getFullyQualifiedName(), value));
        return new ArrayList<>(unique.values());
    }

    private static void parseBatch(
            Path projectRoot,
            List<Path> javaFiles,
            String[] classPath,
            String[] sourceRoots,
            JavaLanguageConfiguration configuration,
            boolean topLevelOnly,
            List<AeeemMetricResult> results,
            int[] diagnosticCounts) throws IOException {
        Map<Path, String> sourceByPath = new LinkedHashMap<>();
        for (Path file : javaFiles) {
            sourceByPath.put(file.toAbsolutePath().normalize(),
                    readSource(file, configuration));
        }

        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(classPath, sourceRoots, null, true);
        parser.setCompilerOptions(configuration.compilerOptions());

        String[] fileNames = javaFiles.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toArray(String[]::new);
        String[] encodings = new String[fileNames.length];
        Arrays.fill(encodings, configuration.getCharset().name());
        parser.createASTs(fileNames, encodings, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit unit) {
                Path sourcePath = java.nio.file.Paths.get(sourceFilePath).toAbsolutePath().normalize();
                reportProblems(sourcePath, unit, diagnosticCounts);
                String source = sourceByPath.get(sourcePath);
                unit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
                    @Override
                    public boolean visit(TypeDeclaration node) {
                        if (!shouldInclude(node, topLevelOnly)) {
                            return false;
                        }
                        addResult(unit, node, sourcePath, projectRoot, source, results, diagnosticCounts);
                        return true;
                    }

                    @Override
                    public boolean visit(EnumDeclaration node) {
                        if (!shouldInclude(node, topLevelOnly)) {
                            return false;
                        }
                        addResult(unit, node, sourcePath, projectRoot, source, results, diagnosticCounts);
                        return true;
                    }

                    @Override
                    public boolean visit(AnnotationTypeDeclaration node) {
                        if (!shouldInclude(node, topLevelOnly)) {
                            return false;
                        }
                        addResult(unit, node, sourcePath, projectRoot, source,
                                results, diagnosticCounts);
                        return true;
                    }
                });
            }
        }, null);
    }

    private static boolean shouldInclude(
            AbstractTypeDeclaration type,
            boolean topLevelOnly) {
        if (topLevelOnly) {
            return type.getParent() instanceof CompilationUnit;
        }
        return !(type.getParent() instanceof TypeDeclarationStatement);
    }

    private static void addResult(CompilationUnit unit, AbstractTypeDeclaration type, Path sourcePath,
                                  Path projectRoot, String source, List<AeeemMetricResult> results,
                                  int[] diagnosticCounts) {
        if (type.resolveBinding() == null) {
            reportDiagnostic("unresolved type binding for " + type.getName().getIdentifier()
                    + " in " + sourcePath, diagnosticCounts);
        }
        AeeemMetricResult metrics = AeeemStaticMetricsCalculator.calculateAeeemForType(unit, type, source);
        metrics.setSourcePath(projectRoot.toAbsolutePath().normalize().relativize(sourcePath).toString());
        results.add(metrics);
    }

    private static List<String> inferSourceRoots(
            Collection<Path> files,
            ResolvedJavaProject configuration) throws IOException {
        Set<String> roots = new LinkedHashSet<>();
        for (Path file : files) {
            Path root = sourceRootFromPackage(
                    file,
                    readSource(file, configuration.configurationFor(file)));
            if (root != null) {
                roots.add(root.toString());
            }
        }
        return new ArrayList<>(roots);
    }

    private static Path sourceRootFromPackage(Path file, String source) {
        Matcher matcher = PACKAGE_DECLARATION.matcher(source);
        if (matcher.find()) {
            String[] packageSegments = matcher.group(1).split("\\.");
            Path root = file.toAbsolutePath().normalize().getParent();
            boolean matchesPath = true;
            for (int index = packageSegments.length - 1; index >= 0; index--) {
                if (root == null || root.getFileName() == null
                        || !packageSegments[index].equals(root.getFileName().toString())) {
                    matchesPath = false;
                    break;
                }
                root = root.getParent();
            }
            if (matchesPath && root != null) {
                return root;
            }
        }
        return conventionalSourceRoot(file);
    }

    private static Path conventionalSourceRoot(Path file) {
        Path current = file.toAbsolutePath().normalize().getParent();
        Path srcFallback = null;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && name.toString().equalsIgnoreCase("java")) {
                return current;
            }
            if (name != null && name.toString().equalsIgnoreCase("src") && srcFallback == null) {
                srcFallback = current;
            }
            current = current.getParent();
        }
        return srcFallback == null ? file.toAbsolutePath().normalize().getParent() : srcFallback;
    }

    private static int configuredBatchSize() {
        String configured = System.getProperty("aeeem.jdt.batchSize");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("AEEEM_JDT_BATCH_SIZE");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                return Math.max(16, Math.min(512, Integer.parseInt(configured.trim())));
            } catch (NumberFormatException ignored) {
                // Use the memory-safe default.
            }
        }
        return DEFAULT_BATCH_SIZE;
    }

    private static String readSource(
            Path path,
            JavaLanguageConfiguration configuration) throws IOException {
        return new String(Files.readAllBytes(path), configuration.getCharset());
    }

    private static JavaLanguageConfiguration languageFallback(
            AeeemBenchmarkProfile profile) {
        switch (profile) {
            case JDT:
            case PDE:
            case EQ:
                return new JavaLanguageConfiguration(
                        "1.3", "1.4", "1.2", StandardCharsets.UTF_8,
                        "AEEEM " + profile.getId() + " benchmark fallback");
            case ML:
                return JavaLanguageConfiguration.uniform(
                        "1.5", "AEEEM ML benchmark fallback");
            case LC:
                return JavaLanguageConfiguration.uniform(
                        "1.4", "AEEEM LC benchmark fallback");
            case CURRENT:
            default:
                return null;
        }
    }

    private static void reportProblems(Path file, CompilationUnit unit, int[] diagnosticCounts) {
        for (IProblem problem : unit.getProblems()) {
            if (problem.isError()) {
                reportDiagnostic(file + ":" + problem.getSourceLineNumber() + " " + problem.getMessage(),
                        diagnosticCounts);
            }
        }
    }

    private static void reportDiagnostic(String message, int[] diagnosticCounts) {
        if (diagnosticCounts[0] < MAX_DIAGNOSTICS) {
            System.err.println("AEEEM JDT warning: " + message);
            diagnosticCounts[0]++;
        } else {
            diagnosticCounts[1]++;
        }
    }
}
