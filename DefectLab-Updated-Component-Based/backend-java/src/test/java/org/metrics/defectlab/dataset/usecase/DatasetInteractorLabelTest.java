package org.metrics.defectlab.dataset.usecase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metrics.defectlab.comparison.usecase.port.MetricComparisonRepository;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.usecase.port.DatasetFileReader;
import org.metrics.defectlab.dataset.usecase.port.MetricDatasetRepository;
import org.metrics.defectlab.prediction.usecase.port.PredictionRunRepository;
import org.metrics.defectlab.shared.storage.StorageRoot;
import org.springframework.test.util.ReflectionTestUtils;

class DatasetInteractorLabelTest {

    private DatasetInteractor interactor;

    @BeforeEach
    void setUp() throws IOException {
        interactor = new DatasetInteractor(
                mock(MetricDatasetRepository.class),
                mock(PredictionRunRepository.class),
                mock(MetricComparisonRepository.class),
                mock(DatasetFileReader.class),
                new StorageRoot("storage"));
    }

    @Test
    void marksDatasetLabeledOnlyWhenEveryRowHasAUsableLabel() {
        DatasetTable table = labelTable("0", "2", "clean", "buggy");

        assertTrue(hasCompleteLabels(table));
    }

    @Test
    void rejectsMissingOrInvalidLabels() {
        assertFalse(hasCompleteLabels(labelTable("0", "?", "1")));
        assertFalse(hasCompleteLabels(labelTable("0", "unknown", "1")));
        assertFalse(hasCompleteLabels(labelTable("0", "-1", "1")));
    }

    private DatasetTable labelTable(String... labels) {
        return new DatasetTable(List.of("bug"),
                java.util.Arrays.stream(labels).map(List::of).toList());
    }

    private boolean hasCompleteLabels(DatasetTable table) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                interactor, "hasCompleteUsableLabels", table, "bug"));
    }
}
