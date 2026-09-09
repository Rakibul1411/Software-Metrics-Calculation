package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;
import java.nio.file.Path;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Input boundary: registers bundled/public predefined data without creating
 * another table. The nullable owner makes these rows visible to every user.
 */
public interface RegisterPredefinedDatasetUseCase {

    MetricDataset registerPredefined(String projectName, String projectVersion, Path source)
            throws IOException;
}
