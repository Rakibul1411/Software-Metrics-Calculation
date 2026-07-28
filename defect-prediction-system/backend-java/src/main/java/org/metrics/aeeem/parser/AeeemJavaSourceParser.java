package org.metrics.aeeem.parser;

import java.io.File;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.metrics.aeeem.calculator.AeeemStaticMetricsCalculator;
import org.metrics.aeeem.model.AeeemMetricResult;

public final class AeeemJavaSourceParser {

    private static final int MAX_DIAGNOSTICS = 5;
    private static final int DEFAULT_BATCH_SIZE = 96;
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "(?m)^\\s*package\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(?:\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*)\\s*;");

    private AeeemJavaSourceParser() {
    }

    public static List<AeeemMetricResult> parseProject(Path projectRoot) throws IOException {
        return parseProject(projectRoot, projectRoot);
    }

    /**
     * Parses only {@code sourceScope}, while keeping repository-relative paths
     * anchored at {@code projectRoot}. This prevents sibling modules from
     * becoming output rows without breaking Git file-to-class matching.
     */
    public static List<AeeemMetricResult> parseProject(
            Path projectRoot,
            Path sourceScope) throws IOException {
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
        String sourceVersion = inferSourceVersion(javaFiles);
        String[] classPath = classPath(normalizedRoot);
        String[] sourceRoots = inferSourceRoots(javaFiles).toArray(new String[0]);
        List<AeeemMetricResult> results = new ArrayList<>();
        int[] diagnosticCounts = new int[2];
        int batchSize = configuredBatchSize();
        for (int start = 0; start < javaFiles.size(); start += batchSize) {
            int end = Math.min(javaFiles.size(), start + batchSize);
            parseBatch(normalizedRoot, javaFiles.subList(start, end), classPath, sourceRoots,
                    sourceVersion, results, diagnosticCounts);
        }
        if (diagnosticCounts[1] > 0) {
            System.err.println("AEEEM JDT warning: " + diagnosticCounts[1]
                    + " additional diagnostics were suppressed for this snapshot.");
        }
        return results.stream()
                .filter(value -> value.getFullyQualifiedName() != null)
                .sorted(Comparator.comparing(AeeemMetricResult::getFullyQualifiedName))
                .collect(Collectors.toList());
    }

    private static void parseBatch(
            Path projectRoot,
            List<Path> javaFiles,
            String[] classPath,
            String[] sourceRoots,
            String sourceVersion,
            List<AeeemMetricResult> results,
            int[] diagnosticCounts) throws IOException {
        Map<Path, String> sourceByPath = new LinkedHashMap<>();
        for (Path file : javaFiles) {
            sourceByPath.put(file.toAbsolutePath().normalize(), readSource(file));
        }

        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(classPath, sourceRoots, null, true);
        parser.setCompilerOptions(compilerOptions(sourceVersion));

        String[] fileNames = javaFiles.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toArray(String[]::new);
        String[] encodings = new String[fileNames.length];
        Arrays.fill(encodings, StandardCharsets.UTF_8.name());
        parser.createASTs(fileNames, encodings, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit unit) {
                Path sourcePath = java.nio.file.Paths.get(sourceFilePath).toAbsolutePath().normalize();
                reportProblems(sourcePath, unit, diagnosticCounts);
                String source = sourceByPath.get(sourcePath);
                unit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
                    @Override
                    public boolean visit(TypeDeclaration node) {
                        if (node.getParent() instanceof TypeDeclarationStatement) {
                            return false;
                        }
                        addResult(unit, node, sourcePath, projectRoot, source, results, diagnosticCounts);
                        return true;
                    }

                    @Override
                    public boolean visit(EnumDeclaration node) {
                        if (node.getParent() instanceof TypeDeclarationStatement) {
                            return false;
                        }
                        addResult(unit, node, sourcePath, projectRoot, source, results, diagnosticCounts);
                        return true;
                    }
                });
            }
        }, null);
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

    private static List<String> inferSourceRoots(Collection<Path> files) throws IOException {
        Set<String> roots = new LinkedHashSet<>();
        for (Path file : files) {
            Path root = sourceRootFromPackage(file, readSource(file));
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

    private static String[] classPath(Path projectRoot) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        String runtimeClassPath = System.getProperty("java.class.path", "");
        if (!runtimeClassPath.isEmpty()) {
            entries.addAll(Arrays.asList(runtimeClassPath.split(File.pathSeparator)));
        }
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(path -> !ProductionSourceSelector.isExcludedPath(projectRoot, path))
                    .filter(AeeemJavaSourceParser::isValidJar)
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .forEach(entries::add);
        }
        return entries.toArray(new String[0]);
    }

    private static String inferSourceVersion(List<Path> javaFiles) throws IOException {
        List<String> sources = new ArrayList<>();
        for (Path file : javaFiles) {
            sources.add(readSource(file));
            if (sources.size() == 25) {
                break;
            }
        }
        String latest = JavaCore.latestSupportedJavaVersion();
        List<String> candidates = Arrays.asList(JavaCore.VERSION_1_4, JavaCore.VERSION_1_5,
                JavaCore.VERSION_1_8, JavaCore.VERSION_11, JavaCore.VERSION_17, latest);
        String selected = latest;
        int bestErrors = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int errors = 0;
            int inspected = 0;
            for (String source : sources) {
                ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                parser.setSource(source.toCharArray());
                parser.setCompilerOptions(compilerOptions(candidate));
                CompilationUnit unit = (CompilationUnit) parser.createAST(null);
                for (IProblem problem : unit.getProblems()) {
                    if (problem.isError()) {
                        errors++;
                    }
                }
                if (errors > bestErrors) {
                    break;
                }
                if (++inspected == sources.size()) {
                    break;
                }
            }
            if (errors <= bestErrors) {
                bestErrors = errors;
                selected = candidate;
            }
        }
        return selected;
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

    private static String readSource(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Map<String, String> compilerOptions(String sourceVersion) {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_SOURCE, sourceVersion);
        options.put(JavaCore.COMPILER_COMPLIANCE, sourceVersion);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, sourceVersion);
        boolean latest = JavaCore.latestSupportedJavaVersion().equals(sourceVersion);
        options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES,
                latest ? JavaCore.ENABLED : JavaCore.DISABLED);
        options.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
        return options;
    }

    private static boolean isValidJar(Path path) {
        try (JarFile ignored = new JarFile(path.toFile())) {
            return true;
        } catch (IOException exception) {
            return false;
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
