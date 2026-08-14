package org.metrics.defectlab.comparison.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.metrics.defectlab.comparison.domain.MetricComparison;
import org.metrics.defectlab.comparison.persistence.MetricComparisonRepository;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.application.DatasetSummaryMapper;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.FeatureProfile;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.shared.exception.NotFoundException;
import org.metrics.defectlab.shared.report.PdfReportWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricComparisonService {

    private final DatasetService datasetService;
    private final MetricComparisonRepository comparisonRepository;
    private final ObjectMapper objectMapper;
    private final Path storageRoot = Paths.get("storage/comparisons");

    public MetricComparisonService(
            DatasetService datasetService,
            MetricComparisonRepository comparisonRepository,
            ObjectMapper objectMapper) throws IOException {
        this.datasetService = datasetService;
        this.comparisonRepository = comparisonRepository;
        this.objectMapper = objectMapper;
        Files.createDirectories(storageRoot);
        migrateStaleComparisons();
    }

    public Map<String, Object> execute(Long userId, Map<String, Object> body)
            throws IOException {
        MetricDataset manual = datasetService.require(
                userId, requiredLong(body, "manualDatasetId"));
        MetricDataset predefined = datasetService.require(
                userId, requiredLong(body, "predefinedDatasetId"));
        validateDatasets(manual, predefined);

        Optional<MetricComparison> existing = comparisonRepository
                .findByUserIdAndManualDatasetIdAndPredefinedDatasetId(
                        userId, manual.getId(), predefined.getId());
        if (existing.isPresent()) {
            return detail(userId, existing.get(), true);
        }

        DatasetTable manualTable = datasetService.load(manual);
        DatasetTable predefinedTable = datasetService.load(predefined);
        Map<String, Object> config = config(body, manual, manualTable, predefinedTable);

        Map<String, Object> result = "AGGREGATE".equals(config.get("comparisonMode"))
                ? aggregate(manual, manualTable, predefinedTable)
                : instanceWise(manual, manualTable, predefinedTable, config);

        Path directory = storageRoot.resolve(String.valueOf(userId));
        Files.createDirectories(directory);
        String token = UUID.randomUUID().toString();
        Path pdf = directory.resolve(token + "-metric-comparison.pdf");
        Path json = metadataPath(pdf);
        try {
            writePdf(pdf, manual, predefined, config, result);
            Files.writeString(json, writeJson(result), StandardCharsets.UTF_8);
            MetricComparison saved = comparisonRepository.saveAndFlush(new MetricComparison(
                    userId, manual.getId(), predefined.getId(), writeJson(config), absolute(pdf)));
            return detail(userId, saved, false);
        } catch (DataIntegrityViolationException exception) {
            Files.deleteIfExists(pdf);
            Files.deleteIfExists(json);
            Optional<MetricComparison> cached = comparisonRepository
                    .findByUserIdAndManualDatasetIdAndPredefinedDatasetId(
                            userId, manual.getId(), predefined.getId());
            if (cached.isPresent()) {
                return detail(userId, cached.get(), true);
            }
            throw new IllegalArgumentException(
                    "The saved comparison could not be loaded. Please try again.");
        } catch (RuntimeException | IOException exception) {
            Files.deleteIfExists(pdf);
            Files.deleteIfExists(json);
            throw exception;
        }
    }

    private void validateDatasets(MetricDataset manual, MetricDataset predefined) {
        if (manual.getId().equals(predefined.getId())) {
            throw new IllegalArgumentException(
                    "Manual and predefined datasets must differ.");
        }
        if (manual.getDatasetType() != MetricDataset.Type.MANUAL) {
            throw new IllegalArgumentException("manualDatasetId must reference a MANUAL dataset.");
        }
        if (predefined.getDatasetType() != MetricDataset.Type.PREDEFINED) {
            throw new IllegalArgumentException(
                    "predefinedDatasetId must reference a PREDEFINED dataset.");
        }
        if (manual.getDatasetFamily() != predefined.getDatasetFamily()) {
            throw new IllegalArgumentException(
                    "Metric comparison requires datasets from the same family.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config(
            Map<String, Object> body, MetricDataset manual,
            DatasetTable manualTable, DatasetTable predefinedTable) {
        Object raw = body.get("comparisonConfig");
        Map<String, Object> input = raw instanceof Map
                ? (Map<String, Object>) raw : body;
        boolean defaultIdentifier = manual.getDatasetFamily() == MetricDataset.Family.PROMISE
                && manualTable.indexOf("name") >= 0
                && predefinedTable.indexOf("name") >= 0;
        boolean hasIdentifier = booleanValue(
                input.getOrDefault("hasIdentifierColumn", defaultIdentifier));
        String identifierName = nullableText(input.get("identifierColumnName"));
        if (hasIdentifier && identifierName == null) {
            identifierName = "name";
        }
        if (hasIdentifier && (manualTable.indexOf(identifierName) < 0
                || predefinedTable.indexOf(identifierName) < 0)) {
            throw new IllegalArgumentException(
                    "identifierColumnName must exist in both datasets.");
        }
        String defaultMode = hasIdentifier ? "INSTANCE_WISE" : "AGGREGATE";
        String mode = String.valueOf(input.getOrDefault("comparisonMode", defaultMode))
                .trim().toUpperCase(Locale.ROOT);
        if (!Set.of("AGGREGATE", "INSTANCE_WISE").contains(mode)) {
            throw new IllegalArgumentException(
                    "comparisonMode must be AGGREGATE or INSTANCE_WISE.");
        }
        if ("INSTANCE_WISE".equals(mode) && !hasIdentifier) {
            throw new IllegalArgumentException(
                    "INSTANCE_WISE comparison requires an identifier column.");
        }
        if (manual.getDatasetFamily() == MetricDataset.Family.AEEEM && !hasIdentifier) {
            mode = "AGGREGATE";
        }
        double absolute = doubleValue(input.get("absoluteTolerance"), 0.0001);
        double relative = doubleValue(input.get("relativeTolerance"), 0.01);
        if (absolute < 0 || relative < 0) {
            throw new IllegalArgumentException("Comparison tolerances cannot be negative.");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("hasIdentifierColumn", hasIdentifier);
        config.put("identifierColumnName", hasIdentifier ? identifierName : null);
        config.put("comparisonMode", mode);
        config.put("absoluteTolerance", absolute);
        config.put("relativeTolerance", relative);
        return config;
    }

    private Map<String, Object> aggregate(
            MetricDataset dataset, DatasetTable manual, DatasetTable predefined) {
        List<String> metrics = commonNumericMetrics(dataset, manual, predefined);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("comparisonMode", "AGGREGATE");
        result.put("commonNumericMetricCount", metrics.size());
        result.put("metrics", metricStatsRows(metrics, manual, predefined));
        return result;
    }

    private List<Map<String, Object>> metricStatsRows(
            List<String> metrics, DatasetTable manual, DatasetTable predefined) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String metric : metrics) {
            Stats left = stats(numericValues(manual.column(metric)));
            Stats right = stats(numericValues(predefined.column(metric)));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metric", metric);
            row.put("manual", left.toMap());
            row.put("predefined", right.toMap());
            row.put("meanDifference", difference(left.mean, right.mean));
            row.put("sdDifference", difference(left.standardDeviation, right.standardDeviation));
            row.put("percentageDifference", percentageDifference(left.mean, right.mean));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> instanceWise(
            MetricDataset dataset, DatasetTable manual, DatasetTable predefined,
            Map<String, Object> config) {
        String identifier = String.valueOf(config.get("identifierColumnName"));
        int manualId = manual.indexOf(identifier);
        int predefinedId = predefined.indexOf(identifier);
        List<String> metrics = commonNumericMetrics(dataset, manual, predefined);
        Map<String, List<String>> left = rowsByIdentifier(manual, manualId);
        Map<String, List<String>> right = rowsByIdentifier(predefined, predefinedId);
        Set<String> identifiers = new LinkedHashSet<>(left.keySet());
        identifiers.addAll(right.keySet());
        double absolute = doubleValue(config.get("absoluteTolerance"), 0.0001);
        double relative = doubleValue(config.get("relativeTolerance"), 0.01);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of(
                "EXACT_MATCH", "CLOSE_MATCH", "MISMATCH", "NOT_COMPARABLE")) {
            counts.put(status, 0);
        }
        List<Map<String, Object>> comparisons = new ArrayList<>();
        List<String> manualOnly = new ArrayList<>();
        List<String> predefinedOnly = new ArrayList<>();
        for (String normalized : identifiers) {
            List<String> leftRow = left.get(normalized);
            List<String> rightRow = right.get(normalized);
            if (leftRow == null) {
                predefinedOnly.add(cell(rightRow, predefinedId));
                continue;
            }
            if (rightRow == null) {
                manualOnly.add(cell(leftRow, manualId));
                continue;
            }
            for (String metric : metrics) {
                Double leftValue = parseDouble(cell(leftRow, manual.indexOf(metric)));
                Double rightValue = parseDouble(cell(rightRow, predefined.indexOf(metric)));
                String status = status(leftValue, rightValue, absolute, relative);
                counts.put(status, counts.get(status) + 1);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("identifier", cell(leftRow, manualId));
                row.put("metric", metric);
                row.put("manualValue", leftValue);
                row.put("predefinedValue", rightValue);
                row.put("status", status);
                comparisons.add(row);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("comparisonMode", "INSTANCE_WISE");
        result.put("commonNumericMetricCount", metrics.size());
        result.put("statusCounts", counts);
        result.put("matchedIdentifiers", identifiers.size()
                - manualOnly.size() - predefinedOnly.size());
        result.put("manualOnly", manualOnly);
        result.put("predefinedOnly", predefinedOnly);
        result.put("comparisons", comparisons);
        result.put("metrics", metricStatsRows(metrics, manual, predefined));
        return result;
    }

    private List<String> commonNumericMetrics(
            MetricDataset dataset, DatasetTable left, DatasetTable right) {
        FeatureProfile profile = datasetService.profileFor(dataset);
        List<String> metrics = new ArrayList<>();
        for (String metric : profile.getFeatures()) {
            if (left.indexOf(metric) >= 0 && right.indexOf(metric) >= 0
                    && hasNumeric(left.column(metric)) && hasNumeric(right.column(metric))) {
                metrics.add(metric);
            }
        }
        return metrics;
    }

    private static final int MAX_LISTED_IDENTIFIERS = 30;

    private void writePdf(
            Path pdf, MetricDataset manual, MetricDataset predefined,
            Map<String, Object> config, Map<String, Object> result) throws IOException {
        List<String> intro = new ArrayList<>();
        intro.add("Manual dataset: " + manual.getDisplayName());
        intro.add("Predefined dataset: " + predefined.getDisplayName());
        intro.add("Family: " + manual.getDatasetFamily());
        intro.add("Comparison mode: " + result.get("comparisonMode"));
        intro.add("Absolute tolerance: " + config.get("absoluteTolerance")
                + "   Relative tolerance: " + config.get("relativeTolerance"));

        List<PdfReportWriter.Table> tables = new ArrayList<>();
        if (!"AGGREGATE".equals(result.get("comparisonMode"))) {
            intro.add("Matched identifiers: " + result.get("matchedIdentifiers"));
            intro.add("Status counts: " + result.get("statusCounts"));
            intro.add(identifierSummaryLine("Manual-only files", result.get("manualOnly")));
            intro.add(identifierSummaryLine("Predefined-only files", result.get("predefinedOnly")));
        }

        tables.add(new PdfReportWriter.Table(
                "Metric-wise mean & std (manual vs predefined)",
                List.of("Metric", "Mean Manual", "Mean Predefined",
                        "Std Manual", "Std Predefined", "% Diff"),
                metricStatsTableRows(result.get("metrics"))));

        if (!"AGGREGATE".equals(result.get("comparisonMode"))) {
            tables.add(new PdfReportWriter.Table(
                    "File-wise comparison",
                    List.of("File / Identifier", "Metric", "Manual Value",
                            "Predefined Value", "Status"),
                    instanceComparisonTableRows(result.get("comparisons"))));
        }

        PdfReportWriter.writeTables(pdf, "DefectLab Metric Comparison Report", intro, tables);
    }

    private String identifierSummaryLine(String label, Object rawList) {
        List<?> identifiers = rawList instanceof List ? (List<?>) rawList : List.of();
        if (identifiers.isEmpty()) {
            return label + ": none";
        }
        StringBuilder line = new StringBuilder(label + " (" + identifiers.size() + "): ");
        List<?> shown = identifiers.subList(0, Math.min(MAX_LISTED_IDENTIFIERS, identifiers.size()));
        line.append(shown.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        if (identifiers.size() > shown.size()) {
            line.append(", +").append(identifiers.size() - shown.size()).append(" more");
        }
        return line.toString();
    }

    private List<List<String>> metricStatsTableRows(Object rawRows) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> row : mapList(rawRows)) {
            Map<String, Object> manualStats = asMap(row.get("manual"));
            Map<String, Object> predefinedStats = asMap(row.get("predefined"));
            rows.add(List.of(
                    String.valueOf(row.get("metric")),
                    formatNumber(manualStats.get("mean")),
                    formatNumber(predefinedStats.get("mean")),
                    formatNumber(manualStats.get("standardDeviation")),
                    formatNumber(predefinedStats.get("standardDeviation")),
                    formatPercentage(row.get("percentageDifference"))));
        }
        return rows;
    }

    private List<List<String>> instanceComparisonTableRows(Object rawRows) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> row : mapList(rawRows)) {
            rows.add(List.of(
                    String.valueOf(row.get("identifier")),
                    String.valueOf(row.get("metric")),
                    formatNumber(row.get("manualValue")),
                    formatNumber(row.get("predefinedValue")),
                    String.valueOf(row.get("status"))));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static String formatNumber(Object value) {
        if (!(value instanceof Number)) {
            return "-";
        }
        double doubleValue = ((Number) value).doubleValue();
        return Double.isFinite(doubleValue) ? String.format(Locale.ROOT, "%.4f", doubleValue) : "-";
    }

    private static String formatPercentage(Object value) {
        if (!(value instanceof Number)) {
            return "-";
        }
        double doubleValue = ((Number) value).doubleValue();
        return Double.isFinite(doubleValue)
                ? String.format(Locale.ROOT, "%.2f%%", doubleValue) : "-";
    }

    @Transactional(readOnly = true)
    public List<MetricComparison> list(Long userId) {
        return comparisonRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> eligiblePairs(Long userId) {
        List<MetricDataset> visible = datasetService.list(userId);
        Map<String, List<MetricDataset>> manuals = new LinkedHashMap<>();
        Map<String, List<MetricDataset>> predefined = new LinkedHashMap<>();
        Map<Long, MetricDataset> byId = new HashMap<>();
        for (MetricDataset dataset : visible) {
            byId.put(dataset.getId(), dataset);
            Map<String, List<MetricDataset>> destination =
                    dataset.getDatasetType() == MetricDataset.Type.MANUAL
                            ? manuals : predefined;
            destination.computeIfAbsent(datasetIdentity(dataset),
                    ignored -> new ArrayList<>()).add(dataset);
        }

        List<MetricComparison> saved =
                comparisonRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<MetricDataset>> entry : manuals.entrySet()) {
            List<MetricDataset> predefinedMatches = predefined.get(entry.getKey());
            if (predefinedMatches == null || predefinedMatches.isEmpty()) continue;

            Set<Long> manualIds = datasetIds(entry.getValue());
            Set<Long> predefinedIds = datasetIds(predefinedMatches);
            MetricComparison cached = saved.stream()
                    .filter(item -> manualIds.contains(item.getManualDatasetId())
                            && predefinedIds.contains(item.getPredefinedDatasetId()))
                    .findFirst()
                    .orElse(null);

            MetricDataset manual = cached == null
                    ? preferredDataset(entry.getValue(), userId)
                    : byId.get(cached.getManualDatasetId());
            MetricDataset target = cached == null
                    ? preferredDataset(predefinedMatches, userId)
                    : byId.get(cached.getPredefinedDatasetId());
            if (manual == null || target == null) continue;

            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("key", entry.getKey());
            pair.put("displayName", manual.getDisplayName());
            pair.put("datasetFamily", manual.getDatasetFamily().name());
            pair.put("projectName", manual.getProjectName());
            pair.put("projectVersion", manual.getProjectVersion());
            pair.put("manualDatasetId", manual.getId());
            pair.put("predefinedDatasetId", target.getId());
            pair.put("comparisonId", cached == null ? null : cached.getId());
            pair.put("cached", cached != null);
            result.add(pair);
        }
        result.sort((left, right) -> String.valueOf(left.get("displayName"))
                .compareToIgnoreCase(String.valueOf(right.get("displayName"))));
        return result;
    }

    @Transactional(readOnly = true)
    public MetricComparison require(Long userId, Long comparisonId) {
        return comparisonRepository.findByIdAndUserId(comparisonId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "That metric comparison does not exist."));
    }

    public Map<String, Object> summary(Long userId, MetricComparison comparison) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", comparison.getId());
        body.put("createdAt", comparison.getCreatedAt().toString());
        body.put("manualDatasetId", comparison.getManualDatasetId());
        body.put("predefinedDatasetId", comparison.getPredefinedDatasetId());
        body.put("manualDataset", DatasetSummaryMapper.toSummary(
                datasetService.require(userId, comparison.getManualDatasetId())));
        body.put("predefinedDataset", DatasetSummaryMapper.toSummary(
                datasetService.require(userId, comparison.getPredefinedDatasetId())));
        body.put("comparisonConfig", readJson(comparison.getComparisonConfig()));
        body.put("reportFileAvailable", true);
        return body;
    }

    public Map<String, Object> detail(Long userId, MetricComparison comparison) {
        return detail(userId, comparison, true);
    }

    private Map<String, Object> detail(
            Long userId, MetricComparison comparison, boolean cacheHit) {
        Map<String, Object> body = summary(userId, comparison);
        body.put("result", readResult(comparison));
        body.put("cacheHit", cacheHit);
        return body;
    }

    @Transactional
    public void delete(Long userId, Long comparisonId) throws IOException {
        MetricComparison comparison = require(userId, comparisonId);
        comparisonRepository.delete(comparison);
        Path pdf = Paths.get(comparison.getComparisonReportFilePath());
        Files.deleteIfExists(pdf);
        Files.deleteIfExists(metadataPath(pdf));
    }

    public Path reportFile(Long userId, Long comparisonId) {
        Path path = Paths.get(require(userId, comparisonId)
                .getComparisonReportFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("The metric-comparison report is unavailable.");
        }
        return path;
    }

    private Map<String, Object> readResult(MetricComparison comparison) {
        Path path = metadataPath(Paths.get(comparison.getComparisonReportFilePath()));
        if (!Files.isRegularFile(path)) return Map.of();
        try {
            return objectMapper.readValue(path.toFile(),
                    new TypeReference<Map<String, Object>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "The metric-comparison metadata is unreadable.", exception);
        }
    }

    /**
     * Comparisons saved before the per-metric mean/std summary was added to the
     * comparison result are missing the "metrics" field. Recompute and overwrite
     * those stored reports once at startup so old comparisons pick up the new
     * summary without requiring a manual re-run. Cache-hit reads themselves stay
     * cheap and never touch dataset files (see MetricComparisonServiceTest).
     */
    private void migrateStaleComparisons() {
        for (MetricComparison comparison : comparisonRepository.findAll()) {
            if (readResult(comparison).containsKey("metrics")) continue;
            try {
                refreshStoredResult(comparison);
            } catch (RuntimeException | IOException exception) {
                // Best-effort: skip comparisons whose source datasets are gone.
            }
        }
    }

    private void refreshStoredResult(MetricComparison comparison) throws IOException {
        Long userId = comparison.getUserId();
        MetricDataset manual = datasetService.require(userId, comparison.getManualDatasetId());
        MetricDataset predefined = datasetService.require(
                userId, comparison.getPredefinedDatasetId());
        DatasetTable manualTable = datasetService.load(manual);
        DatasetTable predefinedTable = datasetService.load(predefined);
        Map<String, Object> config = readJson(comparison.getComparisonConfig());
        Map<String, Object> result = "AGGREGATE".equals(config.get("comparisonMode"))
                ? aggregate(manual, manualTable, predefinedTable)
                : instanceWise(manual, manualTable, predefinedTable, config);
        Path pdf = Paths.get(comparison.getComparisonReportFilePath());
        Files.writeString(metadataPath(pdf), writeJson(result), StandardCharsets.UTF_8);
        writePdf(pdf, manual, predefined, config, result);
    }

    private static String datasetIdentity(MetricDataset dataset) {
        return dataset.getDatasetFamily().name() + "|"
                + normalizeIdentityPart(dataset.getProjectName()) + "|"
                + normalizeIdentityPart(dataset.getProjectVersion());
    }

    private static String normalizeIdentityPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static Set<Long> datasetIds(List<MetricDataset> datasets) {
        Set<Long> ids = new LinkedHashSet<>();
        for (MetricDataset dataset : datasets) ids.add(dataset.getId());
        return ids;
    }

    private static MetricDataset preferredDataset(
            List<MetricDataset> datasets, Long userId) {
        for (MetricDataset dataset : datasets) {
            if (userId.equals(dataset.getUserId())) return dataset;
        }
        return datasets.isEmpty() ? null : datasets.get(0);
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() { });
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize comparison metadata.", exception);
        }
    }

    private static Map<String, List<String>> rowsByIdentifier(
            DatasetTable table, int identifierIndex) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (List<String> row : table.getRows()) {
            String original = cell(row, identifierIndex);
            rows.putIfAbsent(normalizeIdentifier(original), row);
        }
        return rows;
    }

    private static String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/').replaceAll("\\.java$", "")
                .replaceAll("\\s+", "");
    }

    private static String status(
            Double left, Double right, double absolute, double relative) {
        if (left == null || right == null) return "NOT_COMPARABLE";
        if (Double.compare(left, right) == 0) return "EXACT_MATCH";
        double difference = Math.abs(left - right);
        double relativeLimit = relative * Math.max(Math.abs(left), Math.abs(right));
        return difference <= Math.max(absolute, relativeLimit)
                ? "CLOSE_MATCH" : "MISMATCH";
    }

    private static List<Double> numericValues(List<String> values) {
        List<Double> result = new ArrayList<>();
        for (String value : values) {
            Double parsed = parseDouble(value);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private static boolean hasNumeric(List<String> values) {
        return values.stream().anyMatch(value -> parseDouble(value) != null);
    }

    private static Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "?".equals(value.trim())) return null;
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Stats stats(List<Double> values) {
        if (values.isEmpty()) return new Stats(0, null, null, null, null, null);
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double mean = Arrays.stream(sorted).average().orElse(0);
        double variance = Arrays.stream(sorted)
                .map(value -> (value - mean) * (value - mean)).average().orElse(0);
        double median = sorted.length % 2 == 0
                ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
                : sorted[sorted.length / 2];
        return new Stats(sorted.length, mean, Math.sqrt(variance), median,
                sorted[0], sorted[sorted.length - 1]);
    }

    private static Double difference(Double left, Double right) {
        return left == null || right == null ? null : left - right;
    }

    private static Double percentageDifference(Double left, Double right) {
        if (left == null || right == null) return null;
        if (right == 0) return left == 0 ? 0.0 : null;
        return (left - right) / Math.abs(right) * 100.0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object raw) {
        return raw instanceof List
                ? (List<Map<String, Object>>) raw : Collections.emptyList();
    }

    private static String cell(List<String> row, int index) {
        return row == null || index < 0 || index >= row.size() ? "" : row.get(index);
    }

    private static String nullableText(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean
                ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static double doubleValue(Object value, double fallback) {
        if (value == null) return fallback;
        return value instanceof Number
                ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private static Long requiredLong(Map<String, Object> body, String key) {
        try {
            Object value = body.get(key);
            if (value == null) throw new NumberFormatException();
            return value instanceof Number
                    ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " is required and must be a number.");
        }
    }

    private static Path metadataPath(Path pdf) {
        return pdf.resolveSibling(pdf.getFileName().toString() + ".json");
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static final class Stats {
        private final int count;
        private final Double mean;
        private final Double standardDeviation;
        private final Double median;
        private final Double minimum;
        private final Double maximum;

        private Stats(int count, Double mean, Double standardDeviation,
                      Double median, Double minimum, Double maximum) {
            this.count = count;
            this.mean = mean;
            this.standardDeviation = standardDeviation;
            this.median = median;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("recordCount", count);
            map.put("mean", mean);
            map.put("standardDeviation", standardDeviation);
            map.put("median", median);
            map.put("minimum", minimum);
            map.put("maximum", maximum);
            return map;
        }
    }
}
