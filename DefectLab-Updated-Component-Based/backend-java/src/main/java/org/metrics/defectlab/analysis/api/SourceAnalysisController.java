package org.metrics.defectlab.analysis.api;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.analysis.usecase.AnalyzeSourceUseCase;
import org.metrics.defectlab.analysis.usecase.UploadedArchive;
import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.usecase.DatasetSummaryMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public source-analysis API for Java archives and public GitHub repositories.
 */
@RestController
@RequestMapping("/api/analysis")
public class SourceAnalysisController {

    private final AnalyzeSourceUseCase analyzeSourceUseCase;
    private final CurrentUser currentUser;

    public SourceAnalysisController(
            AnalyzeSourceUseCase analyzeSourceUseCase,
            CurrentUser currentUser) {
        this.analyzeSourceUseCase = analyzeSourceUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestParam(value = "projectArchive", required = false)
                    MultipartFile projectArchive,
            @RequestParam(value = "githubUrl", required = false) String githubUrl,
            @RequestParam(value = "projectName", required = false) String projectName,
            @RequestParam("projectVersion") String projectVersion,
            @RequestParam(value = "datasetFamily", defaultValue = "PROMISE")
                    String datasetFamily,
            @RequestParam(value = "aeeemProfile", defaultValue = "current")
                    String aeeemProfile,
            HttpServletRequest request) throws IOException {
        MetricDataset dataset = analyzeSourceUseCase.analyze(
                currentUser.requireUserId(request),
                toUploadedArchive(projectArchive),
                githubUrl,
                projectName,
                projectVersion,
                datasetFamily,
                aeeemProfile);
        return ResponseEntity.ok(DatasetSummaryMapper.toSummary(dataset));
    }

    private static UploadedArchive toUploadedArchive(MultipartFile file) throws IOException {
        if (file == null) {
            return null;
        }
        return new UploadedArchive(file.getInputStream(), file.getOriginalFilename(), file.getSize());
    }
}
