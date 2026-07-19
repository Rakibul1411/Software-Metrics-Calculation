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

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.metrics.aeeem.calculator.AeeemStaticMetricsCalculator;
import org.metrics.aeeem.model.AeeemMetricResult;

public final class AeeemJavaSourceParser {

    private static final int MAX_DIAGNOSTICS = 30;

    private AeeemJavaSourceParser() {
    }

    public static List<AeeemMetricResult> parseProject(Path projectRoot) throws IOException {
        List<Path> javaFiles = collectJavaFiles(projectRoot);
        if (javaFiles.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Map<Path, String> sourceByPath = new LinkedHashMap<>();
        for (Path file : javaFiles) {
            sourceByPath.put(file.toAbsolutePath().normalize(),
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }

        String sourceVersion = inferSourceVersion(sourceByPath.values());
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(classPath(projectRoot), inferSourceRoots(javaFiles).toArray(new String[0]), null, true);
        parser.setCompilerOptions(compilerOptions(sourceVersion));

        String[] fileNames = javaFiles.stream().map(path -> path.toAbsolutePath().normalize().toString())
                .toArray(String[]::new);
        String[] encodings = new String[fileNames.length];
        Arrays.fill(encodings, StandardCharsets.UTF_8.name());
        List<AeeemMetricResult> results = new ArrayList<>();
        int[] diagnosticCounts = new int[2];
        parser.createASTs(fileNames, encodings, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit unit) {
                Path sourcePath = java.nio.file.Paths.get(sourceFilePath).toAbsolutePath().normalize();
                reportProblems(sourcePath, unit, diagnosticCounts);
                String source = sourceByPath.get(sourcePath);
                unit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
                    @Override
                    public boolean visit(TypeDeclaration node) {
                        addResult(unit, node, sourcePath, projectRoot, source, results, diagnosticCounts);
                        return true;
                    }

                    @Override
                    public boolean visit(EnumDeclaration node) {
                        addResult(unit, node, sourcePath, projectRoot, source, results, diagnosticCounts);
                        return true;
                    }
                });
            }
        }, null);
        if (diagnosticCounts[1] > 0) {
            System.err.println("AEEEM JDT warning: " + diagnosticCounts[1]
                    + " additional diagnostics were suppressed for this snapshot.");
        }
        return results.stream()
                .filter(value -> value.getFullyQualifiedName() != null)
                .sorted(Comparator.comparing(AeeemMetricResult::getFullyQualifiedName))
                .collect(Collectors.toList());
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

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(AeeemJavaSourceParser::isProductionSource)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static boolean isProductionSource(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.equals("module-info.java") || fileName.equals("package-info.java")) {
            return false;
        }
        if (fileName.startsWith("test") || fileName.endsWith("test.java") || fileName.endsWith("tests.java")) {
            return false;
        }
        for (Path segment : path.toAbsolutePath().normalize()) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            boolean testSourceSet = name.equals("test") || name.equals("tests") || name.equals("testcase")
                    || name.equals("testcases") || name.startsWith("test-") || name.endsWith("-test")
                    || name.equals("testdata") || name.equals("test-framework");
            boolean buildInfrastructure = Arrays.asList("build-tools", "build-infra", "buildsrc", "gradle",
                    ".gradle", "dev-tools", "devtools").contains(name);
            boolean nonProductSource = Arrays.asList("generated", "target", "build", "out", ".git",
                    "examples", "samples", "benchmark", "benchmarks").contains(name);
            if (testSourceSet || buildInfrastructure || nonProductSource) {
                return false;
            }
        }
        return true;
    }

    private static List<String> inferSourceRoots(Collection<Path> files) {
        Set<String> roots = new LinkedHashSet<>();
        for (Path file : files) {
            Path root = conventionalSourceRoot(file);
            if (root != null) {
                roots.add(root.toString());
            }
        }
        return new ArrayList<>(roots);
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
                    .filter(AeeemJavaSourceParser::isValidJar)
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .forEach(entries::add);
        }
        return entries.toArray(new String[0]);
    }

    private static String inferSourceVersion(Collection<String> sources) {
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
                if (++inspected == 25) {
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
