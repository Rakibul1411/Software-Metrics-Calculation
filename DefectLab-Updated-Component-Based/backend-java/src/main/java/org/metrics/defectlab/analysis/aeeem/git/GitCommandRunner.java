package org.metrics.defectlab.analysis.aeeem.git;

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

    static GitResult runCommand(Path repository, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("http.version=HTTP/1.1");
        command.add("-c");
        command.add("http.lowSpeedLimit=1000");
        command.add("-c");
        command.add("http.lowSpeedTime=30");
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
            return new GitResult(process.exitValue(), text);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command was interrupted.", exception);
        } finally {
            process.destroy();
        }
    }

    static String run(Path repository, String... arguments) throws IOException {
        GitResult result = runCommand(repository, arguments);
        if (!result.isSuccess()) {
            throw new IOException("Git command failed: " + result.getOutput());
        }
        return result.getOutput();
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

    static final class GitResult {
        private final int exitCode;
        private final String output;

        GitResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        int getExitCode() {
            return exitCode;
        }

        String getOutput() {
            return output;
        }

        boolean isSuccess() {
            return exitCode == 0;
        }

        boolean isPromisorOrNetworkFailure() {
            String lower = output.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("promisor remote")
                    || lower.contains("could not fetch")
                    || lower.contains("failed to connect")
                    || lower.contains("couldn't connect")
                    || lower.contains("unable to access")
                    || lower.contains("connection timed out")
                    || lower.contains("timed out")
                    || lower.contains("network is unreachable")
                    || lower.contains("port 443");
        }
    }
}
