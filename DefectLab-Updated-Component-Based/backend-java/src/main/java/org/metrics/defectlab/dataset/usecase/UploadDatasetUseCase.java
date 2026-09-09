package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Input boundary: registers a ready metric CSV/ARFF uploaded by a user. */
public interface UploadDatasetUseCase {

    MetricDataset upload(Long userId, String requestedProjectName, String projectVersion,
            MetricDataset.Type datasetType, UploadedFile file) throws IOException;
}
