package org.metrics.aeeem.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-facing provenance for a generated AEEEM target dataset.
 */
public final class AeeemAnalysisSummary {

    private final String profileId;
    private final String profileName;
    private final String historyStart;
    private final String releaseDate;
    private final String releaseCommit;
    private final int snapshotCount;
    private final String branch;
    private final String modulePath;
    private final int referenceSnapshotCount;
    private final int referenceRowCount;
    private final String releaseResolution;
    private final List<String> warnings;

    public AeeemAnalysisSummary(
            AeeemAnalysisOptions options,
            String historyStart,
            String releaseDate,
            String releaseCommit,
            int snapshotCount,
            String branch,
            String releaseResolution,
            List<String> warnings) {
        this.profileId = options.getProfile().getId();
        this.profileName = options.getProfile().getDisplayName();
        this.historyStart = historyStart;
        this.releaseDate = releaseDate;
        this.releaseCommit = releaseCommit;
        this.snapshotCount = snapshotCount;
        this.branch = branch;
        this.modulePath = options.getModulePath();
        this.referenceSnapshotCount =
                options.getProfile().getReferenceSnapshotCount();
        this.referenceRowCount = options.getProfile().getReferenceRowCount();
        this.releaseResolution = releaseResolution;
        this.warnings = Collections.unmodifiableList(
                warnings == null ? Collections.emptyList() : new ArrayList<>(warnings));
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getHistoryStart() {
        return historyStart;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getReleaseCommit() {
        return releaseCommit;
    }

    public int getSnapshotCount() {
        return snapshotCount;
    }

    public String getBranch() {
        return branch;
    }

    public String getModulePath() {
        return modulePath;
    }

    public int getReferenceSnapshotCount() {
        return referenceSnapshotCount;
    }

    public int getReferenceRowCount() {
        return referenceRowCount;
    }

    public String getReleaseResolution() {
        return releaseResolution;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
