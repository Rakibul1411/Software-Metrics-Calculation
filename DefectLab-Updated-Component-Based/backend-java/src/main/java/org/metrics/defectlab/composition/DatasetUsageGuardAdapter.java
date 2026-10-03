package org.metrics.defectlab.composition;

import org.metrics.defectlab.comparison.usecase.port.MetricComparisonRepository;
import org.metrics.defectlab.dataset.usecase.port.DatasetUsageGuard;
import org.metrics.defectlab.prediction.usecase.port.PredictionRunRepository;
import org.springframework.stereotype.Component;

/**
 * Verifies whether a dataset is referenced by existing prediction runs or comparisons
 * before allowing deletion.
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
