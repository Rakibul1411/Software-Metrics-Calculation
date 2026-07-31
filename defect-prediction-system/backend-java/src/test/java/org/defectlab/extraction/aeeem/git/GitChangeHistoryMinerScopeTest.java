package org.metrics.aeeem.git;

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
import org.metrics.aeeem.history.AeeemAnalysisOptions;
import org.metrics.aeeem.history.GitChangePeriod;

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
