package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ZipExtractionService {
    
    private final Path extractLocation = Paths.get("storage/extracted-projects");

    public ZipExtractionService() throws IOException {
        Files.createDirectories(extractLocation);
    }

    public Path extractZipFile(Path zipFilePath) throws IOException {
        String folderName = "zip_" + UUID.randomUUID().toString();
        Path targetPath = extractLocation.resolve(folderName);
        Files.createDirectories(targetPath);
        // Stub implementation for ZIP extraction logic
        System.out.println("Extracting " + zipFilePath + " into " + targetPath);
        return targetPath;
    }
}
