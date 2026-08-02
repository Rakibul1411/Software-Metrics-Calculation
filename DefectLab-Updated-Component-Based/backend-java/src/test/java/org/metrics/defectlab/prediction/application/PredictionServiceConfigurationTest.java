package org.metrics.defectlab.prediction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.prediction.infrastructure.MlServiceClient;
import org.metrics.defectlab.prediction.persistence.PredictionRunRepository;
import org.springframework.test.util.ReflectionTestUtils;

class PredictionServiceConfigurationTest {

    private PredictionService service;
    private MetricDataset source;

    @BeforeEach
    void setUp() throws IOException {
        service = new PredictionService(
                mock(DatasetService.class),
                mock(MlServiceClient.class),
                mock(PredictionRunRepository.class),
                new ObjectMapper());
        source = mock(MetricDataset.class);
        when(source.getDatasetFamily()).thenReturn(MetricDataset.Family.PROMISE);
    }

    @Test
    void defaultsToKnnThreeWithAlignmentEnabled() {
        Map<String, Object> config = modelConfig(Map.of());

        assertEquals("KNN", config.get("modelName"));
        assertEquals(3, config.get("k"));
        assertTrue((Boolean) config.get("coral"));
    }

    @Test
    void preservesDisabledDatasetAlignment() {
        Map<String, Object> config = modelConfig(Map.of("coral", false));

        assertFalse((Boolean) config.get("coral"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void acceptsUserSelectedKValues(int k) {
        assertEquals(k, modelConfig(Map.of("k", k)).get("k"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsKOutsideSupportedRange(int k) {
        assertThrows(IllegalArgumentException.class,
                () -> modelConfig(Map.of("k", k)));
    }

    @Test
    void rejectsUnsupportedModel() {
        assertThrows(IllegalArgumentException.class,
                () -> modelConfig(Map.of("modelName", "S" + "VM")));
    }

    @Test
    void allowsDifferentRowsWithTheSameProjectAndVersion() {
        MetricDataset target = mock(MetricDataset.class);
        when(source.getId()).thenReturn(52L);
        when(target.getId()).thenReturn(57L);
        when(target.getDatasetFamily()).thenReturn(MetricDataset.Family.PROMISE);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service, "validateDifferentAndFamily", source, target));
    }

    @Test
    void rejectsUsingTheExactSameDatasetRowAsSourceAndTarget() {
        MetricDataset target = mock(MetricDataset.class);
        when(source.getId()).thenReturn(52L);
        when(target.getId()).thenReturn(52L);
        when(target.getDatasetFamily()).thenReturn(MetricDataset.Family.PROMISE);

        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service, "validateDifferentAndFamily", source, target));
    }

    @Test
    void allowsLabeledPredefinedDatasetToBeItsOwnEvaluationTarget() {
        when(source.getId()).thenReturn(52L);
        when(source.getDatasetType()).thenReturn(MetricDataset.Type.PREDEFINED);
        when(source.hasActualLabel()).thenReturn(true);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service, "validateTargets", source, null, source));
    }

    @Test
    @SuppressWarnings("unchecked")
    void translatesLegacySummaryNamesToBuggyAndClean() {
        Map<String, Object> summary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service, "canonicalSummary", Map.of(
                        "totalRecords", 10,
                        "predictedDefective", 4,
                        "predictedNonDefective", 6));

        assertEquals(4, summary.get("predictedBuggy"));
        assertEquals(6, summary.get("predictedClean"));
        assertFalse(summary.containsKey("predictedDefective"));
        assertFalse(summary.containsKey("predictedNonDefective"));
    }

    @Test
    void rendersBinaryClassesAsBuggyAndClean() {
        assertEquals("Buggy", ReflectionTestUtils.invokeMethod(
                service, "classLabel", 1));
        assertEquals("Clean", ReflectionTestUtils.invokeMethod(
                service, "classLabel", 0));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> modelConfig(Map<String, Object> body) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service, "modelConfig", body, source);
    }
}
