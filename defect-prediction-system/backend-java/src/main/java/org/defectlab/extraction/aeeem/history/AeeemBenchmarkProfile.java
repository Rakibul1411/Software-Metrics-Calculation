package org.metrics.aeeem.history;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Historical windows reported for the five labelled AEEEM benchmark datasets.
 *
 * <p>The ARFF files contain labels and predictors but no class identifiers or
 * repository metadata. These profiles define the prediction window, preferred
 * release ref, module scope, and verified public repository needed to reproduce
 * each historical target as closely as the migrated Git mirrors allow.</p>
 */
public enum AeeemBenchmarkProfile {

    CURRENT("current", "Current project", null, null, null, null, 0, 0, null),
    JDT("jdt", "Eclipse JDT Core", "2005-01-01", "2008-06-17",
            "R3_4", "org.eclipse.jdt.core", 91, 997,
            "https://github.com/eclipse-jdt/eclipse.jdt.core"),
    PDE("pde", "Eclipse PDE UI", "2005-01-01", "2008-09-11",
            "R3_4_1", "ui/org.eclipse.pde.ui", 97, 1497,
            "https://github.com/eclipse-pde/eclipse.pde"),
    EQ("eq", "Equinox framework", "2005-01-01", "2008-06-25",
            "R3_4", "bundles/org.eclipse.osgi", 91, 324,
            "https://github.com/eclipse-equinox/equinox.framework"),
    ML("ml", "Mylyn", "2005-01-17", "2009-03-17",
            "R_3_1_0", null, 98, 1862,
            "https://github.com/eclipse-mylyn/org.eclipse.mylyn"),
    LC("lc", "Apache Lucene", "2005-01-01", "2008-10-08",
            "releases/lucene/2.4.0", null, 99, 691,
            "https://github.com/apache/lucene");

    private final String id;
    private final String displayName;
    private final LocalDate historyStart;
    private final LocalDate releaseDate;
    private final String releaseRef;
    private final String defaultModulePath;
    private final int referenceSnapshotCount;
    private final int referenceRowCount;
    private final String recommendedRepositoryUrl;

    AeeemBenchmarkProfile(
            String id,
            String displayName,
            String historyStart,
            String releaseDate,
            String releaseRef,
            String defaultModulePath,
            int referenceSnapshotCount,
            int referenceRowCount,
            String recommendedRepositoryUrl) {
        this.id = id;
        this.displayName = displayName;
        this.historyStart = historyStart == null ? null : LocalDate.parse(historyStart);
        this.releaseDate = releaseDate == null ? null : LocalDate.parse(releaseDate);
        this.releaseRef = releaseRef;
        this.defaultModulePath = defaultModulePath;
        this.referenceSnapshotCount = referenceSnapshotCount;
        this.referenceRowCount = referenceRowCount;
        this.recommendedRepositoryUrl = recommendedRepositoryUrl;
    }

    public static AeeemBenchmarkProfile fromId(String value) {
        String normalized = value == null ? "current"
                : value.trim().toLowerCase(Locale.ROOT);
        for (AeeemBenchmarkProfile profile : values()) {
            if (profile.id.equals(normalized)) {
                return profile;
            }
        }
        throw new IllegalArgumentException(
                "AEEEM profile must be current, jdt, pde, eq, ml, or lc.");
    }

    public boolean isBenchmark() {
        return this != CURRENT;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getHistoryStart() {
        return historyStart;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public String getReleaseRef() {
        return releaseRef;
    }

    public String getDefaultModulePath() {
        return defaultModulePath;
    }

    public int getReferenceSnapshotCount() {
        return referenceSnapshotCount;
    }

    public int getReferenceRowCount() {
        return referenceRowCount;
    }

    public String getRecommendedRepositoryUrl() {
        return recommendedRepositoryUrl;
    }

    /**
     * Benchmark profiles are tied to one historical project. Rejecting another
     * repository before cloning prevents a release-date fallback from silently
     * accepting an unrelated Java project that happens to be old enough.
     */
    public void requireRecommendedRepository(String repositoryUrl) {
        if (!isBenchmark() || recommendedRepositoryUrl == null) {
            return;
        }
        String supplied = normalizeRepositoryUrl(repositoryUrl);
        String expected = normalizeRepositoryUrl(recommendedRepositoryUrl);
        if (!expected.equals(supplied)) {
            throw new IllegalArgumentException(
                    getDisplayName() + " requires this historical GitHub repository: "
                            + recommendedRepositoryUrl
                            + ". Select the matching AEEEM profile or use the recommended link.");
        }
    }

    private static String normalizeRepositoryUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }
}
