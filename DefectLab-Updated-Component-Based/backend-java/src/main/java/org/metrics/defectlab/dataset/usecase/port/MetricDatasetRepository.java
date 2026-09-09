package org.metrics.defectlab.dataset.usecase.port;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Output port: how use cases persist and look up datasets, free of any framework detail. */
public interface MetricDatasetRepository {

    MetricDataset save(MetricDataset dataset);

    void delete(MetricDataset dataset);

    List<MetricDataset> findVisibleTo(Long userId);

    Optional<MetricDataset> findVisibleById(Long id, Long userId);

    Optional<MetricDataset> findSystemPredefined(
            String projectName, String projectVersion, MetricDataset.Type datasetType);

    boolean existsDuplicate(Long userId, MetricDataset.Family datasetFamily, String projectName,
            String projectVersion, MetricDataset.Type datasetType);

    /** Raised when a concurrent save conflicts with an existing row, free of any persistence-framework type. */
    class SaveConflictException extends RuntimeException {
        public SaveConflictException(Throwable cause) {
            super(cause);
        }
    }
}
