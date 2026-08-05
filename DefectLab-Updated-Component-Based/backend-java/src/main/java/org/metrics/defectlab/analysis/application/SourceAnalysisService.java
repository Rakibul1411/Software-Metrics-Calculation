package org.metrics.defectlab.analysis.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.infrastructure.FileStorageService;
import org.metrics.defectlab.analysis.infrastructure.GitHubCloneService;
import org.metrics.defectlab.analysis.infrastructure.ZipExtractionService;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.shared.model.DatasetFileFormat;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Coordinates source acquisition, metric extraction, and metric-dataset
 * persistence. The analysis component never writes database rows directly.
 */
@Service
public class SourceAnalysisService {

    private final DatasetService datasetService;
    private final MetricsExtractionService metricsExtractionService;
    private final FileStorageService fileStorageService;
    private final ZipExtractionService zipExtractionService;
    private final GitHubCloneService gitHubCloneService;
    private final AeeemExtractionCoordinator aeeemExtractionCoordinator;

    public SourceAnalysisService(
            DatasetService datasetService,
            MetricsExtractionService metricsExtractionService,
            FileStorageService fileStorageService,
            ZipExtractionService zipExtractionService,
            GitHubCloneService gitHubCloneService,
            AeeemExtractionCoordinator aeeemExtractionCoordinator) {
        this.datasetService = datasetService;
        this.metricsExtractionService = metricsExtractionService;
        this.fileStorageService = fileStorageService;
        this.zipExtractionService = zipExtractionService;
        this.gitHubCloneService = gitHubCloneService;
        this.aeeemExtractionCoordinator = aeeemExtractionCoordinator;
    }

    public MetricDataset analyze(
            Long userId,
            MultipartFile projectArchive,
            String githubUrl,
            String projectName,
            String projectVersion,
            String familyValue,
            String aeeemProfile) throws IOException {
        boolean hasArchive = projectArchive != null && !projectArchive.isEmpty();
        boolean hasGitHubUrl = githubUrl != null && !githubUrl.trim().isEmpty();
        if (hasArchive == hasGitHubUrl) {
            throw new IllegalArgumentException(
                    "Provide exactly one source: Java archive or public GitHub URL.");
        }

        MetricDataset.Family family = parseFamily(familyValue);
        String version = requireVersion(projectVersion);
        if (hasArchive && family == MetricDataset.Family.AEEEM) {
            throw new IllegalArgumentException(
                    "AEEEM WCHU, LDHH and entropy features require Git history. "
                    + "Use a public GitHub repository URL.");
        }

        boolean aeeemSlotAcquired = aeeemExtractionCoordinator.acquire(family.name());
        try {
            return hasArchive
                    ? extractArchive(userId, projectName, version, projectArchive, family)
                    : extractGitHub(userId, projectName, version, githubUrl,
                            family, aeeemProfile);
        } finally {
            aeeemExtractionCoordinator.release(aeeemSlotAcquired);
        }
    }

    private MetricDataset extractArchive(
            Long userId,
            String projectName,
            String version,
            MultipartFile archive,
            MetricDataset.Family family) throws IOException {
        Path uploaded = null;
        Path sourceDirectory = null;
        try {
            uploaded = fileStorageService.storeUploadedFile(archive);
            sourceDirectory = zipExtractionService.extractArchiveFile(uploaded);
            MetricsExtractionService.ExtractionResult result =
                    metricsExtractionService.extractMetrics(
                            sourceDirectory.toString(), family.name(), null,
                            AeeemAnalysisOptions.current());
            String derivedName = cleanOrFallback(
                    projectName, stripArchiveSuffix(archive.getOriginalFilename()));
            return register(userId, derivedName, version, family, result);
        } finally {
            FileStorageService.deleteRecursively(sourceDirectory);
            FileStorageService.deleteRecursively(uploaded);
        }
    }

    private MetricDataset extractGitHub(
            Long userId,
            String projectName,
            String version,
            String githubUrl,
            MetricDataset.Family family,
            String aeeemProfile) throws IOException {
        GitHubCloneService.GitHubTarget target = gitHubCloneService.parseTarget(githubUrl);
        AeeemAnalysisOptions options = AeeemAnalysisOptions.fromRequest(
                family == MetricDataset.Family.AEEEM ? aeeemProfile : "current",
                target.getBranch(), target.getModulePath(),
                null, null, null, null);
        if (family == MetricDataset.Family.AEEEM) {
            options.getProfile().requireRecommendedRepository(target.getRepositoryUrl());
        }

        Path repository = null;
        try {
            repository = gitHubCloneService.cloneRepository(
                    target, family == MetricDataset.Family.AEEEM);
            if (family == MetricDataset.Family.PROMISE) {
                gitHubCloneService.checkoutHead(repository);
            }
            Path analysisRoot = family == MetricDataset.Family.PROMISE
                    && !target.getModulePath().isBlank()
                    ? repository.resolve(target.getModulePath()).normalize()
                    : repository;
            MetricsExtractionService.ExtractionResult result =
                    metricsExtractionService.extractMetrics(
                            analysisRoot.toString(), family.name(), null, options);
            String fallbackName = family == MetricDataset.Family.AEEEM
                    && options.isBenchmarkProfile()
                    ? options.getProfile().getId().toUpperCase(Locale.ROOT)
                    : repositoryName(target);
            return register(userId, cleanOrFallback(projectName, fallbackName),
                    version, family, result);
        } finally {
            FileStorageService.deleteRecursively(repository);
        }
    }

    private MetricDataset register(
            Long userId,
            String projectName,
            String version,
            MetricDataset.Family family,
            MetricsExtractionService.ExtractionResult result) throws IOException {
        Path generated = metricsExtractionService.getDatasetPath(
                result.getTargetDatasetId(), DatasetFileFormat.CSV);
        try {
            return datasetService.createFromExtraction(
                    userId, projectName, version, family, generated);
        } finally {
            Files.deleteIfExists(generated);
            Files.deleteIfExists(metricsExtractionService.getDatasetPath(
                    result.getTargetDatasetId(), DatasetFileFormat.ARFF));
        }
    }

    private static MetricDataset.Family parseFamily(String value) {
        try {
            return MetricDataset.Family.valueOf(
                    value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Dataset family must be PROMISE or AEEEM.");
        }
    }

    private static String requireVersion(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the project version.");
        }
        return value.trim();
    }

    private static String stripArchiveSuffix(String filename) {
        if (filename == null || filename.isBlank()) {
            return "extracted-project";
        }
        String base = Paths.get(filename).getFileName().toString();
        return base.replaceAll("(?i)\\.(zip|tar|tgz|tar\\.gz|gz)$", "");
    }

    private static String repositoryName(GitHubCloneService.GitHubTarget target) {
        String repositoryUrl = target.getRepositoryUrl();
        String repository = repositoryUrl.substring(repositoryUrl.lastIndexOf('/') + 1);
        if (target.getModulePath().isEmpty()) {
            return repository;
        }
        String modulePath = target.getModulePath();
        return repository + "-" + modulePath.substring(modulePath.lastIndexOf('/') + 1);
    }

    private static String cleanOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
