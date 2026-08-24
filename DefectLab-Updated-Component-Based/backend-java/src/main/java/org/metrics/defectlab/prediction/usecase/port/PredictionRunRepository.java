package org.metrics.defectlab.prediction.usecase.port;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/** Output port: how use cases persist and look up prediction runs, free of any framework detail. */
public interface PredictionRunRepository {

    PredictionRun save(PredictionRun run);

    void delete(PredictionRun run);

    List<PredictionRun> findByUserId(Long userId);

    Optional<PredictionRun> findByIdAndUserId(Long id, Long userId);

    boolean existsByDatasetId(Long datasetId);
}
