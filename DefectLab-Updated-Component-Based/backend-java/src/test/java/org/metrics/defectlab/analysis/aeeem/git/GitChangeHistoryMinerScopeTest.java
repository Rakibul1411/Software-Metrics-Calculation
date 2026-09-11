package org.metrics.defectlab.analysis.aeeem.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.aeeem.history.GitChangePeriod;

class GitChangeHistoryMinerScopeTest {

    @TempDir
    Path repository;

    @Test
    void minesOnlyTheRequestedModule() throws Exception {
        git(null, "init");
        git(null, "config", "user.email", "metrics@example.com");
        git(null, "config", "user.name", "Metrics Test");
        Path selected = repository.resolve(
                "org.eclipse.jdt.core/src/demo/Core.java");
        Path sibling = repository.resolve(
                "org.eclipse.jdt.apt.core/src/demo/Apt.java");
        Files.createDirectories(selected.getParent());
        Files.createDirectories(sibling.getParent());
        Files.write(selected, "class Core {}".getBytes(StandardCharsets.UTF_8));
        Files.write(sibling, "class Apt {}".getBytes(StandardCharsets.UTF_8));
        git(null, "add", ".");
        git("2005-01-01T00:00:00Z", "commit", "-m", "initial");
        String first = git(null, "rev-parse", "HEAD").trim();

        Files.write(selected,
                "class Core { int value; }".getBytes(StandardCharsets.UTF_8));
        Files.write(sibling,
                "class Apt { int ignored; }".getBytes(StandardCharsets.UTF_8));
        git(null, "add", ".");
        git("2005-01-15T00:00:00Z", "commit", "-m", "both modules");
        String second = git(null, "rev-parse", "HEAD").trim();

        List<BiWeeklySnapshotGenerator.Snapshot> snapshots = Arrays.asList(
                new BiWeeklySnapshotGenerator.Snapshot(
                        first, LocalDate.of(2005, 1, 1),
                        LocalDate.of(2005, 1, 1)),
                new BiWeeklySnapshotGenerator.Snapshot(
                        second, LocalDate.of(2005, 1, 15),
                        LocalDate.of(2005, 1, 15)));
        AeeemAnalysisOptions options = AeeemAnalysisOptions.fromRequest(
                "current", "master", "org.eclipse.jdt.core",
                null, null, null, 0);

        List<GitChangePeriod> periods =
                new GitChangeHistoryMiner().mine(repository, snapshots, options);
        Map<String, Double> changes = periods.get(0).getChangedLinesByPath();

        assertEquals(1, periods.size());
        assertEquals(1, changes.size());
        assertTrue(changes.containsKey(
                "org.eclipse.jdt.core/src/demo/Core.java"));
        assertFalse(changes.containsKey(
                "org.eclipse.jdt.apt.core/src/demo/Apt.java"));
    }

    @Test
    void parsesNameStatusOutputCleanly() {
        String nameStatusOutput = "commit:d7e0c304d172fca497b655ba971da6ca54adad0f\n"
                + "M\torg.eclipse.jdt.core/compiler/ast/Expression.java\n"
                + "A\torg.eclipse.jdt.core/compiler/ast/NewClass.java\n"
                + "R100\torg.eclipse.jdt.core/compiler/ast/Old.java\torg.eclipse.jdt.core/compiler/ast/Renamed.java\n";

        List<GitNumstatParser.FileChange> changes = GitNumstatParser.parseNameStatus(nameStatusOutput, 10L);

        assertEquals(3, changes.size());
        assertEquals("d7e0c304d172fca497b655ba971da6ca54adad0f", changes.get(0).getCommit());
        assertEquals("org.eclipse.jdt.core/compiler/ast/Expression.java", changes.get(0).getNewPath());
        assertEquals(10L, changes.get(0).getChangedLines());
        assertTrue(changes.get(2).isRename());
        assertEquals("org.eclipse.jdt.core/compiler/ast/Old.java", changes.get(2).getOldPath());
        assertEquals("org.eclipse.jdt.core/compiler/ast/Renamed.java", changes.get(2).getNewPath());
    }

    @Test
    void parsesPartialNumstatOutputContainingFatalRemoteError() {
        String outputWithFatal = "commit:c16fc59ab5654c1c9ba882d21830089973cfaf23\n\n"
                + "2\t1\torg.eclipse.jdt.core/model/DeltaProcessor.java\n"
                + "commit:d7e0c304d172fca497b655ba971da6ca54adad0f\n\n"
                + "22\t3\torg.eclipse.jdt.core/compiler/ConditionalExpression.java\n"
                + "fatal: unable to access 'https://github.com/eclipse-jdt/eclipse.jdt.core/': Failed to connect to github.com port 443 after 75004 ms: Couldn't connect to server\n"
                + "fatal: could not fetch 1c9afd5a0b605b0b11c00c437ff11e072c99ca14 from promisor remote\n";

        List<GitNumstatParser.FileChange> changes = GitNumstatParser.parse(outputWithFatal);

        assertEquals(2, changes.size());
        assertEquals("org.eclipse.jdt.core/model/DeltaProcessor.java", changes.get(0).getNewPath());
        assertEquals(3L, changes.get(0).getChangedLines());
        assertEquals("org.eclipse.jdt.core/compiler/ConditionalExpression.java", changes.get(1).getNewPath());
        assertEquals(25L, changes.get(1).getChangedLines());
    }

    private String git(String date, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(repository.toFile()).redirectErrorStream(true);
        if (date != null) {
            Map<String, String> environment = builder.environment();
            environment.put("GIT_AUTHOR_DATE", date);
            environment.put("GIT_COMMITTER_DATE", date);
        }
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IllegalStateException(
                    new String(output, StandardCharsets.UTF_8));
        }
        return new String(output, StandardCharsets.UTF_8);
    }
}
