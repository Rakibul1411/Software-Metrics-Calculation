package org.metrics.defectlab.dataset.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Maps dataset entities to the path-free summary returned by public APIs.
 */
public final class DatasetSummaryMapper {

    private DatasetSummaryMapper() {
    }

    public static Map<String, Object> toSummary(MetricDataset dataset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", dataset.getId());
        body.put("projectName", dataset.getProjectName());
        body.put("projectVersion", dataset.getProjectVersion());
        body.put("displayName", dataset.getDisplayName());
        body.put("datasetFamily", dataset.getDatasetFamily().name());
        body.put("datasetType", dataset.getDatasetType().name());
        body.put("hasActualLabel", dataset.hasActualLabel());
        body.put("totalFiles", dataset.getTotalFiles());
        body.put("totalMetrics", dataset.getTotalMetrics());
        body.put("systemDataset", dataset.getUserId() == null);
        body.put("createdAt", dataset.getCreatedAt().toString());
        return body;
    }
}
