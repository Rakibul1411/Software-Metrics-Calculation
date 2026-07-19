package org.metrics.aeeem.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.metrics.aeeem.calculator.AeeemProjectMetricsCalculator;
import org.metrics.aeeem.history.GitChangeEntropyCalculator;
import org.metrics.aeeem.history.LdhhCalculator;
import org.metrics.aeeem.history.WchuCalculator;
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.parser.AeeemJavaSourceParser;

public final class GitHistoryAnalyzer {

    private final BiWeeklySnapshotGenerator snapshotGenerator = new BiWeeklySnapshotGenerator();

    public List<AeeemMetricResult> analyze(Path suppliedRoot) throws IOException {
        Path repository = findRepository(suppliedRoot);
        verifyRepository(repository);
        String branch = selectMainLineage(repository);
        List<BiWeeklySnapshotGenerator.Snapshot> snapshots = snapshotGenerator.generate(repository, branch);
        if (snapshots.size() < 2) {
            throw new IllegalArgumentException(
                    "AEEEM history metrics require at least two commits on the main/master lineage.");
        }

        List<Map<String, AeeemMetricResult>> history = new ArrayList<>();
        System.out.println("AEEEM history analysis: " + snapshots.size() + " bi-weekly snapshots selected.");
        for (int index = 0; index < snapshots.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("AEEEM history analysis was cancelled.");
            }
            BiWeeklySnapshotGenerator.Snapshot snapshot = snapshots.get(index);
            Path worktree = null;
            try {
                System.out.println("AEEEM snapshot " + (index + 1) + "/" + snapshots.size()
                        + " started (" + snapshot.getDate() + ").");
                worktree = snapshotGenerator.createWorktree(repository, snapshot);
                List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseProject(worktree);
                AeeemProjectMetricsCalculator.apply(metrics);
                history.add(byName(metrics));
                System.out.println("AEEEM snapshot " + (index + 1) + "/" + snapshots.size()
                        + " completed: " + metrics.size() + " production classes.");
            } finally {
                snapshotGenerator.removeWorktree(repository, worktree);
            }
        }

        Map<String, AeeemMetricResult> finalSnapshot = history.get(history.size() - 1);
        WchuCalculator.apply(history, finalSnapshot);
        LdhhCalculator.apply(history, finalSnapshot);
        System.out.println("AEEEM change-entropy calculation started.");
        new GitChangeEntropyCalculator().apply(repository, branch,
                snapshots.get(0).getDate().minusDays(14), finalSnapshot);
        System.out.println("AEEEM change-entropy calculation completed.");
        System.out.println("AEEEM snapshots generated: " + snapshots.size());
        System.out.println("AEEEM final classes analyzed: " + finalSnapshot.size());
        System.out.println("AEEEM final features: 56");
        return new ArrayList<>(finalSnapshot.values());
    }

    public static String gitLogJavaChanges(Path repository, String branch, java.time.LocalDate historyStart)
            throws IOException {
        return GitCommandRunner.run(repository, "log", "--first-parent", "--no-renames", "--name-only",
                "--since=" + historyStart + "T00:00:00Z", "--format=commit:%H", branch,
                "--", ":(glob)**/*.java");
    }

    private Path findRepository(Path suppliedRoot) throws IOException {
        Path normalized = suppliedRoot.toAbsolutePath().normalize();
        if (Files.exists(normalized.resolve(".git"))) {
            return normalized;
        }
        try (Stream<Path> paths = Files.walk(normalized, 4)) {
            List<Path> repositories = paths.filter(path -> path.getFileName() != null)
                    .filter(path -> ".git".equals(path.getFileName().toString()))
                    .map(Path::getParent)
                    .collect(Collectors.toList());
            if (!repositories.isEmpty()) {
                return repositories.get(0);
            }
        }
        throw new IllegalArgumentException(
                "AEEEM extraction requires a full Git repository containing .git history. Use a GitHub repository URL or upload an archive that includes .git.");
    }

    private void verifyRepository(Path repository) throws IOException {
        if (GitCommandRunner.run(repository, "rev-list", "--all", "--count").equals("0")) {
            throw new IllegalArgumentException("The Git repository has no commits.");
        }
        GitCommandRunner.run(repository, "branch", "--all");
        GitCommandRunner.run(repository, "tag", "--list");
    }

    private String selectMainLineage(Path repository) throws IOException {
        for (String candidate : new String[] {"refs/remotes/origin/main", "refs/remotes/origin/master",
                "refs/heads/main", "refs/heads/master", "HEAD"}) {
            try {
                GitCommandRunner.run(repository, "rev-parse", "--verify", candidate);
                return candidate;
            } catch (IOException ignored) {
            }
        }
        throw new IllegalArgumentException("No main, master, or HEAD development lineage was found.");
    }

    private Map<String, AeeemMetricResult> byName(List<AeeemMetricResult> metrics) {
        Map<String, AeeemMetricResult> result = new LinkedHashMap<>();
        for (AeeemMetricResult value : metrics) {
            result.put(value.getFullyQualifiedName(), value);
        }
        return result;
    }
}
