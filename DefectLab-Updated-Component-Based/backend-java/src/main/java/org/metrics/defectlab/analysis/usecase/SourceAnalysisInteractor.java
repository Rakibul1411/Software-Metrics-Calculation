package org.metrics.defectlab.analysis.usecase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.aeeem.history.AeeemBenchmarkProfile;
import org.metrics.defectlab.analysis.usecase.port.ExtractionSlotCoordinator;
import org.metrics.defectlab.analysis.usecase.port.GitHubRepositoryClient;
import org.metrics.defectlab.analysis.usecase.port.MetricsExtractor;
import org.metrics.defectlab.analysis.usecase.port.SourceArchiveExtractor;
import org.metrics.defectlab.analysis.usecase.port.SourceArchiveStorage;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.usecase.RegisterExtractedDatasetUseCase;
import org.metrics.defectlab.shared.model.DatasetFileFormat;
import org.springframework.stereotype.Service;

/**
 * Coordinates source acquisition, metric extraction, and metric-dataset
 * persistence. The analysis component never writes database rows directly.
 */
@Service
public class SourceAnalysisInteractor implements AnalyzeSourceUseCase {

    private final RegisterExtractedDatasetUseCase registerExtractedDatasetUseCase;
    private final MetricsExtractor metricsExtractionService;
    private final SourceArchiveStorage fileStorageService;
    private final SourceArchiveExtractor zipExtractionService;
    private final GitHubRepositoryClient gitHubCloneService;
    private final ExtractionSlotCoordinator extractionCoordinator;

    public SourceAnalysisInteractor(
            RegisterExtractedDatasetUseCase registerExtractedDatasetUseCase,
            MetricsExtractor metricsExtractionService,
            SourceArchiveStorage fileStorageService,
            SourceArchiveExtractor zipExtractionService,
            GitHubRepositoryClient gitHubCloneService,
            ExtractionSlotCoordinator extractionCoordinator) {
        this.registerExtractedDatasetUseCase = registerExtractedDatasetUseCase;
        this.metricsExtractionService = metricsExtractionService;
        this.fileStorageService = fileStorageService;
        this.zipExtractionService = zipExtractionService;
        this.gitHubCloneService = gitHubCloneService;
        this.extractionCoordinator = extractionCoordinator;
    }

    @Override
    public MetricDataset analyze(
            Long userId,
            UploadedArchive projectArchive,
            String githubUrl,
            String projectName,
            String projectVersion,
            String familyValue,
            String aeeemProfile) throws IOException {
        boolean hasArchive = projectArchive != null && projectArchive.hasContent();
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

        boolean slotAcquired = extractionCoordinator.acquire(userId, family.name());
        try {
            return hasArchive
                    ? extractArchive(userId, projectName, version, projectArchive, family)
                    : extractGitHub(userId, projectName, version, githubUrl,
                            family, aeeemProfile);
        } finally {
            extractionCoordinator.release(userId, family.name(), slotAcquired);
        }
    }

    private MetricDataset extractArchive(
            Long userId,
            String projectName,
            String version,
            UploadedArchive archive,
            MetricDataset.Family family) throws IOException {
        Path uploaded = null;
        Path sourceDirectory = null;
        try {
            uploaded = fileStorageService.storeUploadedFile(archive);
            sourceDirectory = zipExtractionService.extractArchiveFile(uploaded);
            MetricsExtractor.ExtractionResult result =
                    metricsExtractionService.extractMetrics(
                            sourceDirectory.toString(), family.name(), null,
                            AeeemAnalysisOptions.current());
            String derivedName = cleanOrFallback(
                    projectName, stripArchiveSuffix(archive.originalFilename()));
            return register(userId, derivedName, version, family, result);
        } finally {
            fileStorageService.delete(sourceDirectory);
            fileStorageService.delete(uploaded);
        }
    }

    private MetricDataset extractGitHub(
            Long userId,
            String projectName,
            String version,
            String githubUrl,
            MetricDataset.Family family,
            String aeeemProfile) throws IOException {
        GitHubRepositoryClient.GitHubTarget target = gitHubCloneService.parseTarget(githubUrl);
        AeeemAnalysisOptions options = AeeemAnalysisOptions.fromRequest(
                family == MetricDataset.Family.AEEEM ? aeeemProfile : "current",
                target.getBranch(), target.getModulePath(),
                null, null, null, null);
        if (family == MetricDataset.Family.AEEEM) {
            options.getProfile().requireRecommendedRepository(target.getRepositoryUrl());
        }
        boolean multiRepository = family == MetricDataset.Family.AEEEM
                && options.getProfile().isMultiRepositoryBenchmark();

        List<Path> repositories = new ArrayList<>();
        try {
            String analysisRoots;
            if (multiRepository) {
                // Some AEEEM benchmarks (Mylyn) were historically split across
                // several component repositories rather than one. Clone every
                // component; MetricsExtractionService analyzes each
                // independently and merges the resulting classes.
                for (AeeemBenchmarkProfile.HistoricalRepository component
                        : options.getProfile().getHistoricalRepositories()) {
                    GitHubRepositoryClient.GitHubTarget componentTarget =
                            gitHubCloneService.parseTarget(component.getRepositoryUrl());
                    repositories.add(gitHubCloneService.cloneRepository(componentTarget, true));
                }
                analysisRoots = repositories.stream()
                        .map(Path::toString)
                        .collect(Collectors.joining(","));
            } else {
                Path repository = gitHubCloneService.cloneRepository(
                        target, family == MetricDataset.Family.AEEEM);
                repositories.add(repository);
                if (family == MetricDataset.Family.PROMISE) {
                    gitHubCloneService.checkoutHead(repository);
                }
                Path analysisRoot = family == MetricDataset.Family.PROMISE
                        && !target.getModulePath().isBlank()
                        ? repository.resolve(target.getModulePath()).normalize()
                        : repository;
                analysisRoots = analysisRoot.toString();
            }
            MetricsExtractor.ExtractionResult result =
                    metricsExtractionService.extractMetrics(
                            analysisRoots, family.name(), null, options);
            String fallbackName = family == MetricDataset.Family.AEEEM
                    && options.isBenchmarkProfile()
                    ? options.getProfile().getId().toUpperCase(Locale.ROOT)
                    : repositoryName(target);
            return register(userId, cleanOrFallback(projectName, fallbackName),
                    version, family, result);
        } finally {
            for (Path repository : repositories) {
                fileStorageService.delete(repository);
            }
        }
    }

    private MetricDataset register(
            Long userId,
            String projectName,
            String version,
            MetricDataset.Family family,
            MetricsExtractor.ExtractionResult result) throws IOException {
        Path generated = metricsExtractionService.getDatasetPath(
                result.getTargetDatasetId(), DatasetFileFormat.CSV);
        try {
            return registerExtractedDatasetUseCase.registerExtracted(
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

    private static String repositoryName(GitHubRepositoryClient.GitHubTarget target) {
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
