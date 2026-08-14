package org.metrics.defectlab.analysis.promise.compile;

/**
 * Records how a release was compiled. Bytecode-derived metrics depend on the
 * compiler and the source/target level, so this metadata is what makes a
 * difference against the published PROMISE values explainable.
 */
public final class PromiseCompilationMetadata {

    private final String compiler;
    private final String runtimeJavaVersion;
    private final String sourceLevel;
    private final String targetLevel;
    private final int compiledSourceCount;
    private final int classpathEntryCount;

    PromiseCompilationMetadata(
            String compiler,
            String runtimeJavaVersion,
            String sourceLevel,
            String targetLevel,
            int compiledSourceCount,
            int classpathEntryCount) {
        this.compiler = compiler;
        this.runtimeJavaVersion = runtimeJavaVersion;
        this.sourceLevel = sourceLevel;
        this.targetLevel = targetLevel;
        this.compiledSourceCount = compiledSourceCount;
        this.classpathEntryCount = classpathEntryCount;
    }

    public String getCompiler() {
        return compiler;
    }

    public String getRuntimeJavaVersion() {
        return runtimeJavaVersion;
    }

    public String getSourceLevel() {
        return sourceLevel;
    }

    public String getTargetLevel() {
        return targetLevel;
    }

    public int getCompiledSourceCount() {
        return compiledSourceCount;
    }

    public int getClasspathEntryCount() {
        return classpathEntryCount;
    }

    public String describe() {
        return "compiler=" + compiler
                + ", runtimeJava=" + runtimeJavaVersion
                + ", source=" + sourceLevel
                + ", target=" + targetLevel
                + ", sources=" + compiledSourceCount
                + ", classpathEntries=" + classpathEntryCount;
    }
}
