package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final int MAX_FOLDER_FILES = 20_000;
    private static final long MAX_FOLDER_BYTES = 250L * 1024L * 1024L;

    private final Path uploadLocation = Paths.get("storage/uploads");
    private final Path folderLocation = Paths.get("storage/uploaded-folders");

    public FileStorageService() throws IOException {
        Files.createDirectories(uploadLocation);
        Files.createDirectories(folderLocation);
    }

    public Path storeUploadedFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty project archive file.");
        }
        String originalName = file.getOriginalFilename() == null ? "project-archive.zip" : file.getOriginalFilename();
        String safeName = Paths.get(originalName).getFileName().toString();
        if (!isSupportedArchive(safeName)) {
            throw new IllegalArgumentException("The project upload must be a .zip, .tar.gz, or .tgz file.");
        }
        String filename = UUID.randomUUID().toString() + "_" + safeName;
        Path targetPath = uploadLocation.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }

    /**
     * Stores a labelled PROMISE/AEEEM dataset whose first column lists the class
     * names to keep, so extraction can be scoped to a predefined release.
     */
    public Path storeClassFilterFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty predefined dataset file.");
        }
        String originalName = file.getOriginalFilename() == null
                ? "predefined-dataset.csv" : file.getOriginalFilename();
        String safeName = Paths.get(originalName).getFileName().toString();
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "The predefined dataset filter must be a .csv file whose first column holds class names.");
        }
        Path targetPath = uploadLocation.resolve(UUID.randomUUID() + "_" + safeName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }

    /**
     * Rebuilds a browser-selected project folder on disk.
     *
     * <p>The directory picker sends one part per file plus its
     * {@code webkitRelativePath}, so the original package layout has to be
     * recreated before the analyzers can resolve types across files.</p>
     */
    public Path storeProjectFolder(MultipartFile[] files, List<String> relativePaths) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Choose a project folder that contains Java sources.");
        }
        if (relativePaths == null || relativePaths.size() != files.length) {
            throw new IllegalArgumentException(
                    "Each uploaded project file requires one matching relative path.");
        }
        if (files.length > MAX_FOLDER_FILES) {
            throw new IllegalArgumentException(
                    "The selected folder holds more than " + MAX_FOLDER_FILES + " files.");
        }

        Path root = folderLocation.resolve("folder_" + UUID.randomUUID())
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
        try {
            long totalBytes = 0;
            int stored = 0;
            for (int index = 0; index < files.length; index++) {
                MultipartFile file = files[index];
                String relativePath = relativePaths.get(index);
                if (file == null || file.isEmpty() || relativePath == null
                        || !relativePath.toLowerCase(Locale.ROOT).endsWith(".java")) {
                    continue;
                }
                totalBytes += file.getSize();
                if (totalBytes > MAX_FOLDER_BYTES) {
                    throw new IOException("The selected folder is larger than 250 MB of Java source.");
                }
                Path destination = resolveInsideRoot(root, relativePath);
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                stored++;
            }
            if (stored == 0) {
                throw new IllegalArgumentException(
                        "The selected folder does not contain any .java files.");
            }
            return root;
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(root);
            throw exception;
        }
    }

    private static Path resolveInsideRoot(Path root, String relativePath) throws IOException {
        String cleaned = relativePath.replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            throw new IOException("The selected folder contains an empty file path.");
        }
        Path destination = root.resolve(cleaned).normalize();
        if (!destination.startsWith(root)) {
            throw new IOException("The selected folder contains an unsafe file path.");
        }
        return destination;
    }

    private static boolean isSupportedArchive(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
    }

    public static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
