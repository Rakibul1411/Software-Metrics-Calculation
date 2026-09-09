package org.metrics.defectlab.comparison.usecase.port;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.comparison.domain.MetricComparison;

/** Output port: how use cases persist and look up metric comparisons, free of any framework detail. */
public interface MetricComparisonRepository {

    MetricComparison save(MetricComparison comparison);

    void delete(MetricComparison comparison);

    List<MetricComparison> findAll();

    List<MetricComparison> findByUserId(Long userId);

    Optional<MetricComparison> findByIdAndUserId(Long id, Long userId);

    Optional<MetricComparison> findByUserIdAndDatasetPair(
            Long userId, Long manualDatasetId, Long predefinedDatasetId);

    boolean existsByDatasetId(Long datasetId);

    /** Raised when a concurrent save conflicts with an existing row, free of any persistence-framework type. */
    class SaveConflictException extends RuntimeException {
        public SaveConflictException(Throwable cause) {
            super(cause);
        }
    }
}
