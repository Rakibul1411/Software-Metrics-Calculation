package org.metrics.defectlab.prediction.application;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.application.DatasetSummaryMapper;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.prediction.domain.PredictionRun;
import org.metrics.defectlab.prediction.infrastructure.MlServiceClient;
import org.metrics.defectlab.prediction.persistence.PredictionRunRepository;
import org.metrics.defectlab.shared.exception.NotFoundException;
import org.metrics.defectlab.shared.report.PdfReportWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PredictionService {

    private static final int DEFAULT_SEED = 42;
    private static final int MAX_PUBLIC_PREDICTIONS = 5000;

    private final DatasetService datasetService;
    private final MlServiceClient mlServiceClient;
    private final PredictionRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final Path storageRoot = Paths.get("storage/predictions");

    public PredictionService(DatasetService datasetService,
                             MlServiceClient mlServiceClient,
                             PredictionRunRepository runRepository,
                             ObjectMapper objectMapper) throws IOException {
        this.datasetService = datasetService;
        this.mlServiceClient = mlServiceClient;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        Files.createDirectories(storageRoot);
    }

    /**
     * Generates every required artifact first and inserts database rows only
     * after both targets (for a dual request) have completed successfully.
     */
    @Transactional
    public Map<String, Object> execute(Long userId, Map<String, Object> body)
            throws IOException {
        MetricDataset source = datasetService.require(
                userId, requiredLong(body, "sourceDatasetId"));
        if (!source.hasActualLabel()) {
            throw new IllegalArgumentException(
                    "Source dataset must contain actual Buggy/Clean labels.");
        }

        MetricDataset manual = optionalDataset(userId, body.get("manualTargetDatasetId"),
                body.get("manualDatasetId"));
        MetricDataset predefined = optionalDataset(
                userId, body.get("predefinedTargetDatasetId"), body.get("predefinedDatasetId"));
        if (manual == null && predefined == null) {
            throw new IllegalArgumentException(
                    "Select a MANUAL target, a PREDEFINED target, or both.");
        }
        validateTargets(source, manual, predefined);

        Map<String, Object> modelConfig = modelConfig(body, source);
        UUID groupId = manual != null && predefined != null ? UUID.randomUUID() : null;
        DatasetTable sourceTable = datasetService.load(source);
        Path userDirectory = storageRoot.resolve(String.valueOf(userId));
        Files.createDirectories(userDirectory);

        List<GeneratedRun> generated = new ArrayList<>();
        try {
            if (manual != null) {
                generated.add(generate(
                        source, sourceTable, manual, modelConfig, groupId, userDirectory));
            }
            if (predefined != null) {
                generated.add(generate(
                        source, sourceTable, predefined, modelConfig, groupId, userDirectory));
            }

            List<PredictionRun> saved = new ArrayList<>();
            for (GeneratedRun item : generated) {
                saved.add(runRepository.save(new PredictionRun(
                        userId, groupId, source.getId(), item.target.getId(),
                        writeJson(modelConfig),
                        item.predictionCsv == null ? null : absolute(item.predictionCsv),
                        absolute(item.reportPdf))));
            }
            runRepository.flush();

            List<Map<String, Object>> runs = saved.stream()
                    .map(run -> detail(userId, run)).collect(Collectors.toList());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("comparisonGroupId", groupId == null ? null : groupId.toString());
            response.put("runIds", saved.stream().map(PredictionRun::getId).toList());
            response.put("runs", runs);
            return response;
        } catch (RuntimeException | IOException exception) {
            for (GeneratedRun item : generated) {
                deleteArtifacts(item);
            }
            throw exception;
        }
    }

    private GeneratedRun generate(
            MetricDataset source, DatasetTable sourceTable, MetricDataset target,
            Map<String, Object> modelConfig, UUID groupId, Path userDirectory)
            throws IOException {
        DatasetTable targetTable = datasetService.load(target);
        Map<String, Object> mlResponse = mlServiceClient.predict(
                predictionRequest(sourceTable, targetTable, source, modelConfig));
        List<Map<String, Object>> predictions = predictionsOf(mlResponse);
        if (predictions.size() != targetTable.getRowCount()) {
            throw new IllegalStateException(
                    "The ML service did not return one prediction per target record.");
        }

        Map<String, Object> evaluation = Map.of();
        if (target.getDatasetType() == MetricDataset.Type.PREDEFINED) {
            if (predictions.stream().anyMatch(row -> row.get("actualLabel") == null)) {
                throw new IllegalArgumentException(
                        "Every PREDEFINED target record must contain an actual label.");
            }
            Map<String, Object> evaluationRequest = new LinkedHashMap<>();
            evaluationRequest.put("results", predictions);
            evaluation = mlServiceClient.evaluate(evaluationRequest);
        }

        String token = UUID.randomUUID().toString();
        Path predictionCsv = target.getDatasetType() == MetricDataset.Type.MANUAL
                ? userDirectory.resolve(token + "-labeled.csv") : null;
        Path reportPdf = userDirectory.resolve(token + "-report.pdf");
        Path metadataJson = metadataPath(reportPdf);

        try {
            if (predictionCsv != null) {
                writeManualCsv(predictionCsv, targetTable, predictions);
            }
            Map<String, Object> metadata = metadata(
                    source, target, modelConfig, groupId, predictions, evaluation, mlResponse);
            writePredictionPdf(reportPdf, source, target, modelConfig, predictions, evaluation);
            Files.writeString(metadataJson, writeJson(metadata), StandardCharsets.UTF_8);
            return new GeneratedRun(target, predictionCsv, reportPdf, metadataJson);
        } catch (RuntimeException | IOException exception) {
            deleteIfPresent(predictionCsv);
            Files.deleteIfExists(reportPdf);
            Files.deleteIfExists(metadataJson);
            throw exception;
        }
    }

    private void validateTargets(
            MetricDataset source, MetricDataset manual, MetricDataset predefined) {
        if (manual != null) {
            validateDifferentAndFamily(source, manual);
            if (manual.getDatasetType() != MetricDataset.Type.MANUAL) {
                throw new IllegalArgumentException("Manual target must use dataset type MANUAL.");
            }
        }
        if (predefined != null) {
            // A PREDEFINED target must differ from the source just as a MANUAL one
            // does: ck_prediction_different rejects the row either way, and
            // without this check the whole model run happens before the insert
            // fails with an opaque database error.
            validateDifferentAndFamily(source, predefined);
            if (predefined.getDatasetType() != MetricDataset.Type.PREDEFINED) {
                throw new IllegalArgumentException(
                        "Predefined target must use dataset type PREDEFINED.");
            }
            if (!predefined.hasActualLabel()) {
                throw new IllegalArgumentException(
                        "Predefined target must contain actual Buggy/Clean labels.");
            }
        }
    }

    private void validateDifferentAndFamily(MetricDataset source, MetricDataset target) {
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException(
                    "Source and target must reference different dataset records.");
        }
        validateSameFamily(source, target);
    }

    private void validateSameFamily(MetricDataset source, MetricDataset target) {
        if (source.getDatasetFamily() != target.getDatasetFamily()) {
            throw new IllegalArgumentException(
                    "Source and target must use the same metric family.");
        }
    }

    private Map<String, Object> modelConfig(
            Map<String, Object> body, MetricDataset source) {
        String modelName = String.valueOf(
                body.getOrDefault("modelName", "KNN")).trim().toUpperCase(Locale.ROOT);
        if (!"KNN".equals(modelName)) {
            throw new IllegalArgumentException("Only KNN is supported.");
        }
        double threshold = doubleValue(body.get("threshold"), 0.5);
        if (!(threshold > 0.0 && threshold < 1.0)) {
            throw new IllegalArgumentException("Threshold must be between 0 and 1.");
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("modelName", modelName);
        config.put("threshold", threshold);
        config.put("logTransform", true);
        config.put("coral", body.containsKey("coral")
                ? booleanValue(body.get("coral")) : true);
        config.put("seed", integerValue(body.get("seed"), DEFAULT_SEED));
        config.put("datasetFamily", source.getDatasetFamily().name());

        int k = integerValue(body.get("k"), 3);
        if (k < 1 || k > 5) {
            throw new IllegalArgumentException("KNN K must be between 1 and 5.");
        }
        config.put("k", k);
        return config;
    }

    private Map<String, Object> predictionRequest(
            DatasetTable source, DatasetTable target, MetricDataset dataset,
            Map<String, Object> config) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceRows", asRowMaps(source));
        request.put("targetRows", asRowMaps(target));
        request.put("family", dataset.getDatasetFamily().name());
        request.put("modelName", config.get("modelName"));
        request.put("threshold", config.get("threshold"));
        request.put("seed", config.get("seed"));
        request.put("coral", config.get("coral"));
        request.put("k", config.get("k"));
        return request;
    }

    private void writeManualCsv(
            Path file, DatasetTable target, List<Map<String, Object>> predictions)
            throws IOException {
        List<String> headers = new ArrayList<>(target.getHeaders());
        headers.add("predicted_label");
        Map<String, Deque<Integer>> labels = new LinkedHashMap<>();
        for (Map<String, Object> prediction : predictions) {
            labels.computeIfAbsent(String.valueOf(prediction.get("classIdentifier")),
                    ignored -> new ArrayDeque<>())
                    .add(integerValue(prediction.get("predictedLabel"), 0));
        }
        int identifierIndex = target.indexOf("name");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(headers.toArray(String[]::new)).build())) {
            for (int index = 0; index < target.getRows().size(); index++) {
                List<String> original = target.getRows().get(index);
                String identifier = identifierIndex >= 0 && identifierIndex < original.size()
                        ? original.get(identifierIndex) : "row_" + index;
                Deque<Integer> queue = labels.get(identifier);
                if (queue == null || queue.isEmpty()) {
                    throw new IllegalStateException(
                            "Could not align predictions to original manual records.");
                }
                List<Object> output = new ArrayList<>(original);
                output.add(queue.removeFirst());
                printer.printRecord(output);
            }
        }
    }

    private Map<String, Object> metadata(
            MetricDataset source, MetricDataset target, Map<String, Object> config,
            UUID groupId, List<Map<String, Object>> predictions,
            Map<String, Object> evaluation, Map<String, Object> mlResponse) {
        long buggy = predictions.stream()
                .filter(row -> integerValue(row.get("predictedLabel"), 0) == 1).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRecords", predictions.size());
        summary.put("predictedBuggy", buggy);
        summary.put("predictedClean", predictions.size() - buggy);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "COMPLETED");
        metadata.put("comparisonGroupId", groupId == null ? null : groupId.toString());
        metadata.put("targetType", target.getDatasetType().name());
        metadata.put("sourceDataset", DatasetSummaryMapper.toSummary(source));
        metadata.put("targetDataset", DatasetSummaryMapper.toSummary(target));
        metadata.put("modelConfig", config);
        metadata.put("summary", summary);
        metadata.put("evaluation", evaluation.isEmpty() ? null : evaluation);
        metadata.put("predictions", predictions);
        metadata.put("warnings", mlResponse.getOrDefault("warnings", List.of()));
        return metadata;
    }

    private void writePredictionPdf(
            Path report, MetricDataset source, MetricDataset target,
            Map<String, Object> config, List<Map<String, Object>> predictions,
            Map<String, Object> evaluation) throws IOException {
        long buggy = predictions.stream()
                .filter(row -> integerValue(row.get("predictedLabel"), 0) == 1).count();
        List<String> lines = new ArrayList<>();
        lines.add("Source: " + source.getDisplayName() + " (" + source.getDatasetFamily() + ")");
        lines.add("Target: " + target.getDisplayName() + " (" + target.getDatasetType() + ")");
        lines.add("Model configuration: " + config);
        lines.add("Total predicted buggy files: " + buggy);
        lines.add("Total predicted clean files: " + (predictions.size() - buggy));
        if (!evaluation.isEmpty()) {
            lines.add("");
            lines.add("EVALUATION METRICS");
            lines.add("Accuracy: " + metricValue(evaluation.get("accuracy")));
            lines.add("Precision: " + metricValue(evaluation.get("precision")));
            lines.add("Recall: " + metricValue(evaluation.get("recall")));
            lines.add("F1-score: " + metricValue(evaluation.get("f1")));
            lines.add("ROC-AUC: " + metricValue(evaluation.get("rocAuc")));
            lines.add("Confusion matrix: " + evaluation.get("confusionMatrix"));
        }
        lines.add("");
        lines.add(target.hasActualLabel()
                ? "RANK | FILE/IDENTIFIER | PROBABILITY | PREDICTED | ACTUAL"
                : "RANK | FILE/IDENTIFIER | PROBABILITY | PREDICTED");
        for (Map<String, Object> row : predictions) {
            String line = row.get("riskRank") + " | " + row.get("classIdentifier")
                    + " | " + probability(row) + " | "
                    + classLabel(row.get("predictedLabel"));
            if (target.hasActualLabel()) {
                line += " | " + classLabel(row.get("actualLabel"));
            }
            lines.add(line);
        }
        PdfReportWriter.write(report, "DefectLab Prediction Report", lines);
    }

    @Transactional(readOnly = true)
    public List<PredictionRun> list(Long userId) {
        return runRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public PredictionRun require(Long userId, Long runId) {
        return runRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new NotFoundException("That prediction run does not exist."));
    }

    @Transactional(readOnly = true)
    public long countFor(Long userId) {
        return runRepository.countByUserId(userId);
    }

    public Map<String, Object> summary(Long userId, PredictionRun run) {
        Map<String, Object> metadata = readMetadata(run);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", run.getId());
        body.put("comparisonGroupId", run.getComparisonGroupId() == null
                ? null : run.getComparisonGroupId().toString());
        body.put("createdAt", run.getCreatedAt().toString());
        body.put("sourceDataset", DatasetSummaryMapper.toSummary(
                datasetService.require(userId, run.getSourceDatasetId())));
        body.put("targetDataset", DatasetSummaryMapper.toSummary(
                datasetService.require(userId, run.getTargetDatasetId())));
        body.put("modelConfig", readJson(run.getModelConfig()));
        body.put("status", "COMPLETED");
        body.put("predictionFileAvailable", run.getPredictionFilePath() != null);
        body.put("reportFileAvailable", true);
        body.put("summary", canonicalSummary(metadata.get("summary")));
        body.put("evaluation", metadata.get("evaluation"));
        return body;
    }

    public Map<String, Object> detail(Long userId, PredictionRun run) {
        Map<String, Object> body = summary(userId, run);
        Map<String, Object> metadata = readMetadata(run);
        body.put("warnings", metadata.getOrDefault("warnings", List.of()));
        body.put("predictions", limitPredictions(metadata, 100));
        return body;
    }

    public List<Map<String, Object>> grouped(Long userId) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (PredictionRun run : list(userId)) {
            String key = run.getComparisonGroupId() == null
                    ? "run-" + run.getId() : run.getComparisonGroupId().toString();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(summary(userId, run));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("comparisonGroupId",
                    entry.getKey().startsWith("run-") ? null : entry.getKey());
            row.put("runs", entry.getValue());
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> predictions(
            Long userId, Long runId, int limit, boolean buggyOnly) {
        PredictionRun run = require(userId, runId);
        List<Map<String, Object>> rows = predictionRows(readMetadata(run));
        return rows.stream()
                .filter(row -> !buggyOnly
                        || integerValue(row.get("predictedLabel"), 0) == 1)
                .limit(Math.max(1, Math.min(limit, MAX_PUBLIC_PREDICTIONS)))
                .toList();
    }

    public Path predictionFile(Long userId, Long runId) {
        PredictionRun run = require(userId, runId);
        if (run.getPredictionFilePath() == null) {
            throw new NotFoundException(
                    "Only MANUAL target runs have a downloadable labeled CSV.");
        }
        return requireFile(run.getPredictionFilePath());
    }

    public Path reportFile(Long userId, Long runId) {
        return requireFile(require(userId, runId).getReportFilePath());
    }

    private Map<String, Object> readMetadata(PredictionRun run) {
        Path path = metadataPath(Paths.get(run.getReportFilePath()));
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(path.toFile(),
                    new TypeReference<Map<String, Object>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("The prediction metadata file is unreadable.", exception);
        }
    }

    public Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() { });
        } catch (IOException exception) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> predictionsOf(Map<String, Object> response) {
        Object raw = response.get("predictions");
        return raw instanceof List ? (List<Map<String, Object>>) raw : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> predictionRows(Map<String, Object> metadata) {
        Object raw = metadata.get("predictions");
        return raw instanceof List ? (List<Map<String, Object>>) raw : List.of();
    }

    private List<Map<String, Object>> limitPredictions(
            Map<String, Object> metadata, int limit) {
        return predictionRows(metadata).stream().limit(limit).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> canonicalSummary(Object raw) {
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>((Map<String, Object>) raw);
        Object legacyBuggy = summary.remove("predictedDefective");
        Object legacyClean = summary.remove("predictedNonDefective");
        if (!summary.containsKey("predictedBuggy") && legacyBuggy != null) {
            summary.put("predictedBuggy", legacyBuggy);
        }
        if (!summary.containsKey("predictedClean") && legacyClean != null) {
            summary.put("predictedClean", legacyClean);
        }
        return summary;
    }

    private List<Map<String, String>> asRowMaps(DatasetTable table) {
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : table.getRows()) {
            Map<String, String> mapped = new LinkedHashMap<>();
            for (int index = 0; index < table.getHeaders().size(); index++) {
                mapped.put(table.getHeaders().get(index),
                        index < row.size() ? row.get(index) : "");
            }
            result.add(mapped);
        }
        return result;
    }

    private MetricDataset optionalDataset(
            Long userId, Object preferred, Object compatibility) {
        Object value = preferred != null ? preferred : compatibility;
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return datasetService.require(userId, longValue(value, "target dataset id"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize workflow metadata.", exception);
        }
    }

    private static Path metadataPath(Path report) {
        return report.resolveSibling(report.getFileName().toString() + ".json");
    }

    private static Path requireFile(String raw) {
        Path path = Paths.get(raw).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("The saved artifact is unavailable.");
        }
        return path;
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static void deleteArtifacts(GeneratedRun item) {
        try {
            deleteIfPresent(item.predictionCsv);
            Files.deleteIfExists(item.reportPdf);
            Files.deleteIfExists(item.metadataJson);
        } catch (IOException ignored) {
            // Original workflow failure is more useful than a cleanup failure.
        }
    }

    private static void deleteIfPresent(Path path) throws IOException {
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    private static String probability(Map<String, Object> row) {
        Object value = row.containsKey("defectProbability")
                ? row.get("defectProbability") : row.get("defectScore");
        return String.valueOf(value);
    }

    private static String classLabel(Object value) {
        return integerValue(value, 0) == 1 ? "Buggy" : "Clean";
    }

    @SuppressWarnings("unchecked")
    private static Object metricValue(Object raw) {
        return raw instanceof Map
                ? ((Map<String, Object>) raw).get("value") : raw;
    }

    private static Long requiredLong(Map<String, Object> body, String key) {
        if (!body.containsKey(key)) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return longValue(body.get(key), key);
    }

    private static Long longValue(Object value, String label) {
        try {
            return value instanceof Number
                    ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private static int integerValue(Object value, int fallback) {
        if (value == null) return fallback;
        return value instanceof Number
                ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static double doubleValue(Object value, double fallback) {
        if (value == null) return fallback;
        return value instanceof Number
                ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean
                ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static final class GeneratedRun {
        private final MetricDataset target;
        private final Path predictionCsv;
        private final Path reportPdf;
        private final Path metadataJson;

        private GeneratedRun(MetricDataset target, Path predictionCsv,
                             Path reportPdf, Path metadataJson) {
            this.target = target;
            this.predictionCsv = predictionCsv;
            this.reportPdf = reportPdf;
            this.metadataJson = metadataJson;
        }
    }
}
