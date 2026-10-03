package org.metrics.defectlab.dataset.usecase;

import java.io.IOException;

import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.FeatureProfile;
import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Reads the tabular contents and feature profile of a metric dataset.
 */
public interface LoadDatasetTableUseCase {

    DatasetTable load(MetricDataset dataset) throws IOException;

    FeatureProfile profileFor(MetricDataset dataset);
}
