package org.metrics.defectlab.prediction.usecase.port;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/** Repository interface for persisting and querying defect prediction runs. */
public interface PredictionRunRepository {

    PredictionRun save(PredictionRun run);

    void delete(PredictionRun run);

    List<PredictionRun> findByUserId(Long userId);

    Optional<PredictionRun> findByIdAndUserId(Long id, Long userId);

    boolean existsByDatasetId(Long datasetId);
}
