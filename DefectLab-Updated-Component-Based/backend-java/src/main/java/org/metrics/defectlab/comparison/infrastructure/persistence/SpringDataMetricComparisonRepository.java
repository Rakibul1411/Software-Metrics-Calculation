package org.metrics.defectlab.comparison.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMetricComparisonRepository extends JpaRepository<MetricComparisonJpaEntity, Long> {

    List<MetricComparisonJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<MetricComparisonJpaEntity> findByIdAndUserId(Long id, Long userId);

    Optional<MetricComparisonJpaEntity>
            findByUserIdAndManualDatasetIdAndPredefinedDatasetId(
            Long userId, Long manualDatasetId, Long predefinedDatasetId);

    boolean existsByManualDatasetIdOrPredefinedDatasetId(
            Long manualDatasetId, Long predefinedDatasetId);
}
