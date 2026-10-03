package org.metrics.defectlab.dataset.usecase.port;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Repository interface for persisting and retrieving metric datasets. */
public interface MetricDatasetRepository {

    MetricDataset save(MetricDataset dataset);

    void delete(MetricDataset dataset);

    List<MetricDataset> findVisibleTo(Long userId);

    Optional<MetricDataset> findVisibleById(Long id, Long userId);

    Optional<MetricDataset> findSystemPredefined(
            String projectName, String projectVersion, MetricDataset.Type datasetType);

    boolean existsDuplicate(Long userId, MetricDataset.Family datasetFamily, String projectName,
            String projectVersion, MetricDataset.Type datasetType);

    /** Thrown when a concurrent dataset registration violates unique constraint rules. */
    class SaveConflictException extends RuntimeException {
        public SaveConflictException(Throwable cause) {
            super(cause);
        }
    }
}
