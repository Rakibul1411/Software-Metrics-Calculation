package org.metrics.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.metrics.aeeem.calculator.legacy.CBOCalculator;
import org.metrics.aeeem.calculator.legacy.DITCalculator;
import org.metrics.aeeem.calculator.legacy.NOCCalculator;
import org.metrics.promise.analyzer.PromiseProjectAnalyzer;
import org.metrics.promise.model.PromiseMetricResult;
import org.metrics.promise.export.PromiseCsvExporter;

import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.parser.AeeemJavaSourceParser;
import org.metrics.aeeem.export.AeeemCsvExporter;

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
        private final String downloadUrl;

        public ExtractionResult(String targetDatasetId, String datasetFormat, int rowCount,
                                List<String> extractedColumns, List<String> csvPreview, String downloadUrl) {
            this.targetDatasetId = targetDatasetId;
            this.datasetFormat = datasetFormat;
            this.rowCount = rowCount;
            this.extractedColumns = extractedColumns;
            this.csvPreview = csvPreview;
            this.downloadUrl = downloadUrl;
        }

        public String getTargetDatasetId() { return targetDatasetId; }
        public String getDatasetFormat() { return datasetFormat; }
        public int getRowCount() { return rowCount; }
        public List<String> getExtractedColumns() { return extractedColumns; }
        public List<String> getCsvPreview() { return csvPreview; }
        public String getDownloadUrl() { return downloadUrl; }
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
        Path outputFile = outputDirPath.resolve("extracted-metrics-" + targetDatasetId + ".csv");

        List<String> columns;
        int rowCount;
        if ("aeeem".equals(normalizedFormat)) {
            List<AeeemMetricResult> allMetrics = calculateAeeemMetricsForDirectories(sourceDirs);
            if (filterFile != null && !filterFile.trim().isEmpty()) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
            }
            AeeemCsvExporter.exportAeeemToCSV(allMetrics, outputFile);
            columns = getAeeemColumns();
            rowCount = allMetrics.size();
        } else {
            List<PromiseMetricResult> allMetrics = calculatePromiseMetricsForDirectories(sourceDirs);
            if (filterFile != null && !filterFile.trim().isEmpty()) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
            }
            PromiseCsvExporter.exportPromiseToCSV(allMetrics, outputFile);
            columns = getPromiseColumns();
            rowCount = allMetrics.size();
        }

        if (rowCount == 0) {
            Files.deleteIfExists(outputFile);
            throw new IllegalArgumentException("No Java classes were found in the supplied project.");
        }

        // Include every extracted class; the UI keeps this bounded with scrollbars.
        List<String> csvPreview = new ArrayList<>();
        if (Files.exists(outputFile)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(outputFile.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    csvPreview.add(line);
                }
            }
        }

        String downloadUrl = "/api/metrics/download/" + targetDatasetId;

        return new ExtractionResult(targetDatasetId, normalizedFormat, rowCount, columns, csvPreview, downloadUrl);
    }

    private String normalizeDatasetFormat(String datasetFormat) {
        String normalized = datasetFormat == null ? "promise" : datasetFormat.trim().toLowerCase(Locale.ROOT);
        if (!"promise".equals(normalized) && !"aeeem".equals(normalized)) {
            throw new IllegalArgumentException("Dataset format must be PROMISE or AEEEM.");
        }
        return normalized;
    }

    public Path getDatasetPath(String targetDatasetId) {
        return Paths.get(OUTPUT_DIR).resolve("extracted-metrics-" + targetDatasetId + ".csv");
    }

    private Set<String> loadClassNamesFromCSV(String csvPath) throws IOException {
        Set<String> classNames = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
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
        for (String dirPath : dirPaths) {
            Path sourcePath = Paths.get(dirPath.trim());
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) continue;

            try (Stream<Path> paths = Files.walk(sourcePath)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().contains("/optional/") && !path.toString().contains("\\optional\\"))
                        .filter(path -> !path.toString().contains("/test/") && !path.toString().contains("\\test\\"))
                        .forEach(javaFile -> {
                            try {
                                List<AeeemMetricResult> metrics = AeeemJavaSourceParser.parseAeeemFile(javaFile);
                                allMetrics.addAll(metrics);
                            } catch (Exception ignored) {}
                        });
            }
        }
        calculateAeeemProjectMetrics(allMetrics);
        return allMetrics;
    }

    private void calculateAeeemProjectMetrics(List<AeeemMetricResult> allMetrics) {
        DITCalculator ditCalculator = new DITCalculator();
        NOCCalculator nocCalculator = new NOCCalculator();
        CBOCalculator cboCalculator = new CBOCalculator();
        Map<String, AeeemMetricResult> classesByName = new HashMap<>();

        for (AeeemMetricResult metrics : allMetrics) {
            String name = metrics.getFullyQualifiedName();
            ditCalculator.registerClass(name, metrics.getSuperclassName(), metrics.isInterface());
            nocCalculator.registerClass(name, metrics.getSuperclassName());
            cboCalculator.registerClass(name, metrics.getSuperclassName(), metrics.getDependencies());
            classesByName.put(name, metrics);
            int dot = name.lastIndexOf('.');
            classesByName.putIfAbsent(dot < 0 ? name : name.substring(dot + 1), metrics);
        }
        cboCalculator.postProcessDependencies();

        for (AeeemMetricResult metrics : allMetrics) {
            String name = metrics.getFullyQualifiedName();
            int dit = ditCalculator.calculateDIT(name);
            int noc = nocCalculator.calculateNOC(name);
            int fanIn = cboCalculator.calculateCA(name);
            int fanOut = cboCalculator.calculateCE(name);
            int cbo = cboCalculator.calculateCBO(name);
            int inheritedAttributes = 0;
            Set<String> inheritedMethods = new HashSet<>();
            Set<String> visited = new HashSet<>();
            AeeemMetricResult parent = classesByName.get(metrics.getSuperclassName());
            while (parent != null && visited.add(parent.getFullyQualifiedName())) {
                inheritedAttributes += parent.getDeclaredAttributeCount();
                inheritedMethods.addAll(parent.getMethodNames());
                parent = classesByName.get(parent.getSuperclassName());
            }
            applyAeeemProjectMetrics(metrics, dit, noc, fanIn, fanOut, cbo,
                    inheritedAttributes, inheritedMethods.size());
        }
    }

    private void applyAeeemProjectMetrics(AeeemMetricResult metrics, int dit, int noc, int fanIn,
                                           int fanOut, int cbo, int attributesInherited, int methodsInherited) {
        metrics.setCkOoDit(dit); metrics.setLdhhDit(dit); metrics.setWchuDit(dit);
        metrics.setCkOoNoc(noc); metrics.setLdhhNoc(noc); metrics.setWchuNoc(noc);
        metrics.setCkOoFanIn(fanIn); metrics.setLdhhFanIn(fanIn); metrics.setWchuFanIn(fanIn);
        metrics.setCkOoFanOut(fanOut); metrics.setLdhhFanOut(fanOut); metrics.setWchuFanOut(fanOut);
        metrics.setCkOoCbo(cbo); metrics.setLdhhCbo(cbo); metrics.setWchuCbo(cbo);
        metrics.setCkOoNumberOfAttributesInherited(attributesInherited);
        metrics.setLdhhNumberOfAttributesInherited(attributesInherited);
        metrics.setWchuNumberOfAttributesInherited(attributesInherited);
        metrics.setCkOoNumberOfMethodsInherited(methodsInherited);
        metrics.setLdhhNumberOfMethodsInherited(methodsInherited);
        metrics.setWchuNumberOfMethodsInherited(methodsInherited);
    }

    private List<String> getPromiseColumns() {
        return Arrays.asList("name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc");
    }

    private List<String> getAeeemColumns() {
        return Arrays.asList(
                "name", "ck_oo_numberOfPrivateMethods", "LDHH_lcom", "LDHH_fanIn",
                "WCHU_numberOfPublicAttributes", "WCHU_numberOfAttributes", "LDHH_numberOfPublicMethods",
                "WCHU_fanIn", "LDHH_numberOfPrivateAttributes", "LDHH_numberOfPublicAttributes",
                "WCHU_numberOfPrivateMethods", "WCHU_numberOfMethods", "ck_oo_numberOfPublicAttributes", "ck_oo_noc",
                "ck_oo_wmc", "LDHH_numberOfPrivateMethods", "WCHU_numberOfPrivateAttributes",
                "WCHU_noc", "LDHH_numberOfAttributesInherited", "WCHU_wmc", "ck_oo_fanOut",
                "ck_oo_numberOfLinesOfCode", "ck_oo_numberOfAttributesInherited", "ck_oo_numberOfMethods", "ck_oo_dit",
                "ck_oo_fanIn", "LDHH_noc", "WCHU_dit", "ck_oo_lcom", "WCHU_numberOfAttributesInherited", "ck_oo_rfc",
                "LDHH_wmc", "LDHH_numberOfAttributes", "LDHH_numberOfLinesOfCode", "WCHU_fanOut", "WCHU_lcom", "ck_oo_cbo",
                "WCHU_rfc", "ck_oo_numberOfAttributes", "ck_oo_numberOfPrivateAttributes",
                "WCHU_numberOfPublicMethods", "LDHH_dit", "WCHU_cbo",
                "WCHU_numberOfMethodsInherited", "LDHH_fanOut", "LDHH_numberOfMethodsInherited",
                "LDHH_rfc", "ck_oo_numberOfMethodsInherited", "ck_oo_numberOfPublicMethods", "LDHH_cbo", "WCHU_numberOfLinesOfCode",
                "LDHH_numberOfMethods"
        );
    }
}
