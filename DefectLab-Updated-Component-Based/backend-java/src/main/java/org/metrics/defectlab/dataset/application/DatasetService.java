package org.metrics.defectlab.dataset.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.metrics.defectlab.dataset.domain.DatasetQuality;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.FeatureProfile;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.infrastructure.DatasetFileParser;
import org.metrics.defectlab.dataset.persistence.MetricDatasetRepository;
import org.metrics.defectlab.comparison.persistence.MetricComparisonRepository;
import org.metrics.defectlab.prediction.persistence.PredictionRunRepository;
import org.metrics.defectlab.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DatasetService {

    private static final long MAX_DATASET_BYTES = 50L * 1024L * 1024L;

    private final MetricDatasetRepository datasetRepository;
    private final PredictionRunRepository predictionRunRepository;
    private final MetricComparisonRepository metricComparisonRepository;
    private final Path storageRoot = Paths.get("storage/metrics");

    public DatasetService(MetricDatasetRepository datasetRepository,
                          PredictionRunRepository predictionRunRepository,
                          MetricComparisonRepository metricComparisonRepository)
            throws IOException {
        this.datasetRepository = datasetRepository;
        this.predictionRunRepository = predictionRunRepository;
        this.metricComparisonRepository = metricComparisonRepository;
        Files.createDirectories(storageRoot);
    }

    @Transactional
    public MetricDataset createFromUpload(
            Long userId,
            String requestedProjectName,
            String projectVersion,
            MetricDataset.Type datasetType,
            MultipartFile file) throws IOException {
        String originalName = validateUpload(file);
        String projectName = cleanProjectName(requestedProjectName, stripExtension(originalName));
        Path userDirectory = userStorage(userId);
        String storedName = UUID.randomUUID() + "_" + sanitize(projectName)
                + suffixOf(originalName);
        Path stored = userDirectory.resolve(storedName);
        Files.copy(file.getInputStream(), stored, StandardCopyOption.REPLACE_EXISTING);
        try {
            return register(userId, projectName, projectVersion, stored, datasetType, null);
        } catch (RuntimeException | IOException exception) {
            Files.deleteIfExists(stored);
            throw exception;
        }
    }

    @Transactional
    public MetricDataset createFromExtraction(
            Long userId,
            String projectName,
            String projectVersion,
            MetricDataset.Family expectedFamily,
            Path generatedCsv) throws IOException {
        String cleanedProject = cleanProjectName(projectName, "extracted-project");
        Path stored = userStorage(userId).resolve(
                UUID.randomUUID() + "_" + sanitize(cleanedProject) + ".csv");
        Files.copy(generatedCsv, stored, StandardCopyOption.REPLACE_EXISTING);
        try {
            return register(userId, cleanedProject, projectVersion, stored,
                    MetricDataset.Type.MANUAL, expectedFamily);
        } catch (RuntimeException | IOException exception) {
            Files.deleteIfExists(stored);
            throw exception;
        }
    }

    /**
     * Registers bundled/public predefined data without creating another table.
     * The nullable user_id makes these rows visible to every signed-in user.
     */
    @Transactional
    public MetricDataset createSystemPredefined(
            String projectName, String projectVersion, Path source) throws IOException {
        Path systemDirectory = storageRoot.resolve("predefined");
        Files.createDirectories(systemDirectory);
        Path stored = systemDirectory.resolve(
                sanitize(projectName + "-" + projectVersion) + suffixOf(source.getFileName().toString()));
        Files.copy(source, stored, StandardCopyOption.REPLACE_EXISTING);
        return register(null, projectName, projectVersion, stored,
                MetricDataset.Type.PREDEFINED, null);
    }

    private MetricDataset register(
            Long userId,
            String projectName,
            String projectVersion,
            Path stored,
            MetricDataset.Type datasetType,
            MetricDataset.Family expectedFamily) throws IOException {
        DatasetTable table = DatasetFileParser.parse(stored);
        FeatureProfile profile = FeatureProfile.detect(table.getHeaders()).orElseThrow(
                () -> new IllegalArgumentException(
                        "The columns match neither PROMISE (20 predictors) nor "
                        + "AEEEM (56 predictors). Check the header names."));
        if (expectedFamily != null && profile.getFamily() != expectedFamily) {
            throw new IllegalArgumentException(
                    "The extracted columns look like " + profile.getFamily()
                    + " but " + expectedFamily + " was selected.");
        }

        DatasetQuality quality = DatasetQuality.inspect(table, profile);
        if (!quality.isUsable()) {
            throw new IllegalArgumentException(
                    "The dataset failed validation: "
                    + String.join("; ", quality.getBlockingIssues()));
        }

        boolean labeled = profile.findLabelColumn(table.getHeaders())
                .map(column -> hasUsableLabels(table, column))
                .orElse(false);
        return datasetRepository.save(new MetricDataset(
                userId,
                profile.getFamily(),
                cleanProjectName(projectName, "dataset"),
                cleanVersion(projectVersion),
                datasetType,
                labeled,
                table.getRowCount(),
                profile.getFeatures().size(),
                stored.toAbsolutePath().normalize().toString()));
    }

    private boolean hasUsableLabels(DatasetTable table, String labelColumn) {
        for (String raw : table.column(labelColumn)) {
            if (raw != null && !raw.trim().isEmpty() && !"?".equals(raw.trim())) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<MetricDataset> list(Long userId) {
        return datasetRepository.findVisibleTo(userId);
    }

    @Transactional(readOnly = true)
    public MetricDataset require(Long userId, Long datasetId) {
        return datasetRepository.findVisibleById(datasetId, userId)
                .orElseThrow(() -> new NotFoundException("That dataset does not exist."));
    }

    public DatasetTable load(MetricDataset dataset) throws IOException {
        return DatasetFileParser.parse(Paths.get(dataset.getMetricsFilePath()));
    }

    public FeatureProfile profileFor(MetricDataset dataset) {
        return dataset.getDatasetFamily() == MetricDataset.Family.PROMISE
                ? FeatureProfile.promise() : FeatureProfile.aeeem();
    }

    @Transactional
    public void delete(Long userId, Long datasetId) throws IOException {
        MetricDataset dataset = require(userId, datasetId);
        if (dataset.getUserId() == null) {
            throw new IllegalArgumentException("Bundled predefined datasets cannot be deleted.");
        }
        if (predictionRunRepository.existsBySourceDatasetIdOrTargetDatasetId(
                datasetId, datasetId)
                || metricComparisonRepository.existsByManualDatasetIdOrPredefinedDatasetId(
                        datasetId, datasetId)) {
            throw new IllegalArgumentException(
                    "This dataset is used by a saved prediction report and cannot be deleted.");
        }
        datasetRepository.delete(dataset);
        Files.deleteIfExists(Paths.get(dataset.getMetricsFilePath()));
    }

    @Transactional(readOnly = true)
    public long countFor(Long userId) {
        return datasetRepository.countByUserId(userId);
    }

    private Path userStorage(Long userId) throws IOException {
        Path directory = storageRoot.resolve(userId == null ? "predefined" : String.valueOf(userId));
        Files.createDirectories(directory);
        return directory;
    }

    private static String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty CSV or ARFF dataset.");
        }
        if (file.getSize() > MAX_DATASET_BYTES) {
            throw new IllegalArgumentException("A dataset upload may not exceed 50 MB.");
        }
        String originalName = file.getOriginalFilename() == null
                ? "dataset.csv" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".arff")) {
            throw new IllegalArgumentException("A dataset must be a .csv or .arff file.");
        }
        return originalName;
    }

    private static String cleanProjectName(String requested, String fallback) {
        String value = requested == null || requested.trim().isEmpty()
                ? fallback : requested.trim();
        if (value.length() > 150) {
            throw new IllegalArgumentException("Project name may not exceed 150 characters.");
        }
        return value;
    }

    private static String cleanVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "unspecified";
        }
        String value = version.trim();
        if (value.length() > 50) {
            throw new IllegalArgumentException("Project version may not exceed 50 characters.");
        }
        return value;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

    private static String suffixOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".arff") ? ".arff" : ".csv";
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
