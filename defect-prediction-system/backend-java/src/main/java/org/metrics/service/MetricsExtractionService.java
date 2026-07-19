package org.metrics.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.metrics.aeeem.git.GitHistoryAnalyzer;
import org.metrics.aeeem.export.AeeemArffExporter;
import org.metrics.aeeem.export.AeeemCsvExporter;
import org.metrics.aeeem.export.AeeemFeatureSchema;
import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.common.enums.DatasetFileFormat;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer;
import org.metrics.promise.export.PromiseArffExporter;
import org.metrics.promise.export.PromiseCsvExporter;
import org.metrics.promise.export.PromiseFeatureSchema;
import org.metrics.promise.model.PromiseMetricResult;

import org.springframework.stereotype.Service;

@Service
public class MetricsExtractionService {

    private static final String OUTPUT_DIR = "output";

    public static class ExtractionResult {
        private final String targetDatasetId;
        private final String datasetFormat;
        private final int rowCount;
        private final List<String> extractedColumns;
        private final List<String> csvPreview;
        private final String csvDownloadUrl;
        private final String arffDownloadUrl;

        public ExtractionResult(String targetDatasetId, String datasetFormat, int rowCount,
                                List<String> extractedColumns, List<String> csvPreview,
                                String csvDownloadUrl, String arffDownloadUrl) {
            this.targetDatasetId = targetDatasetId;
            this.datasetFormat = datasetFormat;
            this.rowCount = rowCount;
            this.extractedColumns = extractedColumns;
            this.csvPreview = csvPreview;
            this.csvDownloadUrl = csvDownloadUrl;
            this.arffDownloadUrl = arffDownloadUrl;
        }

        public String getTargetDatasetId() { return targetDatasetId; }
        public String getDatasetFormat() { return datasetFormat; }
        public int getRowCount() { return rowCount; }
        public List<String> getExtractedColumns() { return extractedColumns; }
        public List<String> getCsvPreview() { return csvPreview; }
        public String getCsvDownloadUrl() { return csvDownloadUrl; }
        public String getArffDownloadUrl() { return arffDownloadUrl; }
    }

    public ExtractionResult extractMetrics(String sourceDirsStr, String datasetFormat, String filterFile) throws IOException {
        String normalizedFormat = normalizeDatasetFormat(datasetFormat);
        if (sourceDirsStr == null || sourceDirsStr.trim().isEmpty()) {
            throw new IllegalArgumentException("A Java source directory is required.");
        }
        String targetDatasetId = UUID.randomUUID().toString();
        String[] sourceDirs = sourceDirsStr.split(",");
        Path outputDirPath = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDirPath);
        Path csvOutput = getDatasetPath(targetDatasetId, DatasetFileFormat.CSV);
        Path arffOutput = getDatasetPath(targetDatasetId, DatasetFileFormat.ARFF);

        List<String> columns;
        int rowCount;
        if ("aeeem".equals(normalizedFormat)) {
            List<AeeemMetricResult> allMetrics = calculateAeeemMetricsForDirectories(sourceDirs);
            if (filterFile != null && !filterFile.trim().isEmpty()) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
            }
            AeeemCsvExporter.exportAeeemToCSV(allMetrics, csvOutput);
            AeeemArffExporter.exportAeeemToArff(allMetrics, arffOutput);
            columns = getAeeemColumns();
            rowCount = allMetrics.size();
        } else {
            List<PromiseMetricResult> allMetrics = calculatePromiseMetricsForDirectories(sourceDirs);
            if (filterFile != null && !filterFile.trim().isEmpty()) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
            }
            PromiseCsvExporter.exportPromiseToCSV(allMetrics, csvOutput);
            PromiseArffExporter.exportPromiseToArff(allMetrics, arffOutput);
            columns = getPromiseColumns();
            rowCount = allMetrics.size();
        }

        if (rowCount == 0) {
            Files.deleteIfExists(csvOutput);
            Files.deleteIfExists(arffOutput);
            throw new IllegalArgumentException("No Java classes were found in the supplied project.");
        }

        List<String> csvPreview = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvOutput, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                csvPreview.add(line);
            }
        }

        String downloadBaseUrl = "/api/metrics/download/" + targetDatasetId + "/";

        return new ExtractionResult(targetDatasetId, normalizedFormat, rowCount, columns, csvPreview,
                downloadBaseUrl + "csv", downloadBaseUrl + "arff");
    }

    private String normalizeDatasetFormat(String datasetFormat) {
        String normalized = datasetFormat == null ? "promise" : datasetFormat.trim().toLowerCase(Locale.ROOT);
        if (!"promise".equals(normalized) && !"aeeem".equals(normalized)) {
            throw new IllegalArgumentException("Dataset format must be PROMISE or AEEEM.");
        }
        return normalized;
    }

    public Path getDatasetPath(String targetDatasetId, DatasetFileFormat fileFormat) {
        return Paths.get(OUTPUT_DIR).resolve(
                "extracted-metrics-" + targetDatasetId + "." + fileFormat.getExtension());
    }

    private Set<String> loadClassNamesFromCSV(String csvPath) throws IOException {
        Set<String> classNames = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(csvPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int commaIndex = line.indexOf(',');
                if (commaIndex > 0) {
                    classNames.add(line.substring(0, commaIndex).trim());
                }
            }
        }
        return classNames;
    }

    private List<PromiseMetricResult> calculatePromiseMetricsForDirectories(String[] dirPaths) throws IOException {
        List<Path> sourcePaths = new ArrayList<>();
        for (String dirPath : dirPaths) {
            Path sourcePath = Paths.get(dirPath.trim());
            if (Files.exists(sourcePath) && Files.isDirectory(sourcePath)) {
                sourcePaths.add(sourcePath);
            }
        }
        return PromiseProjectAnalyzer.analyzeDirectories(sourcePaths);
    }

    private List<AeeemMetricResult> calculateAeeemMetricsForDirectories(String[] dirPaths) throws IOException {
        List<AeeemMetricResult> allMetrics = new ArrayList<>();
        GitHistoryAnalyzer analyzer = new GitHistoryAnalyzer();
        for (String dirPath : dirPaths) {
            Path sourcePath = Paths.get(dirPath.trim());
            if (Files.exists(sourcePath) && Files.isDirectory(sourcePath)) {
                allMetrics.addAll(analyzer.analyze(sourcePath));
            }
        }
        return allMetrics;
    }

    private List<String> getPromiseColumns() {
        return PromiseFeatureSchema.columns();
    }

    private List<String> getAeeemColumns() {
        return AeeemFeatureSchema.columnsWithIdentifier();
    }
}
