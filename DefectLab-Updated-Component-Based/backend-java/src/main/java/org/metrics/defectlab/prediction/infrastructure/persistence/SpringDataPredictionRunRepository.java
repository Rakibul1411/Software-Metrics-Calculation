package org.metrics.defectlab.prediction.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPredictionRunRepository extends JpaRepository<PredictionRunJpaEntity, Long> {

    List<PredictionRunJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PredictionRunJpaEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsBySourceDatasetIdOrTargetDatasetId(Long sourceDatasetId, Long targetDatasetId);
}
