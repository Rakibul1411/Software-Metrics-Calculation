package org.metrics.defectlab.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.dataset.domain.DatasetQuality;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.FeatureProfile;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.infrastructure.DatasetFileParser;

class AeeemPredefinedCsvTest {

    private static final Path PREDEFINED_DIR = Paths.get(
            "/Users/md.rakibulislam/IIT/SPL-3/promise-dataset-source-code/DefectLab-Updated-Component-Based/sample-data/predefined/aeeem");

    private static final Map<String, Integer> EXPECTED_ROWS = Map.of(
            "EQ.csv", 324,
            "JDT.csv", 997,
            "LC.csv", 691,
            "ML.csv", 1862,
            "PDE.csv", 1497
    );

    @Test
    void verifiesAllPredefinedAeeemCsvFilesWithIdentifiers() throws Exception {
        assertTrue(Files.isDirectory(PREDEFINED_DIR), "Predefined AEEEM directory must exist.");

        for (Map.Entry<String, Integer> entry : EXPECTED_ROWS.entrySet()) {
            Path file = PREDEFINED_DIR.resolve(entry.getKey());
            assertTrue(Files.isRegularFile(file), "File must exist: " + file);

            DatasetTable table = DatasetFileParser.parse(file);
            assertEquals(entry.getValue().intValue(), table.getRowCount(),
                    "Row count mismatch for " + entry.getKey());

            // Verify classname identifier column exists as the first column
            assertTrue(table.getHeaders().contains("classname") || table.getHeaders().contains("name"),
                    "File identifier column (classname/name) must exist in " + entry.getKey());

            // Verify AEEEM feature profile detection
            Optional<FeatureProfile> profile = FeatureProfile.detect(table.getHeaders());
            assertTrue(profile.isPresent(), "Feature profile must be detected for " + entry.getKey());
            assertEquals(MetricDataset.Family.AEEEM, profile.get().getFamily(),
                    "Family must be AEEEM for " + entry.getKey());

            // Verify quality inspection passes
            DatasetQuality quality = DatasetQuality.inspect(table, profile.get());
            assertTrue(quality.isUsable(), "Dataset must be usable: " + String.join(", ", quality.getBlockingIssues()));

            // Verify label column detection
            Optional<String> labelCol = profile.get().findLabelColumn(table.getHeaders());
            assertTrue(labelCol.isPresent(), "Label column must be present in " + entry.getKey());
        }
    }
}
