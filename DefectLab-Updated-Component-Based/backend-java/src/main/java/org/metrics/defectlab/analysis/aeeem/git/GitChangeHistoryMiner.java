package org.metrics.defectlab.analysis.aeeem.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.metrics.defectlab.analysis.aeeem.history.GitChangePeriod;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.aeeem.parser.ProductionSourceSelector;

/**
 * Mines raw Git line changes and groups them into the selected snapshot intervals.
 */
public final class GitChangeHistoryMiner {

    public List<GitChangePeriod> mine(
            Path repository,
            List<BiWeeklySnapshotGenerator.Snapshot> snapshots) throws IOException {
        return mine(repository, snapshots, AeeemAnalysisOptions.current());
    }

    public List<GitChangePeriod> mine(
            Path repository,
            List<BiWeeklySnapshotGenerator.Snapshot> snapshots,
            AeeemAnalysisOptions options) throws IOException {
        Map<String, TrackedFile> currentFiles = new LinkedHashMap<>();
        List<Map<TrackedFile, Double>> changesByIdentity = new ArrayList<>();

        for (int index = 1; index < snapshots.size(); index++) {
            String previous = snapshots.get(index - 1).getCommit();
            String current = snapshots.get(index).getCommit();
            Map<TrackedFile, Double> period = new IdentityHashMap<>();
            if (!previous.equals(current)) {
                List<GitNumstatParser.FileChange> fileChanges;
                try {
                    fileChanges = changes(repository, previous, current, options);
                } catch (Exception exception) {
                    System.err.println("AEEEM Git history miner caught unexpected error for interval "
                            + previous + ".." + current + ": " + exception.getMessage());
                    fileChanges = Collections.emptyList();
                }
                for (GitNumstatParser.FileChange change : fileChanges) {
                    if (!options.isWithinModule(change.getOldPath())
                            && !options.isWithinModule(change.getNewPath())) {
                        continue;
                    }
                    if (!ProductionSourceSelector.isProductionJavaPath(change.getOldPath())
                            && !ProductionSourceSelector.isProductionJavaPath(change.getNewPath())) {
                        continue;
                    }
                    TrackedFile file = identityFor(change, currentFiles);
                    if (change.getChangedLines() > 0L) {
                        period.put(file, period.getOrDefault(file, 0d)
                                + change.getChangedLines());
                    }
                }
            }
            changesByIdentity.add(period);
        }

        List<GitChangePeriod> periods = new ArrayList<>();
        for (int index = 0; index < changesByIdentity.size(); index++) {
            Map<String, Double> byFinalPath = new LinkedHashMap<>();
            for (Map.Entry<TrackedFile, Double> entry
                    : changesByIdentity.get(index).entrySet()) {
                String path = entry.getKey().currentPath;
                if (path != null && !path.isEmpty()) {
                    byFinalPath.put(path, byFinalPath.getOrDefault(path, 0d)
                            + entry.getValue());
                }
            }
            periods.add(new GitChangePeriod(index, byFinalPath));
        }
        return periods;
    }

    private List<GitNumstatParser.FileChange> changes(
            Path repository,
            String previous,
            String current,
            AeeemAnalysisOptions options) {
        String pathSpec = options.isScoped()
                ? ":(glob)" + options.getModulePath() + "/**/*.java"
                : ":(glob)**/*.java";

        // 1. Attempt standard git log with rename detection and retry on transient network failures
        GitCommandRunner.GitResult result = executeLogWithRetry(
                repository, true, previous, current, pathSpec);

        if (result != null && result.isSuccess()) {
            return GitNumstatParser.parse(result.getOutput());
        }

        // 2. If rename detection triggered a promisor remote fetch failure, retry with --no-renames
        if (result != null && result.isPromisorOrNetworkFailure()) {
            System.err.println("AEEEM Git history: promisor remote fetch failed during rename detection between "
                    + previous + " and " + current + "; retrying with --no-renames.");
            GitCommandRunner.GitResult noRenameResult = executeLogWithRetry(
                    repository, false, previous, current, pathSpec);
            if (noRenameResult != null && noRenameResult.isSuccess()) {
                return GitNumstatParser.parse(noRenameResult.getOutput());
            }
            if (noRenameResult != null && !noRenameResult.getOutput().isEmpty()) {
                List<GitNumstatParser.FileChange> partial = GitNumstatParser.parse(noRenameResult.getOutput());
                if (!partial.isEmpty()) {
                    System.err.println("AEEEM Git history: salvaged " + partial.size()
                            + " changes from partial output between " + previous + " and " + current);
                    return supplementWithTreeDiff(repository, previous, current, pathSpec, partial);
                }
            }
        }

        // 3. If partial output was received from the first attempt, salvage it
        if (result != null && !result.getOutput().isEmpty()) {
            List<GitNumstatParser.FileChange> partial = GitNumstatParser.parse(result.getOutput());
            if (!partial.isEmpty()) {
                System.err.println("AEEEM Git history: salvaged " + partial.size()
                        + " changes from partial output between " + previous + " and " + current);
                return supplementWithTreeDiff(repository, previous, current, pathSpec, partial);
            }
        }

        // 4. Fall back to tree-only diff (--name-status), which operates on local tree objects and never touches blobs/remote
        System.err.println("AEEEM Git history: falling back to tree diff (--name-status) between "
                + previous + " and " + current);
        List<GitNumstatParser.FileChange> treeChanges = mineTreeChanges(repository, previous, current, pathSpec);
        if (!treeChanges.isEmpty()) {
            return treeChanges;
        }

        System.err.println("AEEEM Git history warning: no changes could be retrieved between "
                + previous + " and " + current + "; proceeding with zero changes for this interval.");
        return Collections.emptyList();
    }

