package org.metrics.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class GitHubCloneService {

    private static final long CLONE_TIMEOUT_SECONDS = 120;
    private static final int MAX_COMMAND_OUTPUT_BYTES = 16_384;

    private final Path cloneLocation = Paths.get("storage/extracted-projects");

    public GitHubCloneService() throws IOException {
        Files.createDirectories(cloneLocation);
    }

    public Path cloneRepository(String gitUrl) throws IOException {
        String normalizedUrl = validateAndNormalizeUrl(gitUrl);
        Path targetPath = cloneLocation.resolve("git_" + UUID.randomUUID()).toAbsolutePath().normalize();

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "git", "clone", "--depth", "1", "--single-branch", normalizedUrl, targetPath.toString());
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();

            final Process runningProcess = process;
            final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
            Thread outputReader = new Thread(() -> readOutput(runningProcess.getInputStream(), commandOutput));
            outputReader.setDaemon(true);
            outputReader.start();

            if (!process.waitFor(CLONE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("GitHub repository cloning timed out.");
            }
            outputReader.join(1000);

            if (process.exitValue() != 0) {
                String details = new String(commandOutput.toByteArray(), StandardCharsets.UTF_8).trim();
                throw new IOException(details.isEmpty()
                        ? "Unable to clone the GitHub repository."
                        : "Unable to clone the GitHub repository: " + details);
            }
            return targetPath;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("GitHub repository cloning was interrupted.", exception);
        } catch (IOException exception) {
            FileStorageService.deleteRecursively(targetPath);
            throw exception;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    String validateAndNormalizeUrl(String gitUrl) {
        if (gitUrl == null || gitUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a GitHub repository URL.");
        }
        try {
            URI uri = new URI(gitUrl.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            boolean githubHost = "github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !githubHost || uri.getUserInfo() != null
                    || path == null || !path.matches("/[^/]+/[^/]+/?")) {
                throw new IllegalArgumentException("Use an HTTPS GitHub repository URL such as https://github.com/owner/repository.");
            }
            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            return "https://github.com" + normalizedPath;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Enter a valid GitHub repository URL.");
        }
    }

    private static void readOutput(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[1024];
        int total = 0;
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (total < MAX_COMMAND_OUTPUT_BYTES) {
                    int accepted = Math.min(read, MAX_COMMAND_OUTPUT_BYTES - total);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
            }
        } catch (IOException ignored) {
            // The process exit code still gives the caller a reliable failure signal.
        }
    }
}
