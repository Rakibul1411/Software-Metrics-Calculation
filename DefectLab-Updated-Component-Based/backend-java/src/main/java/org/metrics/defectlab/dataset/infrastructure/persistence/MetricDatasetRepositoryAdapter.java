package org.metrics.defectlab.dataset.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.usecase.port.MetricDatasetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Gateway: fulfils the {@link MetricDatasetRepository} port on top of Spring Data JPA. */
@Repository
public class MetricDatasetRepositoryAdapter implements MetricDatasetRepository {

    private final SpringDataMetricDatasetRepository jpaRepository;

    public MetricDatasetRepositoryAdapter(SpringDataMetricDatasetRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public MetricDataset save(MetricDataset dataset) {
        try {
            return toDomain(jpaRepository.saveAndFlush(toJpaEntity(dataset)));
        } catch (DataIntegrityViolationException exception) {
            throw new MetricDatasetRepository.SaveConflictException(exception);
        }
    }

    @Override
    public void delete(MetricDataset dataset) {
        jpaRepository.deleteById(dataset.getId());
    }

    @Override
    public List<MetricDataset> findVisibleTo(Long userId) {
        return jpaRepository.findVisibleTo(userId).stream()
                .map(MetricDatasetRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<MetricDataset> findVisibleById(Long id, Long userId) {
        return jpaRepository.findVisibleById(id, userId).map(MetricDatasetRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<MetricDataset> findSystemPredefined(
            String projectName, String projectVersion, MetricDataset.Type datasetType) {
        return jpaRepository
                .findByUserIdIsNullAndProjectNameIgnoreCaseAndProjectVersionAndDatasetType(
                        projectName, projectVersion, datasetType)
                .map(MetricDatasetRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsDuplicate(Long userId, MetricDataset.Family datasetFamily, String projectName,
            String projectVersion, MetricDataset.Type datasetType) {
        return jpaRepository.existsByUserIdAndDatasetFamilyAndProjectNameAndProjectVersionAndDatasetType(
                userId, datasetFamily, projectName, projectVersion, datasetType);
    }

    private static MetricDatasetJpaEntity toJpaEntity(MetricDataset dataset) {
        return new MetricDatasetJpaEntity(dataset.getId(), dataset.getUserId(), dataset.getDatasetFamily(),
                dataset.getProjectName(), dataset.getProjectVersion(), dataset.getDatasetType(),
                dataset.hasActualLabel(), dataset.getTotalFiles(), dataset.getTotalMetrics(),
                dataset.getMetricsFilePath(), dataset.getCreatedAt());
    }

    private static MetricDataset toDomain(MetricDatasetJpaEntity entity) {
        return new MetricDataset(entity.getId(), entity.getUserId(), entity.getDatasetFamily(),
                entity.getProjectName(), entity.getProjectVersion(), entity.getDatasetType(),
                entity.isHasActualLabel(), entity.getTotalFiles(), entity.getTotalMetrics(),
                entity.getMetricsFilePath(), entity.getCreatedAt());
    }
}
