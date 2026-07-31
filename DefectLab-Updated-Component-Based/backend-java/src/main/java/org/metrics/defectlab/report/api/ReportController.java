package org.metrics.defectlab.report.api;

import java.nio.file.Path;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.prediction.application.PredictionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compatibility download route for clients that use /api/reports. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final PredictionService predictionService;
    private final CurrentUser currentUser;

    public ReportController(PredictionService predictionService, CurrentUser currentUser) {
        this.predictionService = predictionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}.pdf")
    public ResponseEntity<Resource> pdf(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = predictionService.reportFile(currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"defectlab-report-" + id + ".pdf\"")
                .body(new FileSystemResource(file.toFile()));
    }
}
