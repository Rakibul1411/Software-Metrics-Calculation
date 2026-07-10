package org.metrics.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.metrics.service.FileStorageService;
import org.metrics.service.GitHubCloneService;
import org.metrics.service.MetricsExtractionService;
import org.metrics.service.ZipExtractionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsExtractionService metricsExtractionService;
    private final FileStorageService fileStorageService;
    private final ZipExtractionService zipExtractionService;
    private final GitHubCloneService gitHubCloneService;

    public MetricsController(MetricsExtractionService metricsExtractionService,
                             FileStorageService fileStorageService,
                             ZipExtractionService zipExtractionService,
                             GitHubCloneService gitHubCloneService) {
        this.metricsExtractionService = metricsExtractionService;
        this.fileStorageService = fileStorageService;
        this.zipExtractionService = zipExtractionService;
        this.gitHubCloneService = gitHubCloneService;
    }

    /** Extract metrics from either an uploaded Java project archive or a public GitHub repository. */
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MetricsExtractionService.ExtractionResult> extractMetrics(
            @RequestParam(value = "projectZip", required = false) MultipartFile projectZip,
            @RequestParam(value = "githubUrl", required = false) String githubUrl,
            @RequestParam(value = "datasetFormat", defaultValue = "promise") String datasetFormat) throws IOException {
        boolean hasZip = projectZip != null && !projectZip.isEmpty();
        boolean hasGitHubUrl = githubUrl != null && !githubUrl.trim().isEmpty();
        if (hasZip == hasGitHubUrl) {
            throw new IllegalArgumentException("Provide either one project archive or one GitHub repository URL.");
        }

        Path uploadedFile = null;
        Path sourceDirectory = null;
        try {
            if (hasZip) {
                uploadedFile = fileStorageService.storeUploadedFile(projectZip);
                sourceDirectory = zipExtractionService.extractArchiveFile(uploadedFile);
            } else if (gitHubCloneService.isZipFileUrl(githubUrl)) {
                uploadedFile = gitHubCloneService.downloadZipFile(githubUrl);
                sourceDirectory = zipExtractionService.extractArchiveFile(uploadedFile);
            } else {
                sourceDirectory = gitHubCloneService.cloneRepository(githubUrl);
            }
            return ResponseEntity.ok(metricsExtractionService.extractMetrics(
                    sourceDirectory.toString(), datasetFormat, null));
        } finally {
            FileStorageService.deleteRecursively(sourceDirectory);
            FileStorageService.deleteRecursively(uploadedFile);
        }
    }

    /** Backward-compatible local-path endpoint for trusted development environments. */
    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<MetricsExtractionService.ExtractionResult> extractMetricsFromLocalPath(
            @RequestParam("sourceDirectory") String sourceDirectory,
            @RequestParam(value = "datasetFormat", defaultValue = "promise") String datasetFormat,
            @RequestParam(value = "filterFile", required = false) String filterFile) throws IOException {
        return ResponseEntity.ok(metricsExtractionService.extractMetrics(sourceDirectory, datasetFormat, filterFile));
    }

    @GetMapping("/download/{datasetId}")
    public ResponseEntity<Resource> downloadDataset(@PathVariable("datasetId") String datasetId) {
        try {
            UUID.fromString(datasetId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid target dataset ID.");
        }
        Path filePath = metricsExtractionService.getDatasetPath(datasetId);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName() + "\"")
                .body(resource);
    }
}
