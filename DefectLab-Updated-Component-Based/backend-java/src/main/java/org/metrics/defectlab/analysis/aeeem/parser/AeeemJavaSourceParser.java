package org.metrics.defectlab.analysis.aeeem.parser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.metrics.defectlab.analysis.aeeem.calculator.AeeemStaticMetricsCalculator;
import org.metrics.defectlab.analysis.aeeem.history.AeeemBenchmarkProfile;
import org.metrics.defectlab.analysis.aeeem.model.AeeemMetricResult;
import org.metrics.defectlab.analysis.javaparser.JavaLanguageConfiguration;
import org.metrics.defectlab.analysis.javaparser.JavaParserConfigurationResolver;
import org.metrics.defectlab.analysis.javaparser.JdtProjectEnvironment;
import org.metrics.defectlab.analysis.javaparser.ResolvedJavaProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AeeemJavaSourceParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeeemJavaSourceParser.class);
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
        return parseProject(projectRoot, sourceScope, profile, null);
    }

    public static List<AeeemMetricResult> parseProject(
            Path projectRoot,
            Path sourceScope,
            AeeemBenchmarkProfile profile,
            SourceFileCache cache) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedScope = sourceScope.toAbsolutePath().normalize();
        if (!normalizedScope.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "AEEEM source scope must be inside the Git repository.");
        }
        if (!Files.isDirectory(normalizedScope)) {
            return Collections.emptyList();
        }
        List<Path> javaFiles = ProductionSourceSelector.collectJavaFiles(normalizedScope);
        if (javaFiles.isEmpty()) {
            return Collections.emptyList();
        }
        AeeemBenchmarkProfile effectiveProfile = profile == null
                ? AeeemBenchmarkProfile.CURRENT : profile;

        List<AeeemMetricResult> results = new ArrayList<>();
        List<Path> filesToParse = new ArrayList<>();
        if (cache != null) {
            for (Path file : javaFiles) {
                List<AeeemMetricResult> cached = cache.get(file);
                if (cached != null) {
                    results.addAll(cached);
                } else {
                    filesToParse.add(file);
                }
            }
        } else {
            filesToParse.addAll(javaFiles);
        }

        if (!filesToParse.isEmpty()) {
            JavaLanguageConfiguration fallbackLanguageConfig = languageFallback(effectiveProfile);
            ResolvedJavaProject configuration = JavaParserConfigurationResolver.resolve(
                    normalizedRoot, filesToParse, fallbackLanguageConfig);
            for (String diagnostic : configuration.getDiagnostics()) {
                LOGGER.warn("AEEEM JDT configuration warning: {}", diagnostic);
            }
            String[] classPath = JdtProjectEnvironment.collectJarClassPath(
                    normalizedRoot,
                    path -> !ProductionSourceSelector.isExcludedPath(
                            normalizedRoot, path));
            String[] sourceRoots = inferSourceRoots(javaFiles, configuration, fallbackLanguageConfig)
                    .toArray(new String[0]);
            int[] diagnosticCounts = new int[2];
            int batchSize = configuredBatchSize();
            for (Map.Entry<JavaLanguageConfiguration, List<Path>> entry
                    : configuration.getFilesByConfiguration().entrySet()) {
                List<Path> configuredFiles = entry.getValue();
                for (int start = 0; start < configuredFiles.size(); start += batchSize) {
                    int end = Math.min(configuredFiles.size(), start + batchSize);
                    List<Path> batch = configuredFiles.subList(start, end);
                    List<AeeemMetricResult> batchResults = new ArrayList<>();
                    parseBatch(
                            normalizedRoot,
                            batch,
                            classPath,
                            sourceRoots,
                            entry.getKey(),
                            effectiveProfile.isBenchmark(),
                            batchResults,
                            diagnosticCounts);
                    results.addAll(batchResults);
                    if (cache != null) {
                        Map<Path, List<AeeemMetricResult>> metricsByFilePath = new HashMap<>();
                        for (AeeemMetricResult metricResult : batchResults) {
                            if (metricResult.getSourcePath() != null) {
                                Path filePath = normalizedRoot.resolve(metricResult.getSourcePath()).normalize();
                                metricsByFilePath.computeIfAbsent(filePath, key -> new ArrayList<>()).add(metricResult);
                            }
                        }
                        for (Path sourceFile : batch) {
                            List<AeeemMetricResult> fileMetrics = metricsByFilePath.getOrDefault(sourceFile, Collections.emptyList());
                            cache.put(sourceFile, fileMetrics);
                        }
                    }
                }
            }
            if (diagnosticCounts[1] > 0) {
                LOGGER.warn("AEEEM JDT warning: {} additional diagnostics were suppressed for this snapshot.",
                        diagnosticCounts[1]);
            }
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
            ResolvedJavaProject configuration,
            JavaLanguageConfiguration fallback) throws IOException {
        Set<String> roots = new LinkedHashSet<>();
        Set<Path> visitedDirectories = new HashSet<>();
        for (Path file : files) {
            Path parent = file.getParent();
            if (parent != null && visitedDirectories.contains(parent)) {
                continue;
            }
            JavaLanguageConfiguration config = configuration != null
                    ? configuration.configurationFor(file, fallback)
                    : fallback;
            Path root = sourceRootFromPackage(
                    file,
                    readSource(file, config));
            if (root != null) {
                roots.add(root.toString());
                if (parent != null) {
                    visitedDirectories.add(parent);
                }
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
        Charset charset = (configuration != null && configuration.getCharset() != null)
                ? configuration.getCharset()
                : StandardCharsets.UTF_8;
        try {
            return Files.readString(path, charset);
        } catch (Exception exception) {
            try {
                return Files.readString(path, StandardCharsets.ISO_8859_1);
            } catch (Exception ignored) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
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
            LOGGER.warn("AEEEM JDT warning: {}", message);
            diagnosticCounts[0]++;
        } else {
            diagnosticCounts[1]++;
        }
    }

    /**
     * In-memory cache for parsed file metrics across bi-weekly snapshots.
     * Caches AST results by file path, last-modified timestamp, and file size,
     * avoiding re-parsing unchanged files across consecutive Git snapshots.
     */
    public static final class SourceFileCache {
        private final Map<Path, CacheEntry> entries = new LinkedHashMap<>();

        public List<AeeemMetricResult> get(Path file) {
            CacheEntry entry = entries.get(file);
            if (entry == null || !entry.isValid(file)) {
                return null;
            }
            List<AeeemMetricResult> copies = new ArrayList<>(entry.results.size());
            for (AeeemMetricResult result : entry.results) {
                copies.add(result.copy());
            }
            return copies;
        }

        public void put(Path file, List<AeeemMetricResult> results) {
            try {
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                long size = Files.size(file);
                List<AeeemMetricResult> copies = new ArrayList<>(results.size());
                for (AeeemMetricResult result : results) {
                    copies.add(result.copy());
                }
                entries.put(file, new CacheEntry(lastModified, size, copies));
            } catch (IOException ignored) {
            }
        }

        public void clear() {
            entries.clear();
        }

        private static final class CacheEntry {
            final long lastModified;
            final long size;
            final List<AeeemMetricResult> results;

            CacheEntry(long lastModified, long size, List<AeeemMetricResult> results) {
                this.lastModified = lastModified;
                this.size = size;
                this.results = results;
            }

            boolean isValid(Path file) {
                try {
                    return Files.exists(file)
                            && Files.getLastModifiedTime(file).toMillis() == lastModified
                            && Files.size(file) == size;
                } catch (IOException exception) {
                    return false;
                }
            }
        }
    }
}
