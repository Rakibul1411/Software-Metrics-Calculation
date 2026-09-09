package org.metrics.defectlab.prediction.usecase;

import java.util.List;
import java.util.Map;

import org.metrics.defectlab.prediction.domain.PredictionRun;

/** Input boundary: builds display summaries for prediction runs, including grouped ones. */
public interface GetPredictionSummaryUseCase {

    Map<String, Object> summary(Long userId, PredictionRun run);

    Map<String, Object> detail(Long userId, PredictionRun run);

    List<Map<String, Object>> grouped(Long userId);
}
