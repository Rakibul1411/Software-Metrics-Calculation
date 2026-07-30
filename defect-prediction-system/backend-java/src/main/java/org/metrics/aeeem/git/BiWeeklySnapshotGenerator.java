package org.metrics.aeeem.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
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
        return generateSelection(repository, branch, options).getSnapshots();
    }

    Selection generateSelection(
            Path repository,
            String branch,
            AeeemAnalysisOptions options) throws IOException {
        List<String> warnings = new ArrayList<>();
        ResolvedRelease release = resolveReleaseCommit(repository, branch, options);
        if (release.warning != null) {
            warnings.add(release.warning);
        }
        String timeline = GitCommandRunner.run(repository, "log", "--first-parent",
                "--reverse", "--date-order", "--format=%H|%ct", release.commit);
        List<CommitPoint> commits = parseTimeline(timeline);
        if (commits.isEmpty()) {
            return new Selection(Collections.emptyList(), release.resolution, warnings);
        }

        List<Snapshot> snapshots = new ArrayList<>();
        CommitPoint oldest = commits.get(0);
        LocalDate requestedStart = options.getHistoryStart();
        LocalDate effectiveStart = requestedStart;
        if (requestedStart != null && oldest.date.isAfter(requestedStart)) {
            effectiveStart = oldest.date;
            warnings.add(
                    "Partial historical coverage: this GitHub mirror begins on "
                            + oldest.date + ", after the benchmark start "
                            + requestedStart + ". Metrics use all available history from "
                            + oldest.date + "; they are not an exact reproduction of the "
                            + "original SCM window.");
        }
        CommitPoint first = firstSample(commits, effectiveStart);
        CommitPoint head = commits.get(commits.size() - 1);
        LocalDate startDate = effectiveStart == null ? first.date : effectiveStart;
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
            Snapshot releaseSnapshot =
                    new Snapshot(head.commit, releaseDate, head.date);
            if (options.isBenchmarkProfile() && snapshots.size() > 1) {
                snapshots.set(snapshots.size() - 1, releaseSnapshot);
            } else {
                snapshots.add(releaseSnapshot);
            }
        } else if (!last.date.equals(releaseDate)) {
            snapshots.set(snapshots.size() - 1,
                    new Snapshot(last.commit, releaseDate, last.commitDate));
        }

        int limit = options.getMaximumSnapshots() == null
                ? maximumSnapshots : options.getMaximumSnapshots().intValue();
        if (limit > 0 && snapshots.size() > limit) {
            snapshots = new ArrayList<>(snapshots.subList(
                    snapshots.size() - limit, snapshots.size()));
        }
        return new Selection(snapshots, release.resolution, warnings);
    }

    private ResolvedRelease resolveReleaseCommit(
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
                    String commit = GitCommandRunner.run(
                            repository, "rev-parse", "--verify",
                            candidate + "^{commit}");
                    LocalDate commitDate = commitDate(repository, commit);
                    if (options.getReleaseDate() == null
                            || !commitDate.isAfter(options.getReleaseDate())) {
                        if (options.isBenchmarkProfile()
                                && !isAncestorOfLineage(repository, commit, branch)) {
                            continue;
                        }
                        if (options.isBenchmarkProfile()
                                && !containsJavaSource(repository, commit, options)) {
                            continue;
                        }
                        return new ResolvedRelease(
                                commit, "release ref " + options.getReleaseRef(), null);
                    }
                } catch (IOException ignored) {
                }
            }
            if (options.getReleaseDate() != null) {
                String commit = commitOnOrBefore(
                        repository, branch, options.getReleaseDate());
                return new ResolvedRelease(
                        commit,
                        "release-date fallback",
                        "Legacy release ref '" + options.getReleaseRef()
                                + "' is not available in this GitHub mirror. The final "
                                + "first-parent commit on or before "
                                + options.getReleaseDate() + " was used instead.");
            }
            throw new IllegalArgumentException(
                    "The selected repository does not contain AEEEM release ref '"
                            + options.getReleaseRef() + "' and no release date fallback "
                            + "was configured.");
        }
        if (options.getReleaseDate() != null) {
            return new ResolvedRelease(
                    commitOnOrBefore(repository, branch, options.getReleaseDate()),
                    "release date " + options.getReleaseDate(),
                    null);
        }
        return new ResolvedRelease(
                GitCommandRunner.run(repository, "rev-parse", "--verify",
                        branch + "^{commit}"),
                "repository HEAD",
                null);
    }

    private boolean isAncestorOfLineage(
            Path repository,
            String commit,
            String branch) throws IOException {
        try {
            GitCommandRunner.run(repository, "merge-base", "--is-ancestor", commit, branch);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean containsJavaSource(
            Path repository,
            String commit,
            AeeemAnalysisOptions options) throws IOException {
        String tree = GitCommandRunner.run(
                repository, "ls-tree", "-r", "--name-only", commit);
        String module = options.getModulePath();
        for (String rawPath : tree.split("\\R")) {
            String path = rawPath.replace('\\', '/');
            if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".java")) {
                continue;
            }
            if (module == null || module.isEmpty()
                    || path.equals(module) || path.startsWith(module + "/")) {
                return true;
            }
        }
        return false;
    }

    private String commitOnOrBefore(
            Path repository,
            String branch,
            LocalDate releaseDate) throws IOException {
        String commit = GitCommandRunner.run(repository, "rev-list", "-1",
                "--first-parent",
                "--before=" + releaseDate.plusDays(1) + "T00:00:00Z",
                branch);
        if (commit.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "The selected repository has no commit on or before AEEEM release date "
                            + releaseDate + ". Use the recommended historical repository "
                            + "for this profile.");
        }
        return commit.trim();
    }

    private LocalDate commitDate(Path repository, String commit) throws IOException {
        String timestamp = GitCommandRunner.run(
                repository, "show", "-s", "--format=%ct", commit);
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp.trim()))
                    .atZone(ZoneOffset.UTC).toLocalDate();
        } catch (NumberFormatException exception) {
            throw new IOException("Git returned an invalid release timestamp.", exception);
        }
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

    static final class Selection {
        private final List<Snapshot> snapshots;
        private final String releaseResolution;
        private final List<String> warnings;

        private Selection(
                List<Snapshot> snapshots,
                String releaseResolution,
                List<String> warnings) {
            this.snapshots = Collections.unmodifiableList(
                    new ArrayList<>(snapshots));
            this.releaseResolution = releaseResolution;
            this.warnings = Collections.unmodifiableList(
                    new ArrayList<>(warnings));
        }

        List<Snapshot> getSnapshots() {
            return snapshots;
        }

        String getReleaseResolution() {
            return releaseResolution;
        }

        List<String> getWarnings() {
            return warnings;
        }
    }

    private static final class ResolvedRelease {
        private final String commit;
        private final String resolution;
        private final String warning;

        private ResolvedRelease(String commit, String resolution, String warning) {
            this.commit = commit;
            this.resolution = resolution;
            this.warning = warning;
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
