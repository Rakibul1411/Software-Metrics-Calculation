package org.metrics.aeeem.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.metrics.aeeem.history.AeeemAnalysisOptions;

/**
 * Selects one first-parent repository state at each 14-day boundary.
 *
 * <p>Empty periods are retained as repeated commit IDs because their position
 * is part of the decay formulas. Repeated commits are parsed only once by the
 * history analyzer.</p>
 */
public final class BiWeeklySnapshotGenerator {

    static final int SNAPSHOT_DAYS = 14;
    static final int DEFAULT_MAX_SNAPSHOTS = 26;
    private final int maximumSnapshots;

    public BiWeeklySnapshotGenerator() {
        this(configuredMaximumSnapshots());
    }

    BiWeeklySnapshotGenerator(int maximumSnapshots) {
        this.maximumSnapshots = maximumSnapshots <= 0
                ? 0 : Math.max(2, maximumSnapshots);
    }

    public List<Snapshot> generate(Path repository, String branch) throws IOException {
        return generate(repository, branch, AeeemAnalysisOptions.current());
    }

    public List<Snapshot> generate(
            Path repository,
            String branch,
            AeeemAnalysisOptions options) throws IOException {
        String releaseCommit = resolveReleaseCommit(repository, branch, options);
        String timeline = GitCommandRunner.run(repository, "log", "--first-parent",
                "--reverse", "--date-order", "--format=%H|%ct", releaseCommit);
        List<CommitPoint> commits = parseTimeline(timeline);
        if (commits.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Snapshot> snapshots = new ArrayList<>();
        CommitPoint first = firstSample(commits, options.getHistoryStart());
        CommitPoint head = commits.get(commits.size() - 1);
        LocalDate startDate = options.getHistoryStart() == null
                ? first.date : options.getHistoryStart();
        LocalDate releaseDate = options.getReleaseDate() == null
                ? head.date : options.getReleaseDate();
        if (head.date.isAfter(releaseDate)) {
            throw new IllegalArgumentException(
                    "AEEEM release ref '" + options.getReleaseRef()
                            + "' points to " + head.date
                            + ", after the selected release date " + releaseDate + ".");
        }
        snapshots.add(new Snapshot(first.commit, startDate, first.date));

        LocalDate boundary = startDate.plusDays(SNAPSHOT_DAYS);
        while (!boundary.isAfter(releaseDate)) {
            CommitPoint selected = latestAtOrBefore(commits, boundary);
            snapshots.add(new Snapshot(selected.commit, boundary, selected.date));
            boundary = boundary.plusDays(SNAPSHOT_DAYS);
        }

        Snapshot last = snapshots.get(snapshots.size() - 1);
        if (!last.commit.equals(head.commit)) {
            Snapshot release = new Snapshot(head.commit, releaseDate, head.date);
            if (options.isBenchmarkProfile() && snapshots.size() > 1) {
                snapshots.set(snapshots.size() - 1, release);
            } else {
                snapshots.add(release);
            }
        } else if (!last.date.equals(releaseDate)) {
            snapshots.set(snapshots.size() - 1,
                    new Snapshot(last.commit, releaseDate, last.commitDate));
        }

        int limit = options.getMaximumSnapshots() == null
                ? maximumSnapshots : options.getMaximumSnapshots().intValue();
        if (limit > 0 && snapshots.size() > limit) {
            return new ArrayList<>(snapshots.subList(
                    snapshots.size() - limit, snapshots.size()));
        }
        return snapshots;
    }

    private String resolveReleaseCommit(
            Path repository,
            String branch,
            AeeemAnalysisOptions options) throws IOException {
        if (options.getReleaseRef() != null) {
            for (String candidate : new String[] {
                    options.getReleaseRef(),
                    "refs/tags/" + options.getReleaseRef(),
                    "refs/heads/" + options.getReleaseRef(),
                    "refs/remotes/origin/" + options.getReleaseRef()}) {
                try {
                    return GitCommandRunner.run(repository, "rev-parse", "--verify",
                            candidate + "^{commit}");
                } catch (IOException ignored) {
                }
            }
            throw new IllegalArgumentException(
                    "The selected repository does not contain required AEEEM release ref '"
                            + options.getReleaseRef() + "' for "
                            + options.getProfile().getDisplayName() + ".");
        }
        if (options.getReleaseDate() != null) {
            String commit = GitCommandRunner.run(repository, "rev-list", "-1",
                    "--first-parent",
                    "--before=" + options.getReleaseDate().plusDays(1) + "T00:00:00Z",
                    branch);
            if (commit.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "The selected repository has no commit on or before AEEEM release date "
                                + options.getReleaseDate() + ".");
            }
            return commit.trim();
        }
        return GitCommandRunner.run(repository, "rev-parse", "--verify",
                branch + "^{commit}");
    }

