package org.metrics.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.metrics.service.MetricsExtractionService;
import org.metrics.service.PredictionService;
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

    private final MetricsExtractionService metricsExtractionService;
    private final PredictionService predictionService;

    public PredictionController(MetricsExtractionService metricsExtractionService,
                                PredictionService predictionService) {
        this.metricsExtractionService = metricsExtractionService;
        this.predictionService = predictionService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runPrediction(
            @RequestParam("targetDatasetId") String targetDatasetId,
            @RequestParam("sourceFiles") MultipartFile[] sourceFiles,
            @RequestParam(value = "labelColumn", defaultValue = "bug") String labelColumn,
            @RequestParam(value = "knnValue", defaultValue = "5") int knnValue,
            @RequestParam(value = "coralOption", defaultValue = "true") boolean coralOption,
            @RequestParam(value = "topK", defaultValue = "3") int topK) {

        Path targetCsvPath = metricsExtractionService.getDatasetPath(targetDatasetId);
        if (!Files.exists(targetCsvPath)) {
            return error(HttpStatus.NOT_FOUND, "Target dataset not found for ID: " + targetDatasetId);
        }

        if (sourceFiles == null || sourceFiles.length == 0) {
            return error(HttpStatus.BAD_REQUEST, "At least one labelled source dataset file must be provided.");
        }

        try {
            Object result = predictionService.runPrediction(
                    targetCsvPath, sourceFiles, labelColumn, knnValue, coralOption, topK);
            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Prediction failed: " + exception.getMessage());
        }
    }

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluatePrediction(
            @RequestParam("targetFile") MultipartFile targetFile,
            @RequestParam("sourceFiles") MultipartFile[] sourceFiles,
            @RequestParam(value = "labelColumn", defaultValue = "bug") String labelColumn,
            @RequestParam(value = "knnValue", defaultValue = "5") int knnValue,
            @RequestParam(value = "coralOption", defaultValue = "true") boolean coralOption,
            @RequestParam(value = "topK", defaultValue = "3") int topK) {
        if (targetFile == null || targetFile.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "A labelled target CSV file must be provided.");
        }
        if (sourceFiles == null || sourceFiles.length == 0) {
            return error(HttpStatus.BAD_REQUEST, "At least one labelled source CSV file must be provided.");
        }

        try {
            Object result = predictionService.evaluatePrediction(
                    targetFile, sourceFiles, labelColumn, knnValue, coralOption, topK);
            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Evaluation failed: " + exception.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
