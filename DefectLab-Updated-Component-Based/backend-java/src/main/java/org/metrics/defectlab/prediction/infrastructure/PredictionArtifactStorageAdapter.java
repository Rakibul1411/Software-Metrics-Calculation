package org.metrics.defectlab.prediction.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.metrics.defectlab.prediction.usecase.port.ArtifactStorage;
import org.metrics.defectlab.shared.storage.StorageRoot;
import org.springframework.stereotype.Component;

/**
 * File system storage adapter for prediction run artifacts and reports.
 */
@Component
public class PredictionArtifactStorageAdapter implements ArtifactStorage {

    private final StorageRoot storageRoot;

    public PredictionArtifactStorageAdapter(StorageRoot storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public Path rootFor(String category) throws IOException {
        Path path = storageRoot.resolve(category);
        Files.createDirectories(path);
        return path;
    }
}
