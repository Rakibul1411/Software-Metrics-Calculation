package org.metrics.defectlab.analysis.aeeem.history;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Historical windows reported for the five labelled AEEEM benchmark datasets.
 *
 * <p>
 * This class intentionally keeps the original public API so existing DefectLab
 * code does not need to be changed immediately.
 * </p>
 *
 * <p>
 * For ordinary single-repository projects, {@link #getRecommendedRepositoryUrl()}
 * and {@link #getDefaultModulePath()} continue to work exactly as before.
 * </p>
 *
 * <p>
 * Mylyn is special: its historical source was distributed across multiple
 * Mylyn component repositories. Therefore the ML profile additionally exposes
 * {@link #getHistoricalRepositories()}.
 * </p>
 */
public enum AeeemBenchmarkProfile {

    /**
     * Current-project mode.
     *
     * <p>
     * This profile does not impose historical dates, release tags, or
     * repository restrictions.
     * </p>
     */
    CURRENT(
            "current",
            "Current project",
            null,
            null,
            null,
            null,
            0,
            0,
            null,
            Collections.emptyList()
    ),

    /**
     * AEEEM JDT benchmark.
     */
    JDT(
            "jdt",
            "Eclipse JDT Core",
            "2005-01-01",
            "2008-06-17",
            "R3_4",
            "org.eclipse.jdt.core",
            91,
            997,
            "https://github.com/eclipse-jdt/eclipse.jdt.core",
            Collections.singletonList(
                    new HistoricalRepository(
                            "Eclipse JDT Core",
                            "https://github.com/eclipse-jdt/eclipse.jdt.core",
                            "R3_4",
                            "org.eclipse.jdt.core"
                    )
            )
    ),

    /**
     * AEEEM PDE benchmark.
     */
    PDE(
            "pde",
            "Eclipse PDE UI",
            "2005-01-01",
            "2008-09-11",
            "R3_4_1",
            "ui",
            97,
            1497,
            "https://github.com/eclipse-pde/eclipse.pde",
            Collections.singletonList(
                    new HistoricalRepository(
                            "Eclipse PDE UI",
                            "https://github.com/eclipse-pde/eclipse.pde",
                            "R3_4_1",
                            "ui"
                    )
            )
    ),

    /**
     * AEEEM Equinox benchmark.
     */
    EQ(
            "eq",
            "Equinox framework",
            "2005-01-01",
            "2008-06-25",
            "R3_4",
            "bundles/org.eclipse.osgi",
            91,
            324,
            "https://github.com/eclipse-equinox/equinox.framework",
            Collections.singletonList(
                    new HistoricalRepository(
                            "Equinox framework",
                            "https://github.com/eclipse-equinox/equinox.framework",
                            "R3_4",
                            "bundles/org.eclipse.osgi"
                    )
            )
    ),

    /**
     * AEEEM Mylyn 3.1 benchmark.
     *
     * <p>
     * The public GitHub mirror at org.eclipse.mylyn contains the historical
     * R_3_1_0 tag, but the historical Mylyn project was composed of multiple
     * component repositories. Therefore the primary repository URL is retained
     * for backward compatibility, while the complete historical repository set
     * is exposed through getHistoricalRepositories().
     * </p>
     */
    ML(
            "ml",
            "Mylyn",
            "2005-01-17",
            "2009-03-17",
            "R_3_1_0",
            null,
            98,
            1862,
            "https://github.com/eclipse-mylyn/org.eclipse.mylyn",

            createMylynRepositories()
    ),

    /**
     * AEEEM Apache Lucene benchmark.
     */
    LC(
            "lc",
            "Apache Lucene",
            "2005-01-01",
            "2008-10-08",
            "releases/lucene/2.4.0",
            null,
            99,
            691,
            "https://github.com/apache/lucene",
            Collections.singletonList(
                    new HistoricalRepository(
                            "Apache Lucene",
                            "https://github.com/apache/lucene",
                            "releases/lucene/2.4.0",
                            null
                    )
            )
    );

    private final String id;
    private final String displayName;
    private final LocalDate historyStart;
    private final LocalDate releaseDate;
    private final String releaseRef;
    private final String defaultModulePath;
    private final int referenceSnapshotCount;
    private final int referenceRowCount;

    /**
     * Kept for backward compatibility with the existing DefectLab code.
     */
    private final String recommendedRepositoryUrl;

    /**
     * New repository set used when a benchmark is historically split across
     * multiple repositories.
     */
    private final List<HistoricalRepository> historicalRepositories;

    AeeemBenchmarkProfile(
            String id,
            String displayName,
            String historyStart,
            String releaseDate,
            String releaseRef,
            String defaultModulePath,
            int referenceSnapshotCount,
            int referenceRowCount,
            String recommendedRepositoryUrl,
            List<HistoricalRepository> historicalRepositories) {

        this.id = id;
        this.displayName = displayName;

        this.historyStart =
                historyStart == null
                        ? null
                        : LocalDate.parse(historyStart);

        this.releaseDate =
                releaseDate == null
                        ? null
                        : LocalDate.parse(releaseDate);

        this.releaseRef = releaseRef;
        this.defaultModulePath = defaultModulePath;
        this.referenceSnapshotCount = referenceSnapshotCount;
        this.referenceRowCount = referenceRowCount;
        this.recommendedRepositoryUrl = recommendedRepositoryUrl;

        this.historicalRepositories =
                historicalRepositories == null
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(
                                new ArrayList<>(historicalRepositories)
                        );
    }

    /**
     * Resolve an AEEEM profile from its short identifier.
     *
     * @param value profile id such as "ml", "jdt", "pde", "eq", "lc"
     * @return matching profile
     */
    public static AeeemBenchmarkProfile fromId(String value) {

        String normalized =
                value == null
                        ? "current"
                        : value.trim().toLowerCase(Locale.ROOT);

        for (AeeemBenchmarkProfile profile : values()) {
            if (profile.id.equals(normalized)) {
                return profile;
            }
        }

        throw new IllegalArgumentException(
                "AEEEM profile must be current, jdt, pde, eq, ml, or lc."
        );
    }

    /**
     * @return true when this is one of the labelled AEEEM benchmark profiles.
     */
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

    /**
     * Preferred historical release reference.
     *
     * <p>
     * For Mylyn this is R_3_1_0.
     * </p>
     */
    public String getReleaseRef() {
        return releaseRef;
    }

    /**
     * Existing API retained for compatibility.
     *
     * <p>
     * Mylyn intentionally returns null here because its source is not
     * represented by one module path in the top-level repository.
     * </p>
     */
    public String getDefaultModulePath() {
        return defaultModulePath;
    }

    public int getReferenceSnapshotCount() {
        return referenceSnapshotCount;
    }

    public int getReferenceRowCount() {
        return referenceRowCount;
    }

    /**
     * Existing API retained for compatibility.
     *
     * <p>
     * For Mylyn this remains:
     *
     * https://github.com/eclipse-mylyn/org.eclipse.mylyn
     *
     * so existing UI/controller/service code expecting a single URL will
     * continue to work.
     * </p>
     */
    public String getRecommendedRepositoryUrl() {
        return recommendedRepositoryUrl;
    }

    /**
     * Returns all repositories that participate in the historical benchmark.
     *
     * <p>
     * For JDT/PDE/EQ/LC this contains one repository.
     * For Mylyn it contains the historical Mylyn component repositories.
     * </p>
     *
     * @return immutable list of historical repositories
     */
    public List<HistoricalRepository> getHistoricalRepositories() {
        return historicalRepositories;
    }

    /**
     * @return true if this benchmark is represented by more than one repository.
     */
    public boolean isMultiRepositoryBenchmark() {
        return historicalRepositories.size() > 1;
    }

    /**
     * Returns the first historical repository.
     *
     * <p>
     * This is useful for old code that only understands one repository.
     * For Mylyn this is the legacy/top-level Mylyn repository.
     * </p>
     */
    public HistoricalRepository getPrimaryHistoricalRepository() {

        if (historicalRepositories.isEmpty()) {
            return null;
        }

        return historicalRepositories.get(0);
    }

    /**
     * Returns the URLs of all historical repositories.
     */
    public List<String> getHistoricalRepositoryUrls() {

        List<String> urls = new ArrayList<>();

        for (HistoricalRepository repository : historicalRepositories) {
            urls.add(repository.getRepositoryUrl());
        }

        return Collections.unmodifiableList(urls);
    }

    /**
     * Returns the historical repositories that use the supplied release ref.
     *
     * <p>
     * This is useful for Mylyn because each component repository has its own
     * R_3_1_0 tag.
     * </p>
     */
    public List<HistoricalRepository> getRepositoriesForReleaseRef(
            String requestedReleaseRef) {

        if (requestedReleaseRef == null) {
            return Collections.emptyList();
        }

        String requested =
                requestedReleaseRef.trim();

        List<HistoricalRepository> matches =
                new ArrayList<>();

        for (HistoricalRepository repository : historicalRepositories) {

            if (repository.getReleaseRef() != null
                    && repository.getReleaseRef().equals(requested)) {

                matches.add(repository);
            }
        }

        return Collections.unmodifiableList(matches);
    }

    /**
     * Benchmark profiles are tied to one historical project.
     *
     * <p>
     * This method is retained exactly for compatibility with the existing
     * DefectLab implementation.
     * </p>
     *
     * <p>
     * For multi-repository Mylyn, the legacy single URL is still accepted.
     * Use getHistoricalRepositories() when the complete benchmark source is
     * required.
     * </p>
     */
    public void requireRecommendedRepository(String repositoryUrl) {

        if (!isBenchmark() || recommendedRepositoryUrl == null) {
            return;
        }

        String supplied =
                normalizeRepositoryUrl(repositoryUrl);

        String expected =
                normalizeRepositoryUrl(recommendedRepositoryUrl);

        /*
         * Backward-compatible behaviour:
         *
         * Existing code may still provide the top-level Mylyn URL. Do not
         * reject it merely because ML is now represented internally by
         * multiple historical repositories.
         */
        if (expected.equals(supplied)) {
            return;
        }

        /*
         * For multi-repository benchmarks, also allow any repository that is
         * explicitly part of the historical repository set.
         */
        if (isHistoricalRepository(repositoryUrl)) {
            return;
        }

        throw new IllegalArgumentException(
                getDisplayName()
                        + " requires a verified historical repository. "
                        + "Expected one of: "
                        + getHistoricalRepositoryUrls()
        );
    }

    /**
     * Checks whether a URL belongs to this benchmark's historical repository
     * set.
     */
    public boolean isHistoricalRepository(String repositoryUrl) {

        String supplied =
                normalizeRepositoryUrl(repositoryUrl);

        if (supplied.isEmpty()) {
            return false;
        }

        for (HistoricalRepository repository : historicalRepositories) {

            String expected =
                    normalizeRepositoryUrl(
                            repository.getRepositoryUrl()
                    );

            if (expected.equals(supplied)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether historical resolution must be strict.
     *
     * <p>
     * Benchmark datasets should not silently fall back to an arbitrary
     * date-based commit when a required historical ref cannot be resolved.
     * </p>
     */
    public boolean requiresStrictHistoricalResolution() {
        return isBenchmark();
    }

    /**
     * Determines whether this profile has a usable historical time window.
     */
    public boolean hasHistoricalWindow() {
        return historyStart != null && releaseDate != null;
    }

    /**
     * Determines whether a release reference is configured.
     */
    public boolean hasReleaseRef() {
        return releaseRef != null
                && !releaseRef.trim().isEmpty();
    }

    /**
     * Determines whether this profile has an expected benchmark snapshot
     * count.
     */
    public boolean hasReferenceSnapshotCount() {
        return referenceSnapshotCount > 0;
    }

    /**
     * Determines whether this profile has an expected dataset row count.
     */
    public boolean hasReferenceRowCount() {
        return referenceRowCount > 0;
    }

    /**
     * Human-readable benchmark description useful for logging.
     */
    public String describe() {

        StringBuilder builder =
                new StringBuilder();

        builder.append(displayName);

        if (releaseRef != null) {
            builder.append(" [")
                    .append(releaseRef)
                    .append("]");
        }

        if (historyStart != null && releaseDate != null) {
            builder.append(" ")
                    .append(historyStart)
                    .append(" -> ")
                    .append(releaseDate);
        }

        if (!historicalRepositories.isEmpty()) {
            builder.append(", repositories=")
                    .append(historicalRepositories.size());
        }

        if (referenceSnapshotCount > 0) {
            builder.append(", snapshots=")
                    .append(referenceSnapshotCount);
        }

        if (referenceRowCount > 0) {
            builder.append(", rows=")
                    .append(referenceRowCount);
        }

        return builder.toString();
    }

    /**
     * Normalizes a repository URL for safe comparison.
     */
    private static String normalizeRepositoryUrl(String value) {

        String normalized =
                value == null
                        ? ""
                        : value.trim().toLowerCase(Locale.ROOT);

        while (normalized.endsWith("/")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }

        if (normalized.endsWith(".git")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 4
                    );
        }

        return normalized;
    }

    /**
     * Builds the historical Mylyn repository manifest.
     *
     * <p>
     * The top-level org.eclipse.mylyn repository is retained as the first
     * repository for backward compatibility. The component repositories are
     * then listed separately.
     * </p>
     *
     * <p>
     * Module paths are deliberately null here. The repository resolver should
     * discover/validate the actual production source roots at R_3_1_0 rather
     * than assuming that every modern module path existed in exactly the same
     * form in 2009.
     * </p>
     */
    private static List<HistoricalRepository> createMylynRepositories() {

        List<HistoricalRepository> repositories =
                new ArrayList<>();

        /*
         * Primary/top-level repository.
         *
         * Kept first because the existing DefectLab code expects the profile
         * to have one recommended repository URL.
         */
        repositories.add(
                new HistoricalRepository(
                        "Mylyn Releng / Top-level",
                        "https://github.com/eclipse-mylyn/org.eclipse.mylyn",
                        "R_3_1_0",
                        null
                )
        );

        /*
         * Mylyn Commons.
         */
        repositories.add(
                new HistoricalRepository(
                        "Mylyn Commons",
                        "https://github.com/eclipse-mylyn/org.eclipse.mylyn.commons",
                        "R_3_1_0",
                        null
                )
        );

        /*
         * Mylyn Context.
         */
        repositories.add(
                new HistoricalRepository(
                        "Mylyn Context",
                        "https://github.com/eclipse-mylyn/org.eclipse.mylyn.context",
                        "R_3_1_0",
                        null
                )
        );

        /*
         * Mylyn Tasks.
         */
        repositories.add(
                new HistoricalRepository(
                        "Mylyn Tasks",
                        "https://github.com/eclipse-mylyn/org.eclipse.mylyn.tasks",
                        "R_3_1_0",
                        null
                )
        );

        /*
         * Mylyn Docs (WikiText).
         */
        repositories.add(
                new HistoricalRepository(
                        "Mylyn Docs",
                        "https://github.com/eclipse-mylyn/org.eclipse.mylyn.docs",
                        "R_3_1_0",
                        null
                )
        );

        /*
         * Mylyn Incubator (Sandbox, Monitor, Web Tasks).
         */
        repositories.add(
                new HistoricalRepository(
                        "Mylyn Incubator",
                        "https://github.com/eclipse-mylyn/org.eclipse.mylyn.incubator",
                        "R_3_1_0",
                        null
                )
        );

        return Collections.unmodifiableList(repositories);
    }

    /**
     * Immutable description of one historical repository.
     *
     * <p>
     * This is intentionally a normal Java class rather than a record so the
     * code remains compatible with projects that are not yet using Java 16+.
     * </p>
     */
    public static final class HistoricalRepository {

        private final String name;
        private final String repositoryUrl;
        private final String releaseRef;
        private final String modulePath;

        public HistoricalRepository(
                String name,
                String repositoryUrl,
                String releaseRef,
                String modulePath) {

            this.name =
                    Objects.requireNonNull(
                            name,
                            "name"
                    );

            this.repositoryUrl =
                    Objects.requireNonNull(
                            repositoryUrl,
                            "repositoryUrl"
                    );

            this.releaseRef = releaseRef;
            this.modulePath = modulePath;
        }

        public String getName() {
            return name;
        }

        public String getRepositoryUrl() {
            return repositoryUrl;
        }

        public String getReleaseRef() {
            return releaseRef;
        }

        public String getModulePath() {
            return modulePath;
        }

        public boolean hasReleaseRef() {
            return releaseRef != null
                    && !releaseRef.trim().isEmpty();
        }

        public boolean hasModulePath() {
            return modulePath != null
                    && !modulePath.trim().isEmpty();
        }

        @Override
        public String toString() {

            return "HistoricalRepository{"
                    + "name='" + name + '\''
                    + ", repositoryUrl='" + repositoryUrl + '\''
                    + ", releaseRef='" + releaseRef + '\''
                    + ", modulePath='" + modulePath + '\''
                    + '}';
        }
    }
}