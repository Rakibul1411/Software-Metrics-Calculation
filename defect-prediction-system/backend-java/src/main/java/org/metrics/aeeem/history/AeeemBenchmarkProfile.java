package org.metrics.aeeem.history;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Historical windows reported for the five labelled AEEEM benchmark datasets.
 *
 * <p>The ARFF files contain labels and predictors but no class identifiers or
 * repository metadata. These profiles therefore define the prediction window;
 * repository and module scope still come from the source selected by the user.</p>
 */
public enum AeeemBenchmarkProfile {

    CURRENT("current", "Current project", null, null, null, null, 0, 0),
    JDT("jdt", "Eclipse JDT Core", "2005-01-01", "2008-06-17",
            "R3_4", "org.eclipse.jdt.core", 91, 997),
    PDE("pde", "Eclipse PDE UI", "2005-01-01", "2008-09-11",
            "R3_4_1", null, 97, 1497),
    EQ("eq", "Equinox framework", "2005-01-01", "2008-06-25",
            null, null, 91, 324),
    ML("ml", "Mylyn", "2005-01-17", "2009-03-17",
            "R_3_1_0", null, 98, 1862),
    LC("lc", "Apache Lucene", "2005-01-01", "2008-10-08",
            "releases/lucene/2.4.0", null, 99, 691);

    private final String id;
    private final String displayName;
    private final LocalDate historyStart;
    private final LocalDate releaseDate;
    private final String releaseRef;
    private final String defaultModulePath;
    private final int referenceSnapshotCount;
    private final int referenceRowCount;

    AeeemBenchmarkProfile(
            String id,
            String displayName,
            String historyStart,
            String releaseDate,
            String releaseRef,
            String defaultModulePath,
            int referenceSnapshotCount,
            int referenceRowCount) {
        this.id = id;
        this.displayName = displayName;
        this.historyStart = historyStart == null ? null : LocalDate.parse(historyStart);
        this.releaseDate = releaseDate == null ? null : LocalDate.parse(releaseDate);
        this.releaseRef = releaseRef;
        this.defaultModulePath = defaultModulePath;
        this.referenceSnapshotCount = referenceSnapshotCount;
        this.referenceRowCount = referenceRowCount;
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
}
