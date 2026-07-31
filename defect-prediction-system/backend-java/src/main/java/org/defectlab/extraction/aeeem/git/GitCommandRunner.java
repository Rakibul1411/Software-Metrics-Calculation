package org.metrics.aeeem.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class GitCommandRunner {

    private static final long TIMEOUT_SECONDS = 180;

    private GitCommandRunner() {
    }

    static String run(Path repository, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repository.toAbsolutePath().normalize().toString());
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> copy(process.getInputStream(), output));
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Git command timed out: " + String.join(" ", arguments));
            }
            reader.join(1000);
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IOException("Git command failed: " + text);
            }
            return text;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command was interrupted.", exception);
        } finally {
            process.destroy();
        }
    }

    private static void copy(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[8192];
        try {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        } catch (IOException ignored) {
        }
    }
}