    private GitCommandRunner.GitResult executeLogWithRetry(
            Path repository,
            boolean findRenames,
            String previous,
            String current,
            String pathSpec) {
        List<String> args = new ArrayList<>(Arrays.asList(
                "-c", "core.quotepath=false",
                "log",
                "--first-parent",
                "--reverse",
                "--date-order"));
        if (findRenames) {
            args.add("--find-renames=50%");
        } else {
            args.add("--no-renames");
        }
        args.addAll(Arrays.asList(
                "--diff-merges=first-parent",
                "--format=commit:%H",
                "--numstat",
                previous + ".." + current,
                "--",
                pathSpec));

        GitCommandRunner.GitResult result = null;
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                result = GitCommandRunner.runCommand(repository, args.toArray(new String[0]));
                if (result.isSuccess()) {
                    return result;
                }
                if (!result.isPromisorOrNetworkFailure() || attempt == maxAttempts) {
                    return result;
                }
                System.err.println("AEEEM Git history: network timeout during log attempt "
                        + attempt + "/" + maxAttempts + " between " + previous + " and " + current
                        + ". Retrying in " + (attempt * 1500) + "ms...");
                try {
                    Thread.sleep(attempt * 1500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return result;
                }
            } catch (IOException exception) {
                System.err.println("AEEEM Git history command execution error (attempt "
                        + attempt + "/" + maxAttempts + "): " + exception.getMessage());
                if (attempt == maxAttempts) {
                    return result;
                }
            }
        }
        return result;
    }

    private List<GitNumstatParser.FileChange> mineTreeChanges(
            Path repository,
            String previous,
            String current,
            String pathSpec) {
        try {
            GitCommandRunner.GitResult result = GitCommandRunner.runCommand(repository,
                    "-c", "core.quotepath=false",
                    "log",
                    "--first-parent",
                    "--reverse",
                    "--date-order",
                    "--diff-merges=first-parent",
                    "--format=commit:%H",
                    "--name-status",
                    previous + ".." + current,
                    "--",
                    pathSpec);
            if (result != null && !result.getOutput().isEmpty()) {
                return GitNumstatParser.parseNameStatus(result.getOutput(), 10L);
            }
        } catch (Exception exception) {
            System.err.println("AEEEM Git history tree diff failed: " + exception.getMessage());
        }
        return Collections.emptyList();
    }

    private List<GitNumstatParser.FileChange> supplementWithTreeDiff(
            Path repository,
            String previous,
            String current,
            String pathSpec,
            List<GitNumstatParser.FileChange> partialChanges) {
        Set<String> coveredCommits = new HashSet<>();
        for (GitNumstatParser.FileChange change : partialChanges) {
            if (change.getCommit() != null && !change.getCommit().isEmpty()) {
                coveredCommits.add(change.getCommit());
            }
        }
        List<GitNumstatParser.FileChange> treeChanges = mineTreeChanges(repository, previous, current, pathSpec);
        List<GitNumstatParser.FileChange> merged = new ArrayList<>(partialChanges);
        for (GitNumstatParser.FileChange change : treeChanges) {
            if (!coveredCommits.contains(change.getCommit())) {
                merged.add(change);
            }
        }
        return merged;
    }

    private TrackedFile identityFor(
            GitNumstatParser.FileChange change,
            Map<String, TrackedFile> currentFiles) {
        String oldPath = GitNumstatParser.normalize(change.getOldPath());
        String newPath = GitNumstatParser.normalize(change.getNewPath());
        TrackedFile file;
        if (change.isRename()) {
            file = currentFiles.remove(oldPath);
            if (file == null) {
                file = currentFiles.get(newPath);
            }
            if (file == null) {
                file = new TrackedFile(oldPath);
            }
            file.currentPath = newPath;
            currentFiles.put(newPath, file);
        } else {
            file = currentFiles.get(newPath);
            if (file == null) {
                file = new TrackedFile(newPath);
                currentFiles.put(newPath, file);
            }
        }
        return file;
    }

    private static final class TrackedFile {
        private String currentPath;

        private TrackedFile(String currentPath) {
            this.currentPath = currentPath;
        }
    }
}
