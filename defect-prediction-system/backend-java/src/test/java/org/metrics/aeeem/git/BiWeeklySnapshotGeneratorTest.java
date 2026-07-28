package org.metrics.aeeem.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class BiWeeklySnapshotGeneratorTest {

    @TempDir
    Path repository;

    @Test
    void keepsAConfigurableRecentWindowAndAlwaysIncludesHead() throws Exception {
        git(null, "init");
        git(null, "config", "user.email", "metrics@example.com");
        git(null, "config", "user.name", "Metrics Test");
        LocalDate start = LocalDate.of(2024, 1, 1);
        Path source = repository.resolve("src/main/java/demo/Service.java");
        Files.createDirectories(source.getParent());
        for (int index = 0; index < 8; index++) {
            Files.write(source, ("package demo; public class Service { int value = "
                    + index + "; }").getBytes(StandardCharsets.UTF_8));
            git(null, "add", ".");
            git(start.plusDays(index * 14L).atStartOfDay()
                    .toInstant(ZoneOffset.UTC).toString(), "commit", "-m", "change-" + index);
        }

        List<BiWeeklySnapshotGenerator.Snapshot> snapshots =
                new BiWeeklySnapshotGenerator(3).generate(repository, "HEAD");

        assertEquals(3, snapshots.size());
        assertEquals(git(null, "rev-parse", "HEAD").trim(),
                snapshots.get(snapshots.size() - 1).getCommit());
    }

    @Test
    void selectsThePublishedJdtWindowAndExactReleaseTag() throws Exception {
        git(null, "init");
        git(null, "config", "user.email", "metrics@example.com");
        git(null, "config", "user.name", "Metrics Test");
        Path source = repository.resolve("org.eclipse.jdt.core/src/demo/Service.java");
        Files.createDirectories(source.getParent());

        Files.write(source, "class BeforeHistory {}".getBytes(StandardCharsets.UTF_8));
        git(null, "add", ".");
        git("2004-12-31T00:00:00Z", "commit", "-m", "before history");

        Files.write(source, "class HistoryStart {}".getBytes(StandardCharsets.UTF_8));
        git(null, "add", ".");
        git("2005-01-01T12:00:00Z", "commit", "-m", "history start");

        Files.write(source, "class Release {}".getBytes(StandardCharsets.UTF_8));
        git(null, "add", ".");
        git("2008-06-13T14:41:56Z", "commit", "-m", "JDT 3.4");
        git(null, "tag", "R3_4");

        AeeemAnalysisOptions options = AeeemAnalysisOptions.fromRequest(
                "jdt", null, "org.eclipse.jdt.core", null, null, null, null);
        List<BiWeeklySnapshotGenerator.Snapshot> snapshots =
                new BiWeeklySnapshotGenerator(26).generate(repository, "HEAD", options);

        assertEquals(91, snapshots.size());
        assertEquals(LocalDate.of(2005, 1, 1), snapshots.get(0).getDate());
        assertEquals(LocalDate.of(2008, 6, 17),
                snapshots.get(snapshots.size() - 1).getDate());
        assertEquals(git(null, "rev-parse", "R3_4").trim(),
                snapshots.get(snapshots.size() - 1).getCommit());
    }

    private String git(String date, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(repository.toFile())
                .redirectErrorStream(true);
        if (date != null) {
            Map<String, String> environment = builder.environment();
            environment.put("GIT_AUTHOR_DATE", date);
            environment.put("GIT_COMMITTER_DATE", date);
        }
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IllegalStateException(new String(output, StandardCharsets.UTF_8));
        }
        return new String(output, StandardCharsets.UTF_8);
    }
}
