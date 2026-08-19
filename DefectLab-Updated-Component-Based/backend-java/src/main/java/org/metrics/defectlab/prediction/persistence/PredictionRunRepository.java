package org.metrics.defectlab.prediction.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRunRepository extends JpaRepository<PredictionRun, Long> {

    List<PredictionRun> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PredictionRun> findByIdAndUserId(Long id, Long userId);

    boolean existsBySourceDatasetIdOrTargetDatasetId(Long sourceDatasetId, Long targetDatasetId);
}
