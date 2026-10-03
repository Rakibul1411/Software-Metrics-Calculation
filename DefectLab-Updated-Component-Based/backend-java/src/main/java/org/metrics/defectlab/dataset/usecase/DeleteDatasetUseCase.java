package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;

/**
 * Handles deletion of user-owned datasets.
 */
public interface DeleteDatasetUseCase {

    void delete(Long userId, Long datasetId) throws IOException;
}
