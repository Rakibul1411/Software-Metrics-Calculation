package org.metrics.defectlab.dataset.usecase;

import java.util.LinkedHashMap;
import java.util.Map;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/**
 * Maps dataset entities to the path-free summary shared by every component
 * that needs to embed a dataset reference in its own use-case output. Lives
 * in the use-case ring (not {@code dataset.api}) so other components' use
 * cases can depend on it without reaching into dataset's interface adapters.
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
