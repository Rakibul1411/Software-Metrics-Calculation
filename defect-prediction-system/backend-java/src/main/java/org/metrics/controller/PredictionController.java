package org.metrics.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.metrics.service.MetricsExtractionService;
import org.metrics.service.PredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/prediction")
public class PredictionController {

    @Autowired
    private MetricsExtractionService metricsExtractionService;

    @Autowired
    private PredictionService predictionService;

    /**
     * POST /api/prediction/run
     * Runs defect prediction using a target dataset and labelled source files.
     */
    @PostMapping("/run")
    public ResponseEntity<?> runPrediction(
            @RequestParam("targetDatasetId") String targetDatasetId,
            @RequestParam("sourceFiles") MultipartFile[] sourceFiles,
            @RequestParam(value = "labelColumn", defaultValue = "bug") String labelColumn,
            @RequestParam(value = "knnValue", defaultValue = "5") int knnValue,
            @RequestParam(value = "coralOption", defaultValue = "true") boolean coralOption) {

        Path targetCsvPath = metricsExtractionService.getDatasetPath(targetDatasetId);
        if (!Files.exists(targetCsvPath)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Target dataset not found for ID: " + targetDatasetId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        if (sourceFiles == null || sourceFiles.length == 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "At least one labelled source dataset file must be provided.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        try {
            Object result = predictionService.runPrediction(
                    targetCsvPath, sourceFiles, labelColumn, knnValue, coralOption);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Prediction failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
