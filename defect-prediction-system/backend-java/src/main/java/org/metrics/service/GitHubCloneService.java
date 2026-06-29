package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class GitHubCloneService {
    
    private final Path cloneLocation = Paths.get("storage/extracted-projects");

    public GitHubCloneService() throws IOException {
        Files.createDirectories(cloneLocation);
    }

    public Path cloneRepository(String gitUrl) throws IOException {
        String folderName = "git_" + UUID.randomUUID().toString();
        Path targetPath = cloneLocation.resolve(folderName);
        Files.createDirectories(targetPath);
        // Stub implementation for Git clone logic
        System.out.println("Cloning " + gitUrl + " into " + targetPath);
        return targetPath;
    }
}
