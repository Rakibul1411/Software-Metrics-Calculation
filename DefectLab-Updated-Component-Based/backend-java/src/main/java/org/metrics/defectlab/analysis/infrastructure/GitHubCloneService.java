package org.metrics.defectlab.analysis.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.metrics.defectlab.analysis.usecase.port.GitHubRepositoryClient;
import org.metrics.defectlab.shared.storage.StorageRoot;
import org.springframework.stereotype.Service;

@Service
public class GitHubCloneService implements GitHubRepositoryClient {

    private static final long CLONE_TIMEOUT_SECONDS = 600;
    private static final int MAX_COMMAND_OUTPUT_BYTES = 16_384;

    private final Path cloneLocation;

    public GitHubCloneService(StorageRoot storageRoot) throws IOException {
        this.cloneLocation = storageRoot.resolve("extracted-projects");
        Files.createDirectories(cloneLocation);
    }

    public Path cloneRepository(String gitUrl) throws IOException {
        return cloneRepository(gitUrl, false);
    }

    public Path cloneRepository(String gitUrl, boolean fullHistory) throws IOException {
        return cloneRepository(parseTarget(gitUrl), fullHistory);
    }

    @Override
    public Path cloneRepository(GitHubTarget target, boolean fullHistory) throws IOException {
        Path targetPath = cloneLocation.resolve("git_" + UUID.randomUUID()).toAbsolutePath().normalize();
        try {
            List<List<String>> attempts = cloneAttempts(target, targetPath, fullHistory);
            IOException lastFailure = null;
            for (List<String> command : attempts) {
                FileStorageService.deleteRecursively(targetPath);
                try {
                    runClone(command);
                    return targetPath;
                } catch (IOException exception) {
                    lastFailure = exception;
                }
            }
            throw lastFailure == null ? new IOException("Unable to clone the GitHub repository.") : lastFailure;
        } catch (IOException exception) {
            FileStorageService.deleteRecursively(targetPath);
            throw exception;
        }
    }

    /** Materialises the default branch after the intentionally no-checkout clone. */
    @Override
    public void checkoutHead(Path repository) throws IOException {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "git", "-C", repository.toString(), "checkout", "--force", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            readOutput(process.getInputStream(), output);
            if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
                String details = output.toString(StandardCharsets.UTF_8).trim();
                throw new IOException(details.isEmpty()
                        ? "Unable to materialise the GitHub working tree."
                        : "Unable to materialise the GitHub working tree: " + details);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("GitHub checkout was interrupted.", exception);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    List<List<String>> cloneAttempts(String url, Path targetPath, boolean fullHistory) {
        return cloneAttempts(new GitHubTarget(url, null, ""), targetPath, fullHistory);
    }

    List<List<String>> cloneAttempts(GitHubTarget target, Path targetPath, boolean fullHistory) {
        List<List<String>> attempts = new ArrayList<>();
        if (fullHistory) {
            // AEEEM needs the complete commit graph, but it does not need every
            // historical blob before analysis starts. Large repositories such
            // as eclipse.jdt.core cannot reliably transfer all blobs inside the
            // clone deadline. Git fetches the required source blobs on demand
            // while the selected snapshots are materialised.
            attempts.add(cloneCommand(target, targetPath, true, true));
            // Keep a regular clone for Git servers that do not support partial
            // clone filtering.
            attempts.add(cloneCommand(target, targetPath, false, false));
        } else {
            List<String> command = cloneCommand(target, targetPath, true, false);
            command.add(command.size() - 2, "--depth");
            command.add(command.size() - 2, "1");
            attempts.add(command);
        }
        return attempts;
    }

    private List<String> cloneCommand(
            GitHubTarget target,
            Path targetPath,
            boolean singleBranch,
            boolean blobless) {
        List<String> command = new ArrayList<>(Arrays.asList(
                "git", "-c", "http.version=HTTP/1.1", "clone"));
        if (singleBranch) {
            command.add("--single-branch");
        }
        if (blobless) {
            command.add("--filter=blob:none");
        }
        command.add("--no-checkout");
        if (target.getBranch() != null) {
            command.add("--branch");
            command.add(target.getBranch());
        }
        command.add(target.getRepositoryUrl());
        command.add(targetPath.toString());
        return command;
    }

    private void runClone(List<String> command) throws IOException {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("GitHub repository cloning was interrupted.", exception);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    String validateAndNormalizeUrl(String gitUrl) {
        return parseTarget(gitUrl).getRepositoryUrl();
    }

    @Override
    public GitHubTarget parseTarget(String gitUrl) {
        if (gitUrl == null || gitUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a GitHub repository URL.");
        }
        try {
            URI uri = new URI(gitUrl.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            boolean githubHost = "github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !githubHost
                    || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || path == null) {
                throw new IllegalArgumentException("Use an HTTPS GitHub repository URL or folder URL such as https://github.com/owner/repository/tree/main/src.");
            }
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            String[] pathParts = decodedPath.split("/");
            if (pathParts.length < 3 || pathParts[1].isEmpty() || pathParts[2].isEmpty()) {
                throw new IllegalArgumentException("Use an HTTPS GitHub repository URL or folder URL such as https://github.com/owner/repository/tree/main/src.");
            }
            String repositoryName = pathParts[2].endsWith(".git")
                    ? pathParts[2].substring(0, pathParts[2].length() - 4)
                    : pathParts[2];
            String branch = null;
            StringBuilder module = new StringBuilder();
            if (pathParts.length > 3) {
                if (pathParts.length < 5 || !"tree".equals(pathParts[3])
                        || pathParts[4].isEmpty()) {
                    throw new IllegalArgumentException("Use an HTTPS GitHub repository URL or folder URL such as https://github.com/owner/repository/tree/main/src.");
                }
                branch = pathParts[4];
                for (int index = 5; index < pathParts.length; index++) {
                    if (pathParts[index].isEmpty()) {
                        continue;
                    }
                    if (module.length() > 0) {
                        module.append('/');
                    }
                    module.append(pathParts[index]);
                }
            }
            return new GitHubTarget(
                    "https://github.com/" + pathParts[1] + "/" + repositoryName,
                    branch,
                    module.toString());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Enter a valid GitHub repository URL.");
        } catch (IOException exception) {
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
