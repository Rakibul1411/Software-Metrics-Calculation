package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Registers bundled benchmark datasets into the system for global access.
 */
public interface RegisterPredefinedDatasetUseCase {

    MetricDataset registerPredefined(String projectName, String projectVersion, Path source)
            throws IOException;

    MetricDataset updatePredefined(MetricDataset existing, Path source) throws IOException;
}
