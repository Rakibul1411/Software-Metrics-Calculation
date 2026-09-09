package org.metrics.defectlab.prediction.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.metrics.defectlab.prediction.usecase.port.PredictionRunRepository;
import org.springframework.stereotype.Repository;

/** Gateway: fulfils the {@link PredictionRunRepository} port on top of Spring Data JPA. */
@Repository
public class PredictionRunRepositoryAdapter implements PredictionRunRepository {

    private final SpringDataPredictionRunRepository jpaRepository;

    public PredictionRunRepositoryAdapter(SpringDataPredictionRunRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PredictionRun save(PredictionRun run) {
        return toDomain(jpaRepository.saveAndFlush(toJpaEntity(run)));
    }

    @Override
    public void delete(PredictionRun run) {
        jpaRepository.deleteById(run.getId());
    }

    @Override
    public List<PredictionRun> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PredictionRunRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<PredictionRun> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(PredictionRunRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByDatasetId(Long datasetId) {
        return jpaRepository.existsBySourceDatasetIdOrTargetDatasetId(datasetId, datasetId);
    }

    private static PredictionRunJpaEntity toJpaEntity(PredictionRun run) {
        return new PredictionRunJpaEntity(run.getId(), run.getUserId(), run.getComparisonGroupId(),
                run.getSourceDatasetId(), run.getTargetDatasetId(), run.getModelConfig(),
                run.getPredictionFilePath(), run.getReportFilePath(), run.getCreatedAt());
    }

    private static PredictionRun toDomain(PredictionRunJpaEntity entity) {
        return new PredictionRun(entity.getId(), entity.getUserId(), entity.getComparisonGroupId(),
                entity.getSourceDatasetId(), entity.getTargetDatasetId(), entity.getModelConfig(),
                entity.getPredictionFilePath(), entity.getReportFilePath(), entity.getCreatedAt());
    }
}
