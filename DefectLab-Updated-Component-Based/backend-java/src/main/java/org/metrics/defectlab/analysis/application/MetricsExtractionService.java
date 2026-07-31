package org.metrics.defectlab.analysis.application;

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

import org.metrics.defectlab.analysis.aeeem.git.GitHistoryAnalyzer;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisSummary;
import org.metrics.defectlab.analysis.aeeem.export.AeeemArffExporter;
import org.metrics.defectlab.analysis.aeeem.export.AeeemCsvExporter;
import org.metrics.defectlab.analysis.aeeem.export.AeeemFeatureSchema;
import org.metrics.defectlab.analysis.aeeem.model.AeeemMetricResult;
import org.metrics.defectlab.shared.model.DatasetFileFormat;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseProjectAnalyzer;
import org.metrics.defectlab.analysis.promise.analyzer.PromiseInputValidator;
import org.metrics.defectlab.analysis.promise.export.PromiseArffExporter;
import org.metrics.defectlab.analysis.promise.export.PromiseCsvExporter;
import org.metrics.defectlab.analysis.promise.export.PromiseFeatureSchema;
import org.metrics.defectlab.analysis.promise.model.PromiseMetricResult;

import org.springframework.stereotype.Service;

@Service
public class MetricsExtractionService {

    private static final String OUTPUT_DIR = "output";

    public static class ExtractionResult {
        private final String targetDatasetId;
        private final String datasetFormat;
        private final int rowCount;
        private final List<String> extractedColumns;
        private final AeeemAnalysisSummary aeeemAnalysis;

        public ExtractionResult(String targetDatasetId, String datasetFormat, int rowCount,
                                List<String> extractedColumns,
                                AeeemAnalysisSummary aeeemAnalysis) {
            this.targetDatasetId = targetDatasetId;
            this.datasetFormat = datasetFormat;
            this.rowCount = rowCount;
            this.extractedColumns = extractedColumns;
            this.aeeemAnalysis = aeeemAnalysis;
        }

        public String getTargetDatasetId() { return targetDatasetId; }
        public String getDatasetFormat() { return datasetFormat; }
        public int getRowCount() { return rowCount; }
        public List<String> getExtractedColumns() { return extractedColumns; }
        public AeeemAnalysisSummary getAeeemAnalysis() { return aeeemAnalysis; }
    }

    public ExtractionResult extractMetrics(String sourceDirsStr, String datasetFormat, String filterFile) throws IOException {
        return extractMetrics(sourceDirsStr, datasetFormat, filterFile,
                AeeemAnalysisOptions.current());
    }

    public ExtractionResult extractMetrics(
            String sourceDirsStr,
            String datasetFormat,
            String filterFile,
            AeeemAnalysisOptions aeeemOptions) throws IOException {
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
        AeeemAnalysisSummary aeeemAnalysis = null;
        if ("aeeem".equals(normalizedFormat)) {
            AeeemCalculation aeeemCalculation = calculateAeeemMetricsForDirectories(
                    sourceDirs,
                    aeeemOptions == null ? AeeemAnalysisOptions.current() : aeeemOptions);
            List<AeeemMetricResult> allMetrics = aeeemCalculation.metrics;
            aeeemAnalysis = aeeemCalculation.summary;
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
            throw new IllegalArgumentException(filterFile == null || filterFile.trim().isEmpty()
                    ? "No Java classes were found in the supplied project."
                    : "No class in the predefined dataset matched this project release. "
                      + "Check that the labelled CSV belongs to the uploaded release.");
        }

        return new ExtractionResult(targetDatasetId, normalizedFormat, rowCount, columns,
                aeeemAnalysis);
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
        PromiseInputValidator.requireSingleRelease(sourcePaths);
        return PromiseProjectAnalyzer.analyzeDirectories(sourcePaths);
    }

    private AeeemCalculation calculateAeeemMetricsForDirectories(
            String[] dirPaths,
            AeeemAnalysisOptions options) throws IOException {
        List<AeeemMetricResult> allMetrics = new ArrayList<>();
        AeeemAnalysisSummary summary = null;
        GitHistoryAnalyzer analyzer = new GitHistoryAnalyzer();
        for (String dirPath : dirPaths) {
            Path sourcePath = Paths.get(dirPath.trim());
            if (Files.exists(sourcePath) && Files.isDirectory(sourcePath)) {
                GitHistoryAnalyzer.AnalysisResult result =
                        analyzer.analyzeWithSummary(sourcePath, options);
                allMetrics.addAll(result.getMetrics());
                if (summary == null) {
                    summary = result.getSummary();
                }
            }
        }
        return new AeeemCalculation(allMetrics, summary);
    }

    private List<String> getPromiseColumns() {
        return PromiseFeatureSchema.columns();
    }

    private List<String> getAeeemColumns() {
        return AeeemFeatureSchema.columnsWithIdentifier();
    }

    private static final class AeeemCalculation {
        private final List<AeeemMetricResult> metrics;
        private final AeeemAnalysisSummary summary;

        private AeeemCalculation(
                List<AeeemMetricResult> metrics,
                AeeemAnalysisSummary summary) {
            this.metrics = metrics;
            this.summary = summary;
        }
    }
}
