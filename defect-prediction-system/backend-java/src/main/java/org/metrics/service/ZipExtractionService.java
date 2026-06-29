package org.metrics.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;

@Service
public class ZipExtractionService {

    private static final int MAX_ENTRIES = 20_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 250L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 8192;

    private final Path extractLocation = Paths.get("storage/extracted-projects");

    public ZipExtractionService() throws IOException {
        Files.createDirectories(extractLocation);
    }

    public Path extractZipFile(Path zipFilePath) throws IOException {
        Path targetPath = extractLocation.resolve("zip_" + UUID.randomUUID()).toAbsolutePath().normalize();
        Files.createDirectories(targetPath);

        int entryCount = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream input = Files.newInputStream(zipFilePath);
             ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("The ZIP contains too many files.");
                }

                Path destination = targetPath.resolve(entry.getName()).normalize();
                if (!destination.startsWith(targetPath)) {
                    throw new IOException("The ZIP contains an unsafe file path.");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (java.io.OutputStream output = Files.newOutputStream(
                            destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException("The uncompressed ZIP is larger than 250 MB.");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        } catch (IOException exception) {
            FileStorageService.deleteRecursively(targetPath);
            throw exception;
        }

        if (entryCount == 0) {
            FileStorageService.deleteRecursively(targetPath);
            throw new IOException("The uploaded ZIP is empty or invalid.");
        }
        return targetPath;
    }
}
