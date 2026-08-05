package org.metrics.defectlab.analysis.javaparser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable file-to-language-configuration result. */
public final class ResolvedJavaProject {

    private final Map<JavaLanguageConfiguration, List<Path>> filesByConfiguration;
    private final Map<Path, JavaLanguageConfiguration> configurationByFile;
    private final List<String> diagnostics;

    ResolvedJavaProject(
            Map<JavaLanguageConfiguration, List<Path>> filesByConfiguration,
            Map<Path, JavaLanguageConfiguration> configurationByFile,
            List<String> diagnostics) {
        Map<JavaLanguageConfiguration, List<Path>> groups = new LinkedHashMap<>();
        for (Map.Entry<JavaLanguageConfiguration, List<Path>> entry
                : filesByConfiguration.entrySet()) {
            groups.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<>(entry.getValue())));
        }
        this.filesByConfiguration = Collections.unmodifiableMap(groups);
        this.configurationByFile = Collections.unmodifiableMap(
                new LinkedHashMap<>(configurationByFile));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public Map<JavaLanguageConfiguration, List<Path>> getFilesByConfiguration() {
        return filesByConfiguration;
    }

    public JavaLanguageConfiguration configurationFor(Path file) {
        return configurationByFile.get(file.toAbsolutePath().normalize());
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }
}
