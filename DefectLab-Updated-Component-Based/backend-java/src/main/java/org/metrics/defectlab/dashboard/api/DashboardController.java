package org.metrics.defectlab.dashboard.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.prediction.application.PredictionService;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.application.DatasetSummaryMapper;
import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CurrentUser currentUser;
    private final DatasetService datasetService;
    private final PredictionService predictionService;

    public DashboardController(CurrentUser currentUser, DatasetService datasetService,
                               PredictionService predictionService) {
        this.currentUser = currentUser;
        this.datasetService = datasetService;
        this.predictionService = predictionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> dashboard(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        List<MetricDataset> datasets = datasetService.list(userId);
        List<PredictionRun> predictions = predictionService.list(userId);

        long manual = datasets.stream().filter(dataset ->
                dataset.getDatasetType() == MetricDataset.Type.MANUAL).count();
        long predefined = datasets.stream().filter(dataset ->
                dataset.getDatasetType() == MetricDataset.Type.PREDEFINED).count();
        long labeled = datasets.stream().filter(MetricDataset::hasActualLabel).count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalDatasets", datasets.size());
        body.put("manualDatasets", manual);
        body.put("predefinedDatasets", predefined);
        body.put("labeledDatasets", labeled);
        body.put("comparisonRuns", predictions.size());
        body.put("recentDatasets", datasets.stream().limit(5)
                .map(DatasetSummaryMapper::toSummary).toList());
        body.put("recentRuns", predictions.stream().limit(5)
                .map(run -> predictionService.summary(userId, run)).toList());
        return ResponseEntity.ok(body);
    }
}
