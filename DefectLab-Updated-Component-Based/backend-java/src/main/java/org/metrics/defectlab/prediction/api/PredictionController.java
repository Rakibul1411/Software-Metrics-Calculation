package org.metrics.defectlab.prediction.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.metrics.defectlab.prediction.usecase.DeletePredictionRunUseCase;
import org.metrics.defectlab.prediction.usecase.ExecutePredictionUseCase;
import org.metrics.defectlab.prediction.usecase.GetPredictionArtifactUseCase;
import org.metrics.defectlab.prediction.usecase.GetPredictionRowsUseCase;
import org.metrics.defectlab.prediction.usecase.GetPredictionRunUseCase;
import org.metrics.defectlab.prediction.usecase.GetPredictionSummaryUseCase;
import org.metrics.defectlab.prediction.usecase.ListPredictionRunsUseCase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Interface Adapter: translates HTTP requests into use-case calls and back. */
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final ExecutePredictionUseCase executePredictionUseCase;
    private final ListPredictionRunsUseCase listPredictionRunsUseCase;
    private final GetPredictionRunUseCase getPredictionRunUseCase;
    private final DeletePredictionRunUseCase deletePredictionRunUseCase;
    private final GetPredictionSummaryUseCase getPredictionSummaryUseCase;
    private final GetPredictionRowsUseCase getPredictionRowsUseCase;
    private final GetPredictionArtifactUseCase getPredictionArtifactUseCase;
    private final CurrentUser currentUser;

    public PredictionController(ExecutePredictionUseCase executePredictionUseCase,
            ListPredictionRunsUseCase listPredictionRunsUseCase,
            GetPredictionRunUseCase getPredictionRunUseCase,
            DeletePredictionRunUseCase deletePredictionRunUseCase,
            GetPredictionSummaryUseCase getPredictionSummaryUseCase,
            GetPredictionRowsUseCase getPredictionRowsUseCase,
            GetPredictionArtifactUseCase getPredictionArtifactUseCase,
            CurrentUser currentUser) {
        this.executePredictionUseCase = executePredictionUseCase;
        this.listPredictionRunsUseCase = listPredictionRunsUseCase;
        this.getPredictionRunUseCase = getPredictionRunUseCase;
        this.deletePredictionRunUseCase = deletePredictionRunUseCase;
        this.getPredictionSummaryUseCase = getPredictionSummaryUseCase;
        this.getPredictionRowsUseCase = getPredictionRowsUseCase;
        this.getPredictionArtifactUseCase = getPredictionArtifactUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body, HttpServletRequest request)
            throws IOException {
        return ResponseEntity.ok(executePredictionUseCase.execute(
                currentUser.requireUserId(request), body));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        return ResponseEntity.ok(listPredictionRunsUseCase.list(userId).stream()
                .map(run -> getPredictionSummaryUseCase.summary(userId, run)).toList());
    }

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> groups(HttpServletRequest request) {
        return ResponseEntity.ok(
                getPredictionSummaryUseCase.grouped(currentUser.requireUserId(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        PredictionRun run = getPredictionRunUseCase.require(userId, id);
        return ResponseEntity.ok(getPredictionSummaryUseCase.detail(userId, run));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        deletePredictionRunUseCase.delete(currentUser.requireUserId(request), id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/predictions")
    public ResponseEntity<List<Map<String, Object>>> predictions(
            @PathVariable("id") Long id,
            @RequestParam(value = "limit", defaultValue = "500") int limit,
            @RequestParam(value = "buggyOnly", defaultValue = "false")
            boolean buggyOnly,
            HttpServletRequest request) {
        return ResponseEntity.ok(getPredictionRowsUseCase.predictions(
                currentUser.requireUserId(request), id, limit, buggyOnly));
    }

    @GetMapping("/{id}/prediction.csv")
    public ResponseEntity<Resource> downloadPrediction(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = getPredictionArtifactUseCase.predictionFile(
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
        Path file = getPredictionArtifactUseCase.reportFile(currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"prediction-" + id + "-report.pdf\"")
                .body(new FileSystemResource(file.toFile()));
    }
}
