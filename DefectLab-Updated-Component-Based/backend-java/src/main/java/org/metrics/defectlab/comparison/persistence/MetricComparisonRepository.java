package org.metrics.defectlab.comparison.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.comparison.domain.MetricComparison;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricComparisonRepository extends JpaRepository<MetricComparison, Long> {

    List<MetricComparison> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<MetricComparison> findByIdAndUserId(Long id, Long userId);

    Optional<MetricComparison>
            findByUserIdAndManualDatasetIdAndPredefinedDatasetId(
            Long userId, Long manualDatasetId, Long predefinedDatasetId);

    boolean existsByManualDatasetIdOrPredefinedDatasetId(
            Long manualDatasetId, Long predefinedDatasetId);
}
