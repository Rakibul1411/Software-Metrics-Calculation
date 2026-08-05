package org.metrics.defectlab.dataset.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.prediction.infrastructure.MlServiceClient;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the feature registry and the per-dataset transformation preview. */
@RestController
@RequestMapping("/api/preprocessing")
public class PreprocessingController {

    private final MlServiceClient mlServiceClient;
    private final DatasetService datasetService;
    private final CurrentUser currentUser;

    public PreprocessingController(MlServiceClient mlServiceClient,
                                   DatasetService datasetService,
                                   CurrentUser currentUser) {
        this.mlServiceClient = mlServiceClient;
        this.datasetService = datasetService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{family}")
    public ResponseEntity<Map<String, Object>> registry(@PathVariable("family") String family,
                                                        HttpServletRequest request) {
        currentUser.requireUserId(request);
        return ResponseEntity.ok(mlServiceClient.registry(family));
    }

    /** Shows what log1p does to each feature of one dataset before scaling. */
    @GetMapping("/datasets/{id}/preview")
    public ResponseEntity<Map<String, Object>> preview(@PathVariable("id") Long id,
                                                       HttpServletRequest request) throws IOException {
        Long userId = currentUser.requireUserId(request);
        MetricDataset dataset = datasetService.require(userId, id);
        DatasetTable table = datasetService.load(dataset);
        return ResponseEntity.ok(mlServiceClient.preprocessingPreview(
                asRowMaps(table), dataset.getDatasetFamily().name()));
    }

    private List<Map<String, String>> asRowMaps(DatasetTable table) {
        List<String> headers = table.getHeaders();
        List<Map<String, String>> rows = new ArrayList<>(table.getRowCount());
        for (List<String> row : table.getRows()) {
            Map<String, String> mapped = new LinkedHashMap<>();
            for (int index = 0; index < headers.size(); index++) {
                mapped.put(headers.get(index), index < row.size() ? row.get(index) : "");
            }
            rows.add(mapped);
        }
        return rows;
    }
}
