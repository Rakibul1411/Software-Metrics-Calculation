package org.metrics.defectlab.shared.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.dataset.api.DatasetSummaryMapper;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.usecase.ListDatasetsUseCase;
import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.metrics.defectlab.prediction.usecase.GetPredictionSummaryUseCase;
import org.metrics.defectlab.prediction.usecase.ListPredictionRunsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interface Adapter: a small read composition over the dataset and
 * prediction components. It has no domain or use cases of its own, so it
 * lives here as a cross-component presenter rather than as a standalone
 * component.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CurrentUser currentUser;
    private final ListDatasetsUseCase listDatasetsUseCase;
    private final ListPredictionRunsUseCase listPredictionRunsUseCase;
    private final GetPredictionSummaryUseCase getPredictionSummaryUseCase;

    public DashboardController(CurrentUser currentUser, ListDatasetsUseCase listDatasetsUseCase,
                               ListPredictionRunsUseCase listPredictionRunsUseCase,
                               GetPredictionSummaryUseCase getPredictionSummaryUseCase) {
        this.currentUser = currentUser;
        this.listDatasetsUseCase = listDatasetsUseCase;
        this.listPredictionRunsUseCase = listPredictionRunsUseCase;
        this.getPredictionSummaryUseCase = getPredictionSummaryUseCase;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> dashboard(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        List<MetricDataset> datasets = listDatasetsUseCase.list(userId);
        List<PredictionRun> predictions = listPredictionRunsUseCase.list(userId);

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
                .map(run -> getPredictionSummaryUseCase.summary(userId, run)).toList());
        return ResponseEntity.ok(body);
    }
}
