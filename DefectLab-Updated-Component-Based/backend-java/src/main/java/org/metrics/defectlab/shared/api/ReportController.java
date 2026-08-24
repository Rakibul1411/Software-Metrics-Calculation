package org.metrics.defectlab.shared.api;

import java.nio.file.Path;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.prediction.usecase.GetPredictionArtifactUseCase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interface Adapter: a compatibility download route for clients that use
 * /api/reports. It has no domain or use cases of its own -- it only
 * re-exposes the prediction component's report artifact under a legacy
 * path -- so it lives here as a cross-component presenter rather than as
 * a standalone component.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final GetPredictionArtifactUseCase getPredictionArtifactUseCase;
    private final CurrentUser currentUser;

    public ReportController(GetPredictionArtifactUseCase getPredictionArtifactUseCase,
            CurrentUser currentUser) {
        this.getPredictionArtifactUseCase = getPredictionArtifactUseCase;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}.pdf")
    public ResponseEntity<Resource> pdf(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = getPredictionArtifactUseCase.reportFile(currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"defectlab-report-" + id + ".pdf\"")
                .body(new FileSystemResource(file.toFile()));
    }
}
