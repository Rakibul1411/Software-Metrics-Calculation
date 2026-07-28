package org.metrics.aeeem.history;

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

    public AeeemAnalysisSummary(
            AeeemAnalysisOptions options,
            String historyStart,
            String releaseDate,
            String releaseCommit,
            int snapshotCount,
            String branch) {
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
}
