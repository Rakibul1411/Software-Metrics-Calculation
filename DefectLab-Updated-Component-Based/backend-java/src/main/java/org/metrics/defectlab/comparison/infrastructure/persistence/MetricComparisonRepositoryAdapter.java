package org.metrics.defectlab.comparison.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.comparison.domain.MetricComparison;
import org.metrics.defectlab.comparison.usecase.port.MetricComparisonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Gateway: fulfils the {@link MetricComparisonRepository} port on top of Spring Data JPA. */
@Repository
public class MetricComparisonRepositoryAdapter implements MetricComparisonRepository {

    private final SpringDataMetricComparisonRepository jpaRepository;

    public MetricComparisonRepositoryAdapter(SpringDataMetricComparisonRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public MetricComparison save(MetricComparison comparison) {
        try {
            return toDomain(jpaRepository.saveAndFlush(toJpaEntity(comparison)));
        } catch (DataIntegrityViolationException exception) {
            throw new MetricComparisonRepository.SaveConflictException(exception);
        }
    }

    @Override
    public void delete(MetricComparison comparison) {
        jpaRepository.deleteById(comparison.getId());
    }

    @Override
    public List<MetricComparison> findAll() {
        return jpaRepository.findAll().stream()
                .map(MetricComparisonRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<MetricComparison> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(MetricComparisonRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<MetricComparison> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(MetricComparisonRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<MetricComparison> findByUserIdAndDatasetPair(
            Long userId, Long manualDatasetId, Long predefinedDatasetId) {
        return jpaRepository.findByUserIdAndManualDatasetIdAndPredefinedDatasetId(
                userId, manualDatasetId, predefinedDatasetId).map(MetricComparisonRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByDatasetId(Long datasetId) {
        return jpaRepository.existsByManualDatasetIdOrPredefinedDatasetId(datasetId, datasetId);
    }

    private static MetricComparisonJpaEntity toJpaEntity(MetricComparison comparison) {
        return new MetricComparisonJpaEntity(comparison.getId(), comparison.getUserId(),
                comparison.getManualDatasetId(), comparison.getPredefinedDatasetId(),
                comparison.getComparisonConfig(), comparison.getComparisonReportFilePath(),
                comparison.getCreatedAt());
    }

    private static MetricComparison toDomain(MetricComparisonJpaEntity entity) {
        return new MetricComparison(entity.getId(), entity.getUserId(), entity.getManualDatasetId(),
                entity.getPredefinedDatasetId(), entity.getComparisonConfig(),
                entity.getComparisonReportFilePath(), entity.getCreatedAt());
    }
}
