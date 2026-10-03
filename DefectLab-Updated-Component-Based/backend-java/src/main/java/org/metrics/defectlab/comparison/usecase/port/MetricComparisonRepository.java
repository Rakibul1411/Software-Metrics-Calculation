package org.metrics.defectlab.comparison.usecase.port;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/** Repository interface for persisting and querying metric comparison records. */
public interface MetricComparisonRepository {

    MetricComparison save(MetricComparison comparison);

    void delete(MetricComparison comparison);

    List<MetricComparison> findAll();

    List<MetricComparison> findByUserId(Long userId);

    Optional<MetricComparison> findByIdAndUserId(Long id, Long userId);

    Optional<MetricComparison> findByUserIdAndDatasetPair(
            Long userId, Long manualDatasetId, Long predefinedDatasetId);

    boolean existsByDatasetId(Long datasetId);

    /** Thrown when a concurrent save operation violates uniqueness constraints. */
    class SaveConflictException extends RuntimeException {
        public SaveConflictException(Throwable cause) {
            super(cause);
        }
    }
}
