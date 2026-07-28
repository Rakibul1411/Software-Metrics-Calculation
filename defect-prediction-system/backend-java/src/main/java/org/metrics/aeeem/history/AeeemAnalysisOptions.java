package org.metrics.aeeem.history;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Validated repository scope and history controls for one AEEEM extraction. */
public final class AeeemAnalysisOptions {

    private final AeeemBenchmarkProfile profile;
    private final String branch;
    private final String modulePath;
    private final LocalDate historyStart;
    private final LocalDate releaseDate;
    private final String releaseRef;
    private final Integer maximumSnapshots;

    private AeeemAnalysisOptions(
            AeeemBenchmarkProfile profile,
            String branch,
            String modulePath,
            LocalDate historyStart,
            LocalDate releaseDate,
            String releaseRef,
            Integer maximumSnapshots) {
        this.profile = profile;
        this.branch = trimToNull(branch);
        this.modulePath = normalizeModulePath(modulePath);
        this.historyStart = historyStart;
        this.releaseDate = releaseDate;
        this.releaseRef = trimToNull(releaseRef);
        this.maximumSnapshots = maximumSnapshots;
        if (historyStart != null && releaseDate != null && historyStart.isAfter(releaseDate)) {
            throw new IllegalArgumentException(
                    "AEEEM history start date must not be after the release date.");
        }
    }

    public static AeeemAnalysisOptions current() {
        return fromRequest("current", null, null, null, null, null, null);
    }

    public static AeeemAnalysisOptions fromRequest(
            String profileId,
            String branch,
            String modulePath,
            String historyStart,
            String releaseDate,
            String releaseRef,
            Integer maximumSnapshots) {
        AeeemBenchmarkProfile profile = AeeemBenchmarkProfile.fromId(profileId);
        if (maximumSnapshots != null && maximumSnapshots < 0) {
            throw new IllegalArgumentException("Maximum AEEEM snapshots cannot be negative.");
        }

        if (profile.isBenchmark()) {
            String effectiveModule = trimToNull(modulePath);
            if (effectiveModule == null) {
                effectiveModule = profile.getDefaultModulePath();
            }
            return new AeeemAnalysisOptions(
                    profile,
                    branch,
                    effectiveModule,
                    profile.getHistoryStart(),
                    profile.getReleaseDate(),
                    profile.getReleaseRef(),
                    Integer.valueOf(0));
        }

        return new AeeemAnalysisOptions(
                profile,
                branch,
                modulePath,
                parseDate(historyStart, "history start"),
                parseDate(releaseDate, "release"),
                releaseRef,
                maximumSnapshots);
    }

    private static LocalDate parseDate(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "AEEEM " + label + " date must use YYYY-MM-DD.");
        }
    }

    public static String normalizeModulePath(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "";
        }
        normalized = normalized.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "AEEEM module path must be a repository-relative folder.");
            }
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    public AeeemBenchmarkProfile getProfile() {
        return profile;
    }

    public String getBranch() {
        return branch;
    }

    public String getModulePath() {
        return modulePath;
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

    public Integer getMaximumSnapshots() {
        return maximumSnapshots;
    }

    public boolean isScoped() {
        return !modulePath.isEmpty();
    }

    public boolean isBenchmarkProfile() {
        return profile.isBenchmark();
    }

    public boolean isWithinModule(String repositoryRelativePath) {
        if (!isScoped()) {
            return true;
        }
        String normalized = repositoryRelativePath == null ? ""
                : repositoryRelativePath.replace('\\', '/');
        return normalized.equals(modulePath) || normalized.startsWith(modulePath + "/");
    }
}
