package org.metrics.defectlab.comparison.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.comparison.domain.MetricComparison;
import org.metrics.defectlab.comparison.persistence.MetricComparisonRepository;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.domain.MetricDataset;

class MetricComparisonServiceTest {

    @Test
    void returnsStoredComparisonWithoutLoadingOrCalculatingDatasetFiles()
            throws Exception {
        Long userId = 7L;
        DatasetService datasets = mock(DatasetService.class);
        MetricComparisonRepository repository = mock(MetricComparisonRepository.class);
        MetricDataset manual = dataset(
                11L, userId, MetricDataset.Type.MANUAL, "Ant", "1.7");
        MetricDataset predefined = dataset(
                12L, null, MetricDataset.Type.PREDEFINED, "Ant", "1.7");
        MetricComparison saved = comparison(41L, userId, 11L, 12L);

        when(datasets.require(userId, 11L)).thenReturn(manual);
        when(datasets.require(userId, 12L)).thenReturn(predefined);
        when(repository.findByUserIdAndManualDatasetIdAndPredefinedDatasetId(
                userId, 11L, 12L)).thenReturn(Optional.of(saved));

        MetricComparisonService service =
                new MetricComparisonService(datasets, repository, new ObjectMapper());
        Map<String, Object> result = service.execute(userId, Map.of(
                "manualDatasetId", 11L,
                "predefinedDatasetId", 12L));

        assertEquals(41L, result.get("id"));
        assertEquals(11L, result.get("manualDatasetId"));
        assertEquals(12L, result.get("predefinedDatasetId"));
        assertEquals(true, result.get("cacheHit"));
        verify(datasets, never()).load(manual);
        verify(datasets, never()).load(predefined);
    }

    @Test
    void eligiblePairsAreUniqueAndPreferAnExistingComparisonAcrossDuplicates()
            throws Exception {
        Long userId = 7L;
        DatasetService datasets = mock(DatasetService.class);
        MetricComparisonRepository repository = mock(MetricComparisonRepository.class);
        MetricDataset manual = dataset(
                11L, userId, MetricDataset.Type.MANUAL, "Ant", "1.7");
        MetricDataset systemPredefined = dataset(
                12L, null, MetricDataset.Type.PREDEFINED, " ant ", "1.7");
        MetricDataset duplicatePredefined = dataset(
                13L, userId, MetricDataset.Type.PREDEFINED, "ANT", "1.7");
        MetricComparison saved = comparison(41L, userId, 11L, 12L);

        when(datasets.list(userId)).thenReturn(
                List.of(manual, duplicatePredefined, systemPredefined));
        when(repository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(saved));

        MetricComparisonService service =
                new MetricComparisonService(datasets, repository, new ObjectMapper());
        List<Map<String, Object>> pairs = service.eligiblePairs(userId);

        assertEquals(1, pairs.size());
        assertEquals(11L, pairs.get(0).get("manualDatasetId"));
        assertEquals(12L, pairs.get(0).get("predefinedDatasetId"));
        assertEquals(41L, pairs.get(0).get("comparisonId"));
        assertTrue((Boolean) pairs.get(0).get("cached"));
    }

    private static MetricDataset dataset(
            Long id, Long userId, MetricDataset.Type type,
            String project, String version) {
        MetricDataset dataset = mock(MetricDataset.class);
        when(dataset.getId()).thenReturn(id);
        when(dataset.getUserId()).thenReturn(userId);
        when(dataset.getDatasetFamily()).thenReturn(MetricDataset.Family.PROMISE);
        when(dataset.getDatasetType()).thenReturn(type);
        when(dataset.getProjectName()).thenReturn(project);
        when(dataset.getProjectVersion()).thenReturn(version);
        when(dataset.getDisplayName()).thenReturn(project.trim() + " " + version);
        when(dataset.getCreatedAt()).thenReturn(Instant.parse("2026-07-31T00:00:00Z"));
        return dataset;
    }

    private static MetricComparison comparison(
            Long id, Long userId, Long manualId, Long predefinedId) {
        MetricComparison comparison = mock(MetricComparison.class);
        when(comparison.getId()).thenReturn(id);
        when(comparison.getUserId()).thenReturn(userId);
        when(comparison.getManualDatasetId()).thenReturn(manualId);
        when(comparison.getPredefinedDatasetId()).thenReturn(predefinedId);
        when(comparison.getComparisonConfig()).thenReturn(
                "{\"comparisonMode\":\"INSTANCE_WISE\","
                + "\"hasIdentifierColumn\":true,\"identifierColumnName\":\"name\","
                + "\"absoluteTolerance\":0.0001,\"relativeTolerance\":0.01}");
        when(comparison.getComparisonReportFilePath()).thenReturn(
                Path.of("storage", "comparisons", "missing.pdf").toString());
        when(comparison.getCreatedAt()).thenReturn(
                Instant.parse("2026-07-31T00:00:00Z"));
        return comparison;
    }
}