    private CommitPoint firstSample(
            List<CommitPoint> commits,
            LocalDate requestedStart) {
        if (requestedStart == null) {
            return commits.get(0);
        }
        CommitPoint oldest = commits.get(0);
        if (oldest.date.isAfter(requestedStart)) {
            throw new IllegalArgumentException(
                    "The repository history begins on " + oldest.date
                            + ", but the selected AEEEM profile requires history from "
                            + requestedStart + ".");
        }
        return latestAtOrBefore(commits, requestedStart);
    }

    private static int configuredMaximumSnapshots() {
        String configured = System.getProperty("aeeem.maxSnapshots");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("AEEEM_MAX_SNAPSHOTS");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                return Math.max(0, Integer.parseInt(configured.trim()));
            } catch (NumberFormatException ignored) {
                // Use the safe default.
            }
        }
        return DEFAULT_MAX_SNAPSHOTS;
    }

    private List<CommitPoint> parseTimeline(String timeline) throws IOException {
        List<CommitPoint> commits = new ArrayList<>();
        for (String line : timeline.split("\\R")) {
            String[] parts = line.trim().split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                LocalDate date = Instant.ofEpochSecond(Long.parseLong(parts[1]))
                        .atZone(ZoneOffset.UTC).toLocalDate();
                commits.add(new CommitPoint(parts[0], date));
            } catch (NumberFormatException exception) {
                throw new IOException("Git returned an invalid commit timestamp.", exception);
            }
        }
        return commits;
    }

    private CommitPoint latestAtOrBefore(List<CommitPoint> commits, LocalDate boundary) {
        CommitPoint selected = commits.get(0);
        for (CommitPoint commit : commits) {
            if (!commit.date.isAfter(boundary)) {
                selected = commit;
            }
        }
        return selected;
    }

    public Path createWorktree(Path repository, Snapshot snapshot) throws IOException {
        Path worktree = Files.createTempDirectory("aeeem-snapshot-");
        Files.delete(worktree);
        GitCommandRunner.run(repository, "worktree", "add", "--detach", "--force",
                worktree.toString(), snapshot.getCommit());
        return worktree;
    }

    public void removeWorktree(Path repository, Path worktree) {
        if (worktree == null) {
            return;
        }
        try {
            GitCommandRunner.run(repository, "worktree", "remove", "--force", worktree.toString());
            GitCommandRunner.run(repository, "worktree", "prune");
        } catch (IOException ignored) {
            deleteRecursively(worktree);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static final class CommitPoint {
        private final String commit;
        private final LocalDate date;

        private CommitPoint(String commit, LocalDate date) {
            this.commit = commit;
            this.date = date;
        }
    }

    public static final class Snapshot {
        private final String commit;
        private final LocalDate date;
        private final LocalDate commitDate;

        Snapshot(String commit, LocalDate date, LocalDate commitDate) {
            this.commit = commit;
            this.date = date;
            this.commitDate = commitDate;
        }

        public String getCommit() {
            return commit;
        }

        /** Scheduled sample date (or the exact release date for the final partial period). */
        public LocalDate getDate() {
            return date;
        }

        public LocalDate getCommitDate() {
            return commitDate;
        }
    }
}
