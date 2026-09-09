package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;

/** Input boundary: removes a dataset a user owns. */
public interface DeleteDatasetUseCase {

    void delete(Long userId, Long datasetId) throws IOException;
}
