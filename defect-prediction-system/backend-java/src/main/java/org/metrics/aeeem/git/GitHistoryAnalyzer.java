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
import org.metrics.aeeem.history.AeeemHistoryConfiguration;
import org.metrics.aeeem.history.AeeemAnalysisOptions;
import org.metrics.aeeem.history.AeeemAnalysisSummary;
import org.metrics.aeeem.history.GitChangeEntropyCalculator;
import org.metrics.aeeem.history.GitChangePeriod;
import org.metrics.aeeem.history.LdhhCalculator;
import org.metrics.aeeem.history.WchuCalculator;
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.parser.AeeemJavaSourceParser;

public final class GitHistoryAnalyzer {

    private final BiWeeklySnapshotGenerator snapshotGenerator = new BiWeeklySnapshotGenerator();
    private final GitChangeHistoryMiner changeHistoryMiner = new GitChangeHistoryMiner();

    public List<AeeemMetricResult> analyze(Path suppliedRoot) throws IOException {
        return analyze(suppliedRoot, AeeemAnalysisOptions.current());
    }

    public List<AeeemMetricResult> analyze(
            Path suppliedRoot,
            AeeemAnalysisOptions options) throws IOException {
        return analyzeWithSummary(suppliedRoot, options).getMetrics();
    }

    public AnalysisResult analyzeWithSummary(
            Path suppliedRoot,
            AeeemAnalysisOptions options) throws IOException {
        Path repository = findRepository(suppliedRoot);
        verifyRepository(repository);
        String branch = selectMainLineage(repository, options.getBranch());
        List<BiWeeklySnapshotGenerator.Snapshot> snapshots =
                snapshotGenerator.generate(repository, branch, options);
        if (snapshots.size() < 2) {
            throw new IllegalArgumentException(
                    "AEEEM history metrics require at least two commits on the main/master lineage.");
        }

        List<Map<String, AeeemMetricResult>> history = new ArrayList<>();
        Map<String, Map<String, AeeemMetricResult>> metricsByTree = new LinkedHashMap<>();
        System.out.println("AEEEM history analysis: " + snapshots.size()
                + " bi-weekly snapshots selected for "
                + options.getProfile().getDisplayName() + ".");
        System.out.println("AEEEM repository scope: "
                + (options.isScoped() ? options.getModulePath() : "<repository root>") + ".");
        if (options.isBenchmarkProfile()
                && snapshots.size() != options.getProfile().getReferenceSnapshotCount()) {
            System.out.println("AEEEM benchmark note: the selected Git mirror produced "
                    + snapshots.size() + " scheduled snapshots; the historic dataset reports "
                    + options.getProfile().getReferenceSnapshotCount()
                    + " parsed versions. SCM migration and inactive periods can differ.");
        }
        for (int index = 0; index < snapshots.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("AEEEM history analysis was cancelled.");
            }
            BiWeeklySnapshotGenerator.Snapshot snapshot = snapshots.get(index);
            String treeIdentity = treeIdentity(repository, snapshot.getCommit(),
                    options.getModulePath());
            Map<String, AeeemMetricResult> cached = metricsByTree.get(treeIdentity);
            if (cached != null) {
                history.add(cached);
                System.out.println("AEEEM snapshot " + (index + 1) + "/" + snapshots.size()
                        + " reused (" + snapshot.getDate()
                        + ", selected module tree is unchanged).");
                continue;
            }
            Path worktree = null;
            try {
                System.out.println("AEEEM snapshot " + (index + 1) + "/" + snapshots.size()
                        + " started (" + snapshot.getDate() + ").");
                worktree = snapshotGenerator.createWorktree(repository, snapshot);
                Path sourceScope = resolveSourceScope(worktree, options.getModulePath());
                List<AeeemMetricResult> metrics =
                        AeeemJavaSourceParser.parseProject(worktree, sourceScope);
                AeeemProjectMetricsCalculator.apply(metrics);
                Map<String, AeeemMetricResult> snapshotMetrics = byName(metrics);
                metricsByTree.put(treeIdentity, snapshotMetrics);
                history.add(snapshotMetrics);
                System.out.println("AEEEM snapshot " + (index + 1) + "/" + snapshots.size()
                        + " completed: " + metrics.size() + " production classes.");
            } finally {
                snapshotGenerator.removeWorktree(repository, worktree);
            }
        }

