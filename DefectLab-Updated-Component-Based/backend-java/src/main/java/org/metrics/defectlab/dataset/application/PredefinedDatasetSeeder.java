package org.metrics.defectlab.dataset.application;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.persistence.MetricDatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Imports the bundled benchmark manifest into metric_datasets on first start.
 * The imported rows have user_id = NULL and are therefore visible to every user.
 */
@Component
public class PredefinedDatasetSeeder implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PredefinedDatasetSeeder.class);

    private final DatasetService datasetService;
    private final MetricDatasetRepository datasetRepository;
    private final Path dataDirectory;

    public PredefinedDatasetSeeder(
            DatasetService datasetService,
            MetricDatasetRepository datasetRepository,
            @Value("${app.predefined-data-dir:../sample-data/predefined}")
            String dataDirectory) {
        this.datasetService = datasetService;
        this.datasetRepository = datasetRepository;
        this.dataDirectory = Paths.get(dataDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) {
        Path manifest = dataDirectory.resolve("manifest.csv");
        if (!Files.isRegularFile(manifest)) {
            LOGGER.info("No predefined dataset manifest found at {}", manifest);
            return;
        }

        try (Reader reader = Files.newBufferedReader(manifest)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);
            for (CSVRecord record : records) {
                seedRecord(record);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not read predefined dataset manifest {}", manifest, exception);
        }
    }

    private void seedRecord(CSVRecord record) {
        String projectName = record.get("project_name");
        String projectVersion = record.get("project_version");
        Path source = dataDirectory.resolve(record.get("file")).normalize();
        if (!source.startsWith(dataDirectory) || !Files.isRegularFile(source)) {
            LOGGER.warn("Skipping missing or unsafe predefined dataset path: {}", source);
            return;
        }
        Optional<MetricDataset> existing = datasetRepository
                .findByUserIdIsNullAndProjectNameIgnoreCaseAndProjectVersionAndDatasetType(
                        projectName, projectVersion, MetricDataset.Type.PREDEFINED);
        if (existing.isPresent()) {
            restoreMissingFile(existing.get(), source);
            return;
        }
        try {
            datasetService.createSystemPredefined(projectName, projectVersion, source);
            LOGGER.info("Registered predefined dataset {} {}", projectName, projectVersion);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not register predefined dataset {} {}",
                    projectName, projectVersion, exception);
        }
    }

    private void restoreMissingFile(MetricDataset dataset, Path source) {
        Path stored = Paths.get(dataset.getMetricsFilePath()).toAbsolutePath().normalize();
        if (Files.isRegularFile(stored)) {
            return;
        }
        try {
            Files.createDirectories(stored.getParent());
            Files.copy(source, stored, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Restored predefined dataset file for {}",
                    dataset.getDisplayName());
        } catch (IOException exception) {
            LOGGER.warn("Could not restore predefined dataset file for {}",
                    dataset.getDisplayName(), exception);
        }
    }
}
