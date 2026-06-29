package org.metrics.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.metrics.service.MetricsExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    @Autowired
    private MetricsExtractionService metricsExtractionService;

    /**
     * POST /api/metrics/extract
     * Extracts metrics from the specified source directory.
     */
    @PostMapping("/extract")
    public ResponseEntity<?> extractMetrics(
            @RequestParam("sourceDirectory") String sourceDirectory,
            @RequestParam(value = "datasetFormat", defaultValue = "promise") String datasetFormat,
            @RequestParam(value = "filterFile", required = false) String filterFile) {
        try {
            MetricsExtractionService.ExtractionResult result = metricsExtractionService.extractMetrics(
                    sourceDirectory, datasetFormat, filterFile);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during metrics extraction: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/download/{datasetId}
     * Downloads the generated metrics CSV file.
     */
    @GetMapping("/download/{datasetId}")
    public ResponseEntity<Resource> downloadDataset(@PathVariable("datasetId") String datasetId) {
        Path filePath = metricsExtractionService.getDatasetPath(datasetId);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
    }
}
