package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    
    private final Path uploadLocation = Paths.get("storage/uploads");

    public FileStorageService() throws IOException {
        Files.createDirectories(uploadLocation);
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

    public static boolean isSupportedArchive(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
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
                    // Temporary-file cleanup should not hide the extraction result.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
