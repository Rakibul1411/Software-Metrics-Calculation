package org.metrics.defectlab.analysis.promise.analyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.metrics.defectlab.analysis.javaparser.JavaLanguageConfiguration;
import org.metrics.defectlab.analysis.promise.compile.SourcePaths;

/**
 * Maps production source files to the fully qualified names they declare.
 *
 * <p>This is the JDT parser's remaining job in the PROMISE pipeline: deciding
 * which classes are eligible to become CSV rows, and letting a source file that
 * failed to compile take its classes out of the output. No metric is derived
 * here.
 *
 * <p>Only top-level types are indexed. The published PROMISE datasets contain no
 * {@code Outer$Inner} rows, so nested and anonymous classes stay out of the CSV
 * while remaining in the bytecode model for hierarchy and coupling resolution.
 *
 * <p>Bindings are not resolved: the declared package and type names are all that
 * is needed, and skipping resolution keeps this pass cheap and independent of
 * whether dependencies are present.
 */
public final class PromiseSourceIndex {

    private final Map<Path, List<String>> typesBySource = new LinkedHashMap<>();

    public static PromiseSourceIndex build(
            List<Path> javaFiles, JavaLanguageConfiguration configuration) {
        PromiseSourceIndex index = new PromiseSourceIndex();
        for (Path file : javaFiles) {
            index.typesBySource.put(
                    SourcePaths.canonical(file), declaredTypesOf(file, configuration));
        }
        return index;
    }

    /**
     * Row-eligible class names, excluding every class whose source failed to
     * compile.
     */
    public Set<String> rowEligibleClassNames(Set<Path> failedSources) {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<Path, List<String>> entry : typesBySource.entrySet()) {
            if (failedSources.contains(entry.getKey())) {
                continue;
            }
            names.addAll(entry.getValue());
        }
        return names;
    }

    /** Class names declared by sources that failed to compile. */
    public Set<String> excludedClassNames(Set<Path> failedSources) {
        Set<String> names = new LinkedHashSet<>();
        for (Path failed : failedSources) {
            names.addAll(typesBySource.getOrDefault(failed, List.of()));
        }
        return names;
    }

    private static List<String> declaredTypesOf(
            Path file, JavaLanguageConfiguration configuration) {
        List<String> names = new ArrayList<>();
        try {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            // Without an explicit source level JDT parses at 1.3, which silently
            // drops any type declared with newer syntax such as an annotation
            // type or an enum, and those classes would then never become rows.
            parser.setCompilerOptions(configuration.compilerOptions());
            parser.setSource(new String(
                    Files.readAllBytes(file), configuration.getCharset()).toCharArray());

            CompilationUnit unit = (CompilationUnit) parser.createAST(null);
            PackageDeclaration declaration = unit.getPackage();
            String packageName = declaration == null
                    ? "" : declaration.getName().getFullyQualifiedName() + ".";

            for (Object type : unit.types()) {
                if (type instanceof AbstractTypeDeclaration) {
                    names.add(packageName
                            + ((AbstractTypeDeclaration) type).getName().getIdentifier());
                }
            }
        } catch (Exception exception) {
            // A source we cannot index simply contributes no rows; the compiler
            // diagnostics already explain anything genuinely broken.
            return List.of();
        }
        return names;
    }
}
