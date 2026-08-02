package org.metrics.defectlab.dataset.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metrics.defectlab.comparison.persistence.MetricComparisonRepository;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.persistence.MetricDatasetRepository;
import org.metrics.defectlab.prediction.persistence.PredictionRunRepository;
import org.springframework.test.util.ReflectionTestUtils;

class DatasetServiceLabelTest {

    private DatasetService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new DatasetService(
                mock(MetricDatasetRepository.class),
                mock(PredictionRunRepository.class),
                mock(MetricComparisonRepository.class));
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
                service, "hasCompleteUsableLabels", table, "bug"));
    }
}
