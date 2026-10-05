package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Handles uploading and registering user-provided metric dataset files.
 */
public interface UploadDatasetUseCase {

    MetricDataset upload(Long userId, String requestedProjectName, String projectVersion,
            MetricDataset.Type datasetType, UploadedFile file) throws IOException;
}
