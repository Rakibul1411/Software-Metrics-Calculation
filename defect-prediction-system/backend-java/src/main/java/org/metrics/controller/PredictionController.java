package org.metrics.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.metrics.common.dto.PredictionModelOptions;
import org.metrics.common.enums.DatasetFileFormat;
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
            @RequestParam(value = "targetFileFormat", required = false) String requestedTargetFormat,
            @RequestParam(value = "labelColumn", defaultValue = "bug") String labelColumn,
            @RequestParam(value = "classifierType", defaultValue = "knn") String classifierType,
            @RequestParam(value = "knnValue", defaultValue = "5") int knnValue,
            @RequestParam(value = "autoTuneK", defaultValue = "true") boolean autoTuneK,
            @RequestParam(value = "svmC", defaultValue = "1.0") double svmC,
            @RequestParam(value = "autoTuneSvmC", defaultValue = "true") boolean autoTuneSvmC,
            @RequestParam(value = "decisionThreshold", required = false) Double decisionThreshold,
            @RequestParam(value = "thresholdBeta", defaultValue = "2.0") double thresholdBeta,
            @RequestParam(value = "coralOption", defaultValue = "true") boolean coralOption,
            @RequestParam(value = "topK", defaultValue = "3") int topK) {

        if (sourceFiles == null || sourceFiles.length == 0) {
            return error(HttpStatus.BAD_REQUEST, "At least one labelled source dataset file must be provided.");
        }

        try {
            UUID.fromString(targetDatasetId);
            DatasetFileFormat fileFormat = requireCommonFormat(sourceFiles);
            if (requestedTargetFormat != null && !requestedTargetFormat.trim().isEmpty()
                    && fileFormat != DatasetFileFormat.fromExtension(requestedTargetFormat)) {
                throw new IllegalArgumentException(
                        "The requested target format must match the uploaded source dataset format.");
            }
            Path targetDatasetPath = metricsExtractionService.getDatasetPath(targetDatasetId, fileFormat);
            if (!Files.exists(targetDatasetPath)) {
                return error(HttpStatus.NOT_FOUND, "Target " + fileFormat.name()
                        + " dataset not found for ID: " + targetDatasetId);
            }
            PredictionModelOptions modelOptions = PredictionModelOptions.create(
                    classifierType, knnValue, autoTuneK, svmC, autoTuneSvmC,
                    decisionThreshold, thresholdBeta);
            Object result = predictionService.runPrediction(
                    targetDatasetPath, sourceFiles, labelColumn, coralOption, topK, modelOptions);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Prediction failed: " + exception.getMessage());
        }
    }

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluatePrediction(
            @RequestParam("targetFile") MultipartFile targetFile,
            @RequestParam("sourceFiles") MultipartFile[] sourceFiles,
            @RequestParam(value = "labelColumn", defaultValue = "bug") String labelColumn,
            @RequestParam(value = "classifierType", defaultValue = "knn") String classifierType,
            @RequestParam(value = "knnValue", defaultValue = "5") int knnValue,
            @RequestParam(value = "autoTuneK", defaultValue = "true") boolean autoTuneK,
            @RequestParam(value = "svmC", defaultValue = "1.0") double svmC,
            @RequestParam(value = "autoTuneSvmC", defaultValue = "true") boolean autoTuneSvmC,
            @RequestParam(value = "decisionThreshold", required = false) Double decisionThreshold,
            @RequestParam(value = "thresholdBeta", defaultValue = "2.0") double thresholdBeta,
            @RequestParam(value = "coralOption", defaultValue = "true") boolean coralOption,
            @RequestParam(value = "topK", defaultValue = "3") int topK) {
        if (targetFile == null || targetFile.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "A labelled target CSV or ARFF file must be provided.");
        }
        if (sourceFiles == null || sourceFiles.length == 0) {
            return error(HttpStatus.BAD_REQUEST, "At least one labelled source CSV or ARFF file must be provided.");
        }

        try {
            DatasetFileFormat sourceFormat = requireCommonFormat(sourceFiles);
            DatasetFileFormat targetFormat = DatasetFileFormat.fromFileName(targetFile.getOriginalFilename());
            if (sourceFormat != targetFormat) {
                throw new IllegalArgumentException(
                        "Source and target dataset formats must match: use CSV with CSV or ARFF with ARFF.");
            }
            PredictionModelOptions modelOptions = PredictionModelOptions.create(
                    classifierType, knnValue, autoTuneK, svmC, autoTuneSvmC,
                    decisionThreshold, thresholdBeta);
            Object result = predictionService.evaluatePrediction(
                    targetFile, sourceFiles, labelColumn, coralOption, topK, modelOptions);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Evaluation failed: " + exception.getMessage());
        }
    }

    private DatasetFileFormat requireCommonFormat(MultipartFile[] files) {
        DatasetFileFormat expected = null;
        for (MultipartFile file : files) {
            DatasetFileFormat current = DatasetFileFormat.fromFileName(file.getOriginalFilename());
            if (expected == null) {
                expected = current;
            } else if (expected != current) {
                throw new IllegalArgumentException(
                        "All source datasets must use one format; do not mix CSV and ARFF files.");
            }
        }
        return expected;
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
