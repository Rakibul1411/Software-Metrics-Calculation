package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Registers metrics produced by source code extraction into the dataset repository.
 */
public interface RegisterExtractedDatasetUseCase {

    MetricDataset registerExtracted(Long userId, String projectName, String projectVersion,
            MetricDataset.Family expectedFamily, Path generatedCsv) throws IOException;
}
