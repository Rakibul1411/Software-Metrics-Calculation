package org.metrics.aeeem.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.metrics.aeeem.history.GitChangePeriod;
import org.metrics.aeeem.history.AeeemAnalysisOptions;
import org.metrics.aeeem.parser.ProductionSourceSelector;

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
                for (GitNumstatParser.FileChange change
                        : changes(repository, previous, current, options)) {
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
            AeeemAnalysisOptions options) throws IOException {
        String pathSpec = options.isScoped()
                ? ":(glob)" + options.getModulePath() + "/**/*.java"
                : ":(glob)**/*.java";
        String output = GitCommandRunner.run(repository,
                "-c", "core.quotepath=false",
                "log",
                "--first-parent",
                "--reverse",
                "--date-order",
                "--find-renames=50%",
                "--diff-merges=first-parent",
                "--format=commit:%H",
                "--numstat",
                previous + ".." + current,
                "--",
                pathSpec);
        return GitNumstatParser.parse(output);
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
