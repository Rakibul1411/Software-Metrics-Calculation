package org.metrics.jdt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.JavaCore;

/**
 * Java language and source-file settings used for one JDT parsing group.
 *
 * <p>The extractor runtime Java version is intentionally independent from
 * these target-project settings.</p>
 */
public final class JavaLanguageConfiguration {

    private final String source;
    private final String compliance;
    private final String target;
    private final Charset charset;
    private final String origin;

    public JavaLanguageConfiguration(
            String source,
            String compliance,
            String target,
            Charset charset,
            String origin) {
        this.source = requireVersion(source, "source");
        this.compliance = requireVersion(compliance, "compliance");
        this.target = requireVersion(target, "target");
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
        this.origin = origin == null || origin.trim().isEmpty()
                ? "fallback" : origin.trim();
    }

    public static JavaLanguageConfiguration uniform(String version, String origin) {
        return new JavaLanguageConfiguration(
                version, version, version, StandardCharsets.UTF_8, origin);
    }

    public JavaLanguageConfiguration withCharset(Charset value) {
        return new JavaLanguageConfiguration(
                source, compliance, target, value, origin);
    }

    public Map<String, String> compilerOptions() {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_SOURCE, source);
        options.put(JavaCore.COMPILER_COMPLIANCE, compliance);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, target);
        options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
        options.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
        return options;
    }

    public String getSource() {
        return source;
    }

    public String getCompliance() {
        return compliance;
    }

    public String getTarget() {
        return target;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getOrigin() {
        return origin;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JavaLanguageConfiguration)) {
            return false;
        }
        JavaLanguageConfiguration that = (JavaLanguageConfiguration) other;
        return source.equals(that.source)
                && compliance.equals(that.compliance)
                && target.equals(that.target)
                && charset.equals(that.charset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, compliance, target, charset);
    }

    @Override
    public String toString() {
        return "source=" + source
                + ", compliance=" + compliance
                + ", target=" + target
                + ", encoding=" + charset.name()
                + " (" + origin + ")";
    }

    private static String requireVersion(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Java " + label + " version is required.");
        }
        return value.trim();
    }
}
