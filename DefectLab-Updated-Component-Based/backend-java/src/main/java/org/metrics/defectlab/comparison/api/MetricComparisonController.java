package org.metrics.defectlab.comparison.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.comparison.application.MetricComparisonService;
import org.metrics.defectlab.comparison.domain.MetricComparison;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metric-comparisons")
public class MetricComparisonController {

    private final MetricComparisonService comparisonService;
    private final CurrentUser currentUser;

    public MetricComparisonController(
            MetricComparisonService comparisonService, CurrentUser currentUser) {
        this.comparisonService = comparisonService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body, HttpServletRequest request)
            throws IOException {
        return ResponseEntity.ok(comparisonService.execute(
                currentUser.requireUserId(request), body));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        return ResponseEntity.ok(comparisonService.list(userId).stream()
                .map(row -> comparisonService.summary(userId, row)).toList());
    }

    @GetMapping("/eligible-pairs")
    public ResponseEntity<List<Map<String, Object>>> eligiblePairs(
            HttpServletRequest request) {
        return ResponseEntity.ok(comparisonService.eligiblePairs(
                currentUser.requireUserId(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        MetricComparison comparison = comparisonService.require(userId, id);
        return ResponseEntity.ok(comparisonService.detail(userId, comparison));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        comparisonService.delete(currentUser.requireUserId(request), id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/report.pdf")
    public ResponseEntity<Resource> report(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = comparisonService.reportFile(
                currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"metric-comparison-" + id + ".pdf\"")
                .body(new FileSystemResource(file.toFile()));
    }
}
