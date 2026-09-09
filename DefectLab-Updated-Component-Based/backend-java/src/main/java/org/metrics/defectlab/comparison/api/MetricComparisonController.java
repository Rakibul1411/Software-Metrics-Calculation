package org.metrics.defectlab.comparison.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.comparison.domain.MetricComparison;
import org.metrics.defectlab.comparison.usecase.DeleteComparisonUseCase;
import org.metrics.defectlab.comparison.usecase.ExecuteComparisonUseCase;
import org.metrics.defectlab.comparison.usecase.GetComparisonReportFileUseCase;
import org.metrics.defectlab.comparison.usecase.GetComparisonSummaryUseCase;
import org.metrics.defectlab.comparison.usecase.GetComparisonUseCase;
import org.metrics.defectlab.comparison.usecase.GetEligiblePairsUseCase;
import org.metrics.defectlab.comparison.usecase.ListComparisonsUseCase;
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

/** Interface Adapter: translates HTTP requests into use-case calls and back. */
@RestController
@RequestMapping("/api/metric-comparisons")
public class MetricComparisonController {

    private final ExecuteComparisonUseCase executeComparisonUseCase;
    private final ListComparisonsUseCase listComparisonsUseCase;
    private final GetEligiblePairsUseCase getEligiblePairsUseCase;
    private final GetComparisonUseCase getComparisonUseCase;
    private final GetComparisonSummaryUseCase getComparisonSummaryUseCase;
    private final DeleteComparisonUseCase deleteComparisonUseCase;
    private final GetComparisonReportFileUseCase getComparisonReportFileUseCase;
    private final CurrentUser currentUser;

    public MetricComparisonController(
            ExecuteComparisonUseCase executeComparisonUseCase,
            ListComparisonsUseCase listComparisonsUseCase,
            GetEligiblePairsUseCase getEligiblePairsUseCase,
            GetComparisonUseCase getComparisonUseCase,
            GetComparisonSummaryUseCase getComparisonSummaryUseCase,
            DeleteComparisonUseCase deleteComparisonUseCase,
            GetComparisonReportFileUseCase getComparisonReportFileUseCase,
            CurrentUser currentUser) {
        this.executeComparisonUseCase = executeComparisonUseCase;
        this.listComparisonsUseCase = listComparisonsUseCase;
        this.getEligiblePairsUseCase = getEligiblePairsUseCase;
        this.getComparisonUseCase = getComparisonUseCase;
        this.getComparisonSummaryUseCase = getComparisonSummaryUseCase;
        this.deleteComparisonUseCase = deleteComparisonUseCase;
        this.getComparisonReportFileUseCase = getComparisonReportFileUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body, HttpServletRequest request)
            throws IOException {
        return ResponseEntity.ok(executeComparisonUseCase.execute(
                currentUser.requireUserId(request), body));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        return ResponseEntity.ok(listComparisonsUseCase.list(userId).stream()
                .map(row -> getComparisonSummaryUseCase.summary(userId, row)).toList());
    }

    @GetMapping("/eligible-pairs")
    public ResponseEntity<List<Map<String, Object>>> eligiblePairs(
            HttpServletRequest request) {
        return ResponseEntity.ok(getEligiblePairsUseCase.eligiblePairs(
                currentUser.requireUserId(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        MetricComparison comparison = getComparisonUseCase.require(userId, id);
        return ResponseEntity.ok(getComparisonSummaryUseCase.detail(userId, comparison));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        deleteComparisonUseCase.delete(currentUser.requireUserId(request), id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/report.pdf")
    public ResponseEntity<Resource> report(
            @PathVariable("id") Long id, HttpServletRequest request) {
        Path file = getComparisonReportFileUseCase.reportFile(
                currentUser.requireUserId(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"metric-comparison-" + id + ".pdf\"")
                .body(new FileSystemResource(file.toFile()));
    }
}
