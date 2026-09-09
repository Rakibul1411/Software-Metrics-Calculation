package org.metrics.defectlab.composition;

import org.metrics.defectlab.comparison.usecase.port.MetricComparisonRepository;
import org.metrics.defectlab.dataset.usecase.port.DatasetUsageGuard;
import org.metrics.defectlab.prediction.usecase.port.PredictionRunRepository;
import org.springframework.stereotype.Component;

/**
 * Composition Root: the one place allowed to depend on more than one
 * component's ports purely to wire a cross-cutting concern. {@code dataset}
 * defines {@link DatasetUsageGuard} but never implements it itself, so it
 * has no outgoing dependency on {@code prediction} or {@code comparison} —
 * this adapter is what keeps the component dependency graph acyclic while
 * still answering "is this dataset still in use elsewhere".
 */
@Component
public class DatasetUsageGuardAdapter implements DatasetUsageGuard {

    private final PredictionRunRepository predictionRunRepository;
    private final MetricComparisonRepository metricComparisonRepository;

    public DatasetUsageGuardAdapter(PredictionRunRepository predictionRunRepository,
            MetricComparisonRepository metricComparisonRepository) {
        this.predictionRunRepository = predictionRunRepository;
        this.metricComparisonRepository = metricComparisonRepository;
    }

    @Override
    public boolean isReferencedElsewhere(Long datasetId) {
        return predictionRunRepository.existsByDatasetId(datasetId)
                || metricComparisonRepository.existsByDatasetId(datasetId);
    }
}
