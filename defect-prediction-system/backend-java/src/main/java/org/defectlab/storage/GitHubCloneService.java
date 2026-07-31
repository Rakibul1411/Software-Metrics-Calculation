package org.metrics.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class GitHubCloneService {

    private static final long CLONE_TIMEOUT_SECONDS = 600;
    private static final int MAX_COMMAND_OUTPUT_BYTES = 16_384;
    private static final long MAX_ZIP_BYTES = 50L * 1024L * 1024L;
    private static final int DOWNLOAD_TIMEOUT_MILLIS = 120_000;

    private final Path cloneLocation = Paths.get("storage/extracted-projects");
    private final Path downloadLocation = Paths.get("storage/uploads");

    public GitHubCloneService() throws IOException {
        Files.createDirectories(cloneLocation);
        Files.createDirectories(downloadLocation);
    }

    public boolean isZipFileUrl(String gitUrl) {
        try {
            validateAndBuildRawZipUrl(gitUrl);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Path downloadZipFile(String gitUrl) throws IOException {
        URL rawUrl = validateAndBuildRawZipUrl(gitUrl);
        Path targetPath = downloadLocation.resolve("github_" + UUID.randomUUID() + ".zip")
                .toAbsolutePath().normalize();
        HttpURLConnection connection = (HttpURLConnection) rawUrl.openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(DOWNLOAD_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "defect-prediction-system");

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Unable to download the GitHub ZIP file (HTTP " + status + ").");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_ZIP_BYTES) {
                throw new IOException("The GitHub ZIP file must be 50 MB or smaller.");
            }

            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(targetPath,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > MAX_ZIP_BYTES) {
                        throw new IOException("The GitHub ZIP file must be 50 MB or smaller.");
                    }
                    output.write(buffer, 0, read);
                }
            }
            return targetPath;
        } catch (IOException exception) {
            Files.deleteIfExists(targetPath);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    public Path cloneRepository(String gitUrl) throws IOException {
        return cloneRepository(gitUrl, false);
    }

    public Path cloneRepository(String gitUrl, boolean fullHistory) throws IOException {
        return cloneRepository(parseTarget(gitUrl), fullHistory);
    }

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

    List<List<String>> cloneAttempts(String url, Path targetPath, boolean fullHistory) {
        return cloneAttempts(new GitHubTarget(url, null, ""), targetPath, fullHistory);
    }

    List<List<String>> cloneAttempts(GitHubTarget target, Path targetPath, boolean fullHistory) {
        List<List<String>> attempts = new ArrayList<>();
        if (fullHistory) {
            // AEEEM later calculates numstat for historical ranges. A partial
            // blobless clone would fetch missing blobs during that calculation,
            // allowing a transient GitHub error to destroy an otherwise
            // completed multi-snapshot extraction.
            attempts.add(cloneCommand(target, targetPath, true));
            attempts.add(cloneCommand(target, targetPath, false));
        } else {
            List<String> command = cloneCommand(target, targetPath, true);
            command.add(command.size() - 2, "--depth");
            command.add(command.size() - 2, "1");
            attempts.add(command);
        }
        return attempts;
    }

    private List<String> cloneCommand(GitHubTarget target, Path targetPath, boolean singleBranch) {
        List<String> command = new ArrayList<>(Arrays.asList(
                "git", "-c", "http.version=HTTP/1.1", "clone"));
        if (singleBranch) {
            command.add("--single-branch");
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

    URL validateAndBuildRawZipUrl(String gitUrl) {
        if (gitUrl == null || gitUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a GitHub ZIP file URL.");
        }
        try {
            URI uri = new URI(gitUrl.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            boolean githubHost = "github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host);
            boolean zipBlobUrl = path != null
                    && path.toLowerCase().matches("/[^/]+/[^/]+/blob/[^/]+/.+\\.zip");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !githubHost || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !zipBlobUrl) {
                throw new IllegalArgumentException("Use a public GitHub ZIP file URL ending in .zip.");
            }
            String rawPath = uri.getRawPath();
            String[] pathParts = rawPath.split("/", 6);
            return new URL("https://raw.githubusercontent.com/" + pathParts[1] + "/" + pathParts[2]
                    + "/" + pathParts[4] + "/" + pathParts[5]);
        } catch (URISyntaxException | IOException exception) {
            throw new IllegalArgumentException("Enter a valid GitHub ZIP file URL.");
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

    public static final class GitHubTarget {
        private final String repositoryUrl;
        private final String branch;
        private final String modulePath;

        private GitHubTarget(String repositoryUrl, String branch, String modulePath) {
            this.repositoryUrl = repositoryUrl;
            this.branch = branch == null || branch.trim().isEmpty() ? null : branch.trim();
            this.modulePath = org.metrics.aeeem.history.AeeemAnalysisOptions
                    .normalizeModulePath(modulePath);
        }

        public String getRepositoryUrl() {
            return repositoryUrl;
        }

        public String getBranch() {
            return branch;
        }

        public String getModulePath() {
            return modulePath;
        }
    }
}