        Map<String, AeeemMetricResult> finalSnapshot = history.get(history.size() - 1);
        if (finalSnapshot.isEmpty()) {
            throw new IllegalArgumentException(
                    "No production Java classes were found at the selected AEEEM release"
                            + (options.isScoped()
                            ? " inside module '" + options.getModulePath() + "'." : "."));
        }
        WchuCalculator.apply(history, finalSnapshot);
        LdhhCalculator.apply(history, finalSnapshot);
        System.out.println("AEEEM change-entropy calculation started.");
        List<GitChangePeriod> changePeriods =
                changeHistoryMiner.mine(repository, snapshots, options);
        new GitChangeEntropyCalculator().apply(changePeriods, finalSnapshot,
                AeeemHistoryConfiguration.fromEnvironment());
        System.out.println("AEEEM change-entropy calculation completed.");
        System.out.println("AEEEM snapshots generated: " + snapshots.size());
        System.out.println("AEEEM final classes analyzed: " + finalSnapshot.size());
        System.out.println("AEEEM final features: 56");
        AeeemAnalysisSummary summary = new AeeemAnalysisSummary(
                options,
                snapshots.get(0).getDate().toString(),
                snapshots.get(snapshots.size() - 1).getDate().toString(),
                snapshots.get(snapshots.size() - 1).getCommit(),
                snapshots.size(),
                branch);
        return new AnalysisResult(new ArrayList<>(finalSnapshot.values()), summary);
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

    private String selectMainLineage(Path repository, String requestedBranch) throws IOException {
        if (requestedBranch != null) {
            for (String candidate : new String[] {requestedBranch,
                    "refs/remotes/origin/" + requestedBranch,
                    "refs/heads/" + requestedBranch}) {
                try {
                    String resolved = GitCommandRunner.run(
                            repository, "rev-parse", "--verify", candidate);
                    if (!resolved.trim().isEmpty()) {
                        return candidate;
                    }
                } catch (IOException ignored) {
                }
            }
            throw new IllegalArgumentException(
                    "The Git repository does not contain requested branch '"
                            + requestedBranch + "'.");
        }
        for (String candidate : new String[] {"refs/remotes/origin/HEAD", "HEAD",
                "refs/remotes/origin/main", "refs/remotes/origin/master",
                "refs/heads/main", "refs/heads/master"}) {
            try {
                String resolved = GitCommandRunner.run(repository, "rev-parse", "--verify", candidate);
                if (!resolved.trim().isEmpty()) {
                    return candidate;
                }
            } catch (IOException ignored) {
            }
        }
        throw new IllegalArgumentException("No main, master, or HEAD development lineage was found.");
    }

    private Path resolveSourceScope(Path worktree, String modulePath) {
        Path root = worktree.toAbsolutePath().normalize();
        if (modulePath == null || modulePath.isEmpty()) {
            return root;
        }
        Path scope = root.resolve(modulePath).normalize();
        if (!scope.startsWith(root)) {
            throw new IllegalArgumentException(
                    "AEEEM module path must stay inside the repository.");
        }
        return scope;
    }

    private String treeIdentity(
            Path repository,
            String commit,
            String modulePath) throws IOException {
        String expression = modulePath == null || modulePath.isEmpty()
                ? commit + "^{tree}" : commit + ":" + modulePath;
        try {
            return GitCommandRunner.run(repository, "rev-parse", "--verify", expression);
        } catch (IOException exception) {
            return "missing-scope:" + modulePath;
        }
    }

    private Map<String, AeeemMetricResult> byName(List<AeeemMetricResult> metrics) {
        Map<String, AeeemMetricResult> result = new LinkedHashMap<>();
        for (AeeemMetricResult value : metrics) {
            result.put(value.getFullyQualifiedName(), value);
        }
        return result;
    }

    public static final class AnalysisResult {
        private final List<AeeemMetricResult> metrics;
        private final AeeemAnalysisSummary summary;

        private AnalysisResult(
                List<AeeemMetricResult> metrics,
                AeeemAnalysisSummary summary) {
            this.metrics = metrics;
            this.summary = summary;
        }

        public List<AeeemMetricResult> getMetrics() {
            return metrics;
        }

        public AeeemAnalysisSummary getSummary() {
            return summary;
        }
    }
}
