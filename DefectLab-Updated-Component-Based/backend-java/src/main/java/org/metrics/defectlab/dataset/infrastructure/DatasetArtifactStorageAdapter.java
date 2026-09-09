package org.metrics.defectlab.dataset.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.usecase.port.ArtifactStorage;
import org.metrics.defectlab.shared.storage.StorageRoot;
import org.springframework.stereotype.Component;

/** Gateway: fulfils the {@link ArtifactStorage} port on top of the shared {@link StorageRoot}. */
@Component
public class DatasetArtifactStorageAdapter implements ArtifactStorage {

    private final StorageRoot storageRoot;

    public DatasetArtifactStorageAdapter(StorageRoot storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public Path rootFor(String category) throws IOException {
        Path path = storageRoot.resolve(category);
        Files.createDirectories(path);
        return path;
    }
}
