package org.metrics.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.metrics.common.exception.ExtractionBusyException;
import org.metrics.common.enums.DatasetFileFormat;
import org.metrics.aeeem.history.AeeemAnalysisOptions;
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

    private final Semaphore aeeemExtractionSlot = new Semaphore(1, true);
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
            @RequestParam(value = "projectFiles", required = false) MultipartFile[] projectFiles,
            @RequestParam(value = "projectFilePaths", required = false) List<String> projectFilePaths,
            @RequestParam(value = "labelFilterCsv", required = false) MultipartFile labelFilterCsv,
            @RequestParam(value = "datasetFormat", defaultValue = "promise") String datasetFormat,
            @RequestParam(value = "aeeemProfile", defaultValue = "current") String aeeemProfile,
            @RequestParam(value = "aeeemHistoryStart", required = false) String aeeemHistoryStart,
            @RequestParam(value = "aeeemReleaseDate", required = false) String aeeemReleaseDate,
            @RequestParam(value = "aeeemReleaseRef", required = false) String aeeemReleaseRef,
            @RequestParam(value = "aeeemModulePath", required = false) String aeeemModulePath,
            @RequestParam(value = "aeeemMaxSnapshots", required = false) Integer aeeemMaxSnapshots)
            throws IOException {
        boolean hasZip = projectZip != null && !projectZip.isEmpty();
        boolean hasGitHubUrl = githubUrl != null && !githubUrl.trim().isEmpty();
        boolean hasFolder = projectFiles != null && projectFiles.length > 0;
        if ((hasZip ? 1 : 0) + (hasGitHubUrl ? 1 : 0) + (hasFolder ? 1 : 0) != 1) {
            throw new IllegalArgumentException("Provide exactly one project source: "
                    + "an archive, a project folder, or a GitHub repository URL.");
        }

        boolean aeeemSlotAcquired = acquireAeeemSlot(datasetFormat);
        Path uploadedFile = null;
        Path sourceDirectory = null;
        Path cleanupDirectory = null;
        Path classFilterFile = null;
        AeeemAnalysisOptions aeeemOptions = AeeemAnalysisOptions.fromRequest(
                aeeemProfile, null, aeeemModulePath, aeeemHistoryStart,
                aeeemReleaseDate, aeeemReleaseRef, aeeemMaxSnapshots);
        try {
            if (labelFilterCsv != null && !labelFilterCsv.isEmpty()) {
                classFilterFile = fileStorageService.storeClassFilterFile(labelFilterCsv);
            }
            if (hasFolder) {
                if (isAeeem(datasetFormat)) {
                    throw new IllegalArgumentException(
                            "AEEEM extraction requires repository history; use a GitHub repository URL instead of a folder upload.");
                }
                sourceDirectory = fileStorageService.storeProjectFolder(projectFiles, projectFilePaths);
                cleanupDirectory = sourceDirectory;
            } else if (hasZip) {
                uploadedFile = fileStorageService.storeUploadedFile(projectZip);
                sourceDirectory = zipExtractionService.extractArchiveFile(uploadedFile);
                cleanupDirectory = sourceDirectory;
            } else if (gitHubCloneService.isZipFileUrl(githubUrl)) {
                if (isAeeem(datasetFormat)) {
                    throw new IllegalArgumentException(
                            "AEEEM extraction requires repository history; use the GitHub repository URL, not a ZIP file URL.");
                }
                uploadedFile = gitHubCloneService.downloadZipFile(githubUrl);
                sourceDirectory = zipExtractionService.extractArchiveFile(uploadedFile);
                cleanupDirectory = sourceDirectory;
            } else {
                GitHubCloneService.GitHubTarget target =
                        gitHubCloneService.parseTarget(githubUrl);
                if (isAeeem(datasetFormat)) {
                    aeeemOptions.getProfile().requireRecommendedRepository(
                            target.getRepositoryUrl());
                }
                sourceDirectory = gitHubCloneService.cloneRepository(
                        target, isAeeem(datasetFormat));
                cleanupDirectory = sourceDirectory;
                if (isAeeem(datasetFormat)) {
                    String modulePath = aeeemModulePath == null
                            || aeeemModulePath.trim().isEmpty()
                            ? target.getModulePath() : aeeemModulePath;
                    aeeemOptions = AeeemAnalysisOptions.fromRequest(
                            aeeemProfile, target.getBranch(), modulePath,
                            aeeemHistoryStart, aeeemReleaseDate,
                            aeeemReleaseRef, aeeemMaxSnapshots);
                } else if (!target.getModulePath().isEmpty()) {
                    Path scopedSource = sourceDirectory.resolve(
                            target.getModulePath()).normalize();
                    if (!scopedSource.startsWith(sourceDirectory)
                            || !Files.isDirectory(scopedSource)) {
                        throw new IllegalArgumentException(
                                "The requested GitHub folder does not exist on the selected branch.");
                    }
                    sourceDirectory = scopedSource;
                }
            }
            return ResponseEntity.ok(metricsExtractionService.extractMetrics(
                    sourceDirectory.toString(), datasetFormat,
                    classFilterFile == null ? null : classFilterFile.toString(),
                    aeeemOptions));
        } finally {
            FileStorageService.deleteRecursively(
                    cleanupDirectory == null ? sourceDirectory : cleanupDirectory);
            FileStorageService.deleteRecursively(uploadedFile);
            FileStorageService.deleteRecursively(classFilterFile);
            releaseAeeemSlot(aeeemSlotAcquired);
        }
    }

    /** Backward-compatible local-path endpoint for trusted development environments. */
    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<MetricsExtractionService.ExtractionResult> extractMetricsFromLocalPath(
            @RequestParam("sourceDirectory") String sourceDirectory,
            @RequestParam(value = "datasetFormat", defaultValue = "promise") String datasetFormat,
            @RequestParam(value = "filterFile", required = false) String filterFile,
            @RequestParam(value = "aeeemProfile", defaultValue = "current") String aeeemProfile,
            @RequestParam(value = "aeeemHistoryStart", required = false) String aeeemHistoryStart,
            @RequestParam(value = "aeeemReleaseDate", required = false) String aeeemReleaseDate,
            @RequestParam(value = "aeeemReleaseRef", required = false) String aeeemReleaseRef,
            @RequestParam(value = "aeeemModulePath", required = false) String aeeemModulePath,
            @RequestParam(value = "aeeemMaxSnapshots", required = false) Integer aeeemMaxSnapshots)
            throws IOException {
        boolean aeeemSlotAcquired = acquireAeeemSlot(datasetFormat);
        try {
            AeeemAnalysisOptions options = AeeemAnalysisOptions.fromRequest(
                    aeeemProfile, null, aeeemModulePath, aeeemHistoryStart,
                    aeeemReleaseDate, aeeemReleaseRef, aeeemMaxSnapshots);
            return ResponseEntity.ok(
                    metricsExtractionService.extractMetrics(
                            sourceDirectory, datasetFormat, filterFile, options));
        } finally {
            releaseAeeemSlot(aeeemSlotAcquired);
        }
    }

    private boolean acquireAeeemSlot(String datasetFormat) {
        if (!isAeeem(datasetFormat)) {
            return false;
        }
        if (!aeeemExtractionSlot.tryAcquire()) {
            throw new ExtractionBusyException(
                    "Another AEEEM repository is already being analyzed. Wait for it to finish before starting a new extraction.");
        }
        return true;
    }

    private void releaseAeeemSlot(boolean acquired) {
        if (acquired) {
            aeeemExtractionSlot.release();
        }
    }

    private static boolean isAeeem(String datasetFormat) {
        return datasetFormat != null && "aeeem".equalsIgnoreCase(datasetFormat.trim());
    }

    @GetMapping("/download/{datasetId}")
    public ResponseEntity<Resource> downloadDataset(@PathVariable("datasetId") String datasetId) {
        return downloadDataset(datasetId, "csv");
    }

    @GetMapping("/download/{datasetId}/{fileFormat}")
    public ResponseEntity<Resource> downloadDataset(@PathVariable("datasetId") String datasetId,
                                                    @PathVariable("fileFormat") String fileFormat) {
        try {
            UUID.fromString(datasetId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid target dataset ID.");
        }
        DatasetFileFormat format = DatasetFileFormat.fromExtension(fileFormat);
        Path filePath = metricsExtractionService.getDatasetPath(datasetId, format);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName() + "\"")
                .body(resource);
    }
}
