package org.metrics.defectlab.analysis.aeeem.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.metrics.defectlab.analysis.aeeem.calculator.AeeemProjectMetricsCalculator;
import org.metrics.defectlab.analysis.aeeem.history.AeeemHistoryConfiguration;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisSummary;
import org.metrics.defectlab.analysis.aeeem.history.GitChangeEntropyCalculator;
import org.metrics.defectlab.analysis.aeeem.history.GitChangePeriod;
import org.metrics.defectlab.analysis.aeeem.history.LdhhCalculator;
import org.metrics.defectlab.analysis.aeeem.history.WchuCalculator;
import org.metrics.defectlab.analysis.aeeem.model.AeeemMetricResult;
import org.metrics.defectlab.analysis.aeeem.parser.AeeemJavaSourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GitHistoryAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHistoryAnalyzer.class);

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
        BiWeeklySnapshotGenerator.Selection selection =
                snapshotGenerator.generateSelection(repository, branch, options);
        List<BiWeeklySnapshotGenerator.Snapshot> snapshots =
                selection.getSnapshots();
        if (snapshots.size() < 2) {
            throw new IllegalArgumentException(
                    "AEEEM history metrics require at least two commits on the main/master lineage.");
        }

        List<Map<String, AeeemMetricResult>> history = new ArrayList<>();
        Map<String, Map<String, AeeemMetricResult>> metricsByTree = new LinkedHashMap<>();
        LOGGER.info("AEEEM history analysis: {} bi-weekly snapshots selected for {}.",
                snapshots.size(), options.getProfile().getDisplayName());
        LOGGER.info("AEEEM repository scope: {}.",
                options.isScoped() ? options.getModulePath() : "<repository root>");
        for (String warning : selection.getWarnings()) {
            LOGGER.warn("AEEEM compatibility note: {}", warning);
        }
        if (options.isBenchmarkProfile()
                && snapshots.size() != options.getProfile().getReferenceSnapshotCount()) {
            LOGGER.info("AEEEM benchmark note: the selected Git mirror produced {} scheduled snapshots; "
                    + "the historic dataset reports {} parsed versions.",
                    snapshots.size(), options.getProfile().getReferenceSnapshotCount());
        }
        AeeemJavaSourceParser.SourceFileCache fileCache =
                new AeeemJavaSourceParser.SourceFileCache();
        Path worktree = null;
        try {
            worktree = snapshotGenerator.createWorktree(repository, snapshots.get(0));
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
                    LOGGER.info("AEEEM snapshot {}/{} reused ({}, selected module tree is unchanged).",
                            index + 1, snapshots.size(), snapshot.getDate());
                    continue;
                }

                LOGGER.info("AEEEM snapshot {}/{} started ({}).",
                        index + 1, snapshots.size(), snapshot.getDate());
                if (index > 0) {
                    snapshotGenerator.checkoutCommit(worktree, snapshot);
                }
                Path sourceScope = resolveSourceScope(worktree, options.getModulePath());
                List<AeeemMetricResult> metrics =
                        AeeemJavaSourceParser.parseProject(
                                worktree,
                                sourceScope,
                                options.getProfile(),
                                fileCache);
                AeeemProjectMetricsCalculator.apply(metrics);
                Map<String, AeeemMetricResult> snapshotMetrics = byName(metrics);
                metricsByTree.put(treeIdentity, snapshotMetrics);
                history.add(snapshotMetrics);
                LOGGER.info("AEEEM snapshot {}/{} completed: {} production classes.",
                        index + 1, snapshots.size(), metrics.size());
            }
        } finally {
            if (worktree != null) {
                snapshotGenerator.removeWorktree(repository, worktree);
            }
        }

        Map<String, AeeemMetricResult> finalSnapshot = history.get(history.size() - 1);
        if (finalSnapshot.isEmpty()) {
            throw new IllegalArgumentException(
                    "No production Java classes were found at the selected AEEEM release"
                            + (options.isScoped()
                            ? " inside module '" + options.getModulePath() + "'."
                            : ".")
                            + " The selected historical ref may be a disconnected migration "
                            + "tag or the GitHub mirror may not contain the original benchmark "
                            + "source tree. Use the verified profile URL; the resolver will use "
                            + "the final first-parent release-date commit when a legacy tag is "
                            + "not on that repository lineage.");
        }
        WchuCalculator.apply(history, finalSnapshot);
        LdhhCalculator.apply(history, finalSnapshot);
        LOGGER.info("AEEEM change-entropy calculation started.");
        List<GitChangePeriod> changePeriods;
        try {
            changePeriods = changeHistoryMiner.mine(repository, snapshots, options);
        } catch (Exception exception) {
            LOGGER.error("AEEEM change-entropy mining error: {}", exception.getMessage(), exception);
            changePeriods = Collections.emptyList();
        }
        new GitChangeEntropyCalculator().apply(changePeriods, finalSnapshot,
                AeeemHistoryConfiguration.fromEnvironment());
        LOGGER.info("AEEEM change-entropy calculation completed.");
        LOGGER.info("AEEEM snapshots generated: {}", snapshots.size());
        LOGGER.info("AEEEM final classes analyzed: {}", finalSnapshot.size());
        LOGGER.info("AEEEM final features: 56");
        List<String> analysisWarnings = new ArrayList<>(selection.getWarnings());
        int referenceRows = options.getProfile().getReferenceRowCount();
        if (options.isBenchmarkProfile() && referenceRows > 0
                && finalSnapshot.size() != referenceRows) {
            analysisWarnings.add(
                    "Entity coverage differs from the published AEEEM dataset: this "
                            + "migrated Git source produced " + finalSnapshot.size()
                            + " top-level production classes; the predefined "
                            + options.getProfile().getId().toUpperCase()
                            + " file contains " + referenceRows
                            + " rows. Do not treat the rows or metric values as an exact "
                            + "one-to-one reproduction.");
        }
        AeeemAnalysisSummary summary = new AeeemAnalysisSummary(
                options,
                snapshots.get(0).getDate().toString(),
                snapshots.get(snapshots.size() - 1).getDate().toString(),
                snapshots.get(snapshots.size() - 1).getCommit(),
                snapshots.size(),
                branch,
                selection.getReleaseResolution(),
                analysisWarnings);
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
                    .toList();
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
