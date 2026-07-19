package org.metrics.aeeem.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BiWeeklySnapshotGenerator {

    private static final int SNAPSHOT_DAYS = 14;
    private static final int DEFAULT_MAX_SNAPSHOTS = 26;

    public List<Snapshot> generate(Path repository, String branch) throws IOException {
        String timeline = GitCommandRunner.run(repository, "log", "--first-parent", "--reverse",
                "--format=%H|%ct", branch);
        List<Snapshot> commits = parseTimeline(timeline);
        if (commits.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        LocalDate firstDate = commits.get(0).getDate();
        LocalDate headDate = commits.get(commits.size() - 1).getDate();
        Set<String> selectedCommits = new LinkedHashSet<>();
        int cursor = 0;
        Snapshot latest = commits.get(0);
        for (LocalDate date = firstDate; !date.isAfter(headDate); date = date.plusDays(SNAPSHOT_DAYS)) {
            LocalDate boundary = date.plusDays(1);
            while (cursor < commits.size() && commits.get(cursor).getDate().isBefore(boundary)) {
                latest = commits.get(cursor++);
            }
            selectedCommits.add(latest.getCommit());
        }
        selectedCommits.add(commits.get(commits.size() - 1).getCommit());

        List<Snapshot> snapshots = new ArrayList<>();
        Map<String, Snapshot> byCommit = new java.util.LinkedHashMap<>();
        for (Snapshot commit : commits) {
            byCommit.put(commit.getCommit(), commit);
        }
        for (String commit : selectedCommits) {
            snapshots.add(byCommit.get(commit));
        }
        return recentWindow(snapshots, maxSnapshots());
    }

    private List<Snapshot> parseTimeline(String timeline) throws IOException {
        List<Snapshot> commits = new ArrayList<>();
        for (String line : timeline.split("\\R")) {
            String[] parts = line.trim().split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                LocalDate date = Instant.ofEpochSecond(Long.parseLong(parts[1]))
                        .atZone(ZoneOffset.UTC).toLocalDate();
                commits.add(new Snapshot(parts[0], date));
            } catch (NumberFormatException exception) {
                throw new IOException("Git returned an invalid commit timestamp.", exception);
            }
        }
        return commits;
    }

    private List<Snapshot> recentWindow(List<Snapshot> snapshots, int maximum) {
        if (maximum <= 0 || snapshots.size() <= maximum) {
            return snapshots;
        }
        return new ArrayList<>(snapshots.subList(snapshots.size() - maximum, snapshots.size()));
    }

    private int maxSnapshots() {
        String configured = System.getenv("AEEEM_MAX_SNAPSHOTS");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_MAX_SNAPSHOTS;
        }
        try {
            return Math.max(0, Integer.parseInt(configured.trim()));
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_SNAPSHOTS;
        }
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
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public static final class Snapshot {
        private final String commit;
        private final LocalDate date;

        Snapshot(String commit, LocalDate date) {
            this.commit = commit;
            this.date = date;
        }

        public String getCommit() { return commit; }
        public LocalDate getDate() { return date; }
    }
}
