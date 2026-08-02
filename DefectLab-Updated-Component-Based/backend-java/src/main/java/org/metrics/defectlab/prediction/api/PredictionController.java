package org.metrics.defectlab.prediction.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.prediction.application.PredictionService;
import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;
    private final CurrentUser currentUser;

    public PredictionController(PredictionService predictionService, CurrentUser currentUser) {
        this.predictionService = predictionService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body, HttpServletRequest request)
            throws IOException {
        return ResponseEntity.ok(predictionService.execute(
                currentUser.requireUserId(request), body));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        return ResponseEntity.ok(predictionService.list(userId).stream()
                .map(run -> predictionService.summary(userId, run)).toList());
    }

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> groups(HttpServletRequest request) {
        return ResponseEntity.ok(
                predictionService.grouped(currentUser.requireUserId(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        PredictionRun run = predictionService.require(userId, id);
        return ResponseEntity.ok(predictionService.detail(userId, run));
    }

    @GetMapping("/{id}/predictions")
    public ResponseEntity<List<Map<String, Object>>> predictions(
            @PathVariable("id") Long id,
            @RequestParam(value = "limit", defaultValue = "500") int limit,
            @RequestParam(value = "buggyOnly", defaultValue = "false")
            boolean buggyOnly,
            HttpServletRequest request) {
        return ResponseEntity.ok(predictionService.predictions(
                currentUser.requireUserId(request), id, limit, buggyOnly));
    }

    @GetMapping("/{id}/prediction.csv")
    public ResponseEntity<Resource> downloadPrediction(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = predictionService.predictionFile(
                currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"prediction-" + id + "-labeled.csv\"")
                .body(new FileSystemResource(file.toFile()));
    }

    @GetMapping("/{id}/report.pdf")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = predictionService.reportFile(currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"prediction-" + id + "-report.pdf\"")
                .body(new FileSystemResource(file.toFile()));
    }
}
