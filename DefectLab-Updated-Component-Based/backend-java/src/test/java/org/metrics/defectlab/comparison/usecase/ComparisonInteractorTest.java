package org.metrics.defectlab.comparison.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.metrics.defectlab.comparison.usecase.port.MetricComparisonRepository;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.usecase.GetDatasetUseCase;
import org.metrics.defectlab.dataset.usecase.ListDatasetsUseCase;
import org.metrics.defectlab.dataset.usecase.LoadDatasetTableUseCase;
import org.metrics.defectlab.comparison.usecase.port.ArtifactStorage;
import org.metrics.defectlab.comparison.usecase.port.ComparisonReportRenderer;

class ComparisonInteractorTest {

    @Test
    void returnsStoredComparisonWithoutLoadingOrCalculatingDatasetFiles()
            throws Exception {
        Long userId = 7L;
        GetDatasetUseCase getDatasetUseCase = mock(GetDatasetUseCase.class);
        LoadDatasetTableUseCase loadDatasetTableUseCase = mock(LoadDatasetTableUseCase.class);
        ListDatasetsUseCase listDatasetsUseCase = mock(ListDatasetsUseCase.class);
        MetricComparisonRepository repository = mock(MetricComparisonRepository.class);
        MetricDataset manual = dataset(
                11L, userId, MetricDataset.Type.MANUAL, "Ant", "1.7");
        MetricDataset predefined = dataset(
                12L, null, MetricDataset.Type.PREDEFINED, "Ant", "1.7");
        MetricComparison saved = comparison(41L, userId, 11L, 12L);

        when(getDatasetUseCase.require(userId, 11L)).thenReturn(manual);
        when(getDatasetUseCase.require(userId, 12L)).thenReturn(predefined);
        when(repository.findByUserIdAndDatasetPair(userId, 11L, 12L))
                .thenReturn(Optional.of(saved));

        ComparisonInteractor interactor = new ComparisonInteractor(
                getDatasetUseCase, loadDatasetTableUseCase, listDatasetsUseCase,
                repository, mock(ComparisonReportRenderer.class), new ObjectMapper(),
                artifactStorage());
        Map<String, Object> result = interactor.execute(userId, Map.of(
                "manualDatasetId", 11L,
                "predefinedDatasetId", 12L));

        assertEquals(41L, result.get("id"));
        assertEquals(11L, result.get("manualDatasetId"));
        assertEquals(12L, result.get("predefinedDatasetId"));
        assertEquals(true, result.get("cacheHit"));
        verify(loadDatasetTableUseCase, never()).load(manual);
        verify(loadDatasetTableUseCase, never()).load(predefined);
    }

    @Test
    void eligiblePairsAreUniqueAndPreferAnExistingComparisonAcrossDuplicates()
            throws Exception {
        Long userId = 7L;
        GetDatasetUseCase getDatasetUseCase = mock(GetDatasetUseCase.class);
        LoadDatasetTableUseCase loadDatasetTableUseCase = mock(LoadDatasetTableUseCase.class);
        ListDatasetsUseCase listDatasetsUseCase = mock(ListDatasetsUseCase.class);
        MetricComparisonRepository repository = mock(MetricComparisonRepository.class);
        MetricDataset manual = dataset(
                11L, userId, MetricDataset.Type.MANUAL, "Ant", "1.7");
        MetricDataset systemPredefined = dataset(
                12L, null, MetricDataset.Type.PREDEFINED, " ant ", "1.7");
        MetricDataset duplicatePredefined = dataset(
                13L, userId, MetricDataset.Type.PREDEFINED, "ANT", "1.7");
        MetricComparison saved = comparison(41L, userId, 11L, 12L);

        when(listDatasetsUseCase.list(userId)).thenReturn(
                List.of(manual, duplicatePredefined, systemPredefined));
        when(repository.findByUserId(userId))
                .thenReturn(List.of(saved));

        ComparisonInteractor interactor = new ComparisonInteractor(
                getDatasetUseCase, loadDatasetTableUseCase, listDatasetsUseCase,
                repository, mock(ComparisonReportRenderer.class), new ObjectMapper(),
                artifactStorage());
        List<Map<String, Object>> pairs = interactor.eligiblePairs(userId);

        assertEquals(1, pairs.size());
        assertEquals(11L, pairs.get(0).get("manualDatasetId"));
        assertEquals(12L, pairs.get(0).get("predefinedDatasetId"));
        assertEquals(41L, pairs.get(0).get("comparisonId"));
        assertTrue((Boolean) pairs.get(0).get("cached"));
    }

    private static ArtifactStorage artifactStorage() throws Exception {
        ArtifactStorage artifactStorage = mock(ArtifactStorage.class);
        when(artifactStorage.rootFor(anyString()))
                .thenReturn(Path.of("storage", "comparison-reports"));
        return artifactStorage;
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
                Path.of("storage", "comparison-reports", "missing.pdf").toString());
        when(comparison.getCreatedAt()).thenReturn(
                Instant.parse("2026-07-31T00:00:00Z"));
        return comparison;
    }
}
