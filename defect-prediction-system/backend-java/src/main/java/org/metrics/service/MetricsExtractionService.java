package org.metrics.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.metrics.promise.calculator.CBMCalculator;
import org.metrics.promise.calculator.CBOCalculator;
import org.metrics.promise.calculator.DITCalculator;
import org.metrics.promise.calculator.ICCalculator;
import org.metrics.promise.calculator.MFACalculator;
import org.metrics.promise.calculator.NOCCalculator;
import org.metrics.promise.model.PromiseMetricResult;
import org.metrics.promise.parser.PromiseJavaSourceParser;
import org.metrics.promise.export.PromiseCsvExporter;

import org.metrics.aeeem.model.AeeemMetricResult;
import org.metrics.aeeem.parser.AeeemJavaSourceParser;
import org.metrics.aeeem.export.AeeemCsvExporter;

import org.springframework.stereotype.Service;

@Service
public class MetricsExtractionService {

    private static final String OUTPUT_DIR = "output";

    public static class ExtractionResult {
        private String targetDatasetId;
        private List<String> extractedColumns;
        private List<String> csvPreview;
        private String downloadUrl;

        public ExtractionResult(String targetDatasetId, List<String> extractedColumns, List<String> csvPreview, String downloadUrl) {
            this.targetDatasetId = targetDatasetId;
            this.extractedColumns = extractedColumns;
            this.csvPreview = csvPreview;
            this.downloadUrl = downloadUrl;
        }

        public String getTargetDatasetId() { return targetDatasetId; }
        public List<String> getExtractedColumns() { return extractedColumns; }
        public List<String> getCsvPreview() { return csvPreview; }
        public String getDownloadUrl() { return downloadUrl; }
    }

    public ExtractionResult extractMetrics(String sourceDirsStr, String datasetFormat, String filterFile) throws IOException {
        String targetDatasetId = UUID.randomUUID().toString();
        String[] sourceDirs = sourceDirsStr.split(",");
        Path outputDirPath = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDirPath);
        Path outputFile = outputDirPath.resolve("extracted-metrics-" + targetDatasetId + ".csv");

        List<String> columns = new ArrayList<>();
        
        if ("aeeem".equalsIgnoreCase(datasetFormat)) {
            List<AeeemMetricResult> allMetrics = calculateAeeemMetricsForDirectories(sourceDirs);
            if (filterFile != null && !filterFile.trim().isEmpty()) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
            }
            AeeemCsvExporter.exportAeeemToCSV(allMetrics, outputFile);
            columns = getAeeemColumns();
        } else {
            List<PromiseMetricResult> allMetrics = calculatePromiseMetricsForDirectories(sourceDirs);
            if (filterFile != null && !filterFile.trim().isEmpty()) {
                Set<String> predefinedClasses = loadClassNamesFromCSV(filterFile);
                allMetrics.removeIf(m -> !predefinedClasses.contains(m.getFullyQualifiedName()));
            }
            PromiseCsvExporter.exportPromiseToCSV(allMetrics, outputFile, false);
            columns = getPromiseColumns();
        }

        // Generate CSV preview (header + first 5 data rows)
        List<String> csvPreview = new ArrayList<>();
        if (Files.exists(outputFile)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(outputFile.toFile()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 6) {
                    csvPreview.add(line);
                    count++;
                }
            }
        }

        String downloadUrl = "/api/metrics/download/" + targetDatasetId;

        return new ExtractionResult(targetDatasetId, columns, csvPreview, downloadUrl);
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
        List<PromiseMetricResult> allMetrics = new ArrayList<>();
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
                                List<PromiseMetricResult> metrics = PromiseJavaSourceParser.parsePromiseFile(javaFile);
                                allMetrics.addAll(metrics);
                            } catch (Exception ignored) {}
                        });
            }
        }

        calculateNOCForAllClasses(allMetrics);
        calculateDITForAllClasses(allMetrics);
        calculateCBOForAllClasses(allMetrics);
        calculateICForAllClasses(allMetrics);
        calculateCBMForAllClasses(allMetrics);
        calculateMFAForAllClasses(allMetrics);
        calculateMOAForAllClasses(allMetrics);

        return allMetrics;
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
        return allMetrics;
    }

    private void calculateMOAForAllClasses(List<PromiseMetricResult> allMetrics) {
        Set<String> projectClassNames = new HashSet<>();
        for (PromiseMetricResult metrics : allMetrics) {
            projectClassNames.add(metrics.getFullyQualifiedName());
            String simpleName = metrics.getFullyQualifiedName();
            int lastDot = simpleName.lastIndexOf('.');
            if (lastDot >= 0) {
                simpleName = simpleName.substring(lastDot + 1);
            }
            projectClassNames.add(simpleName);
        }

        for (PromiseMetricResult metrics : allMetrics) {
            int moa = 0;
            for (String fieldType : metrics.getFieldTypes()) {
                if (projectClassNames.contains(fieldType)) {
                    moa++;
                }
            }
            metrics.setMoa(moa);
        }
    }

    private void calculateCBOForAllClasses(List<PromiseMetricResult> allMetrics) {
        CBOCalculator cboCalculator = new CBOCalculator();
        for (PromiseMetricResult metrics : allMetrics) {
            cboCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getDependencies()
            );
        }
        cboCalculator.postProcessDependencies();

        for (PromiseMetricResult metrics : allMetrics) {
            int cbo = cboCalculator.calculateCBO(metrics.getFullyQualifiedName());
            int ca = cboCalculator.calculateCA(metrics.getFullyQualifiedName());
            int ce = cboCalculator.calculateCE(metrics.getFullyQualifiedName());
            metrics.setCbo(cbo);
            metrics.setCa(ca);
            metrics.setCe(ce);
        }
    }

    private void calculateNOCForAllClasses(List<PromiseMetricResult> allMetrics) {
        NOCCalculator nocCalculator = new NOCCalculator();
        for (PromiseMetricResult metrics : allMetrics) {
            nocCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName()
            );
        }

        for (PromiseMetricResult metrics : allMetrics) {
            int noc = nocCalculator.calculateNOC(metrics.getFullyQualifiedName());
            metrics.setNoc(noc);
        }
    }

    private void calculateDITForAllClasses(List<PromiseMetricResult> allMetrics) {
        DITCalculator ditCalculator = new DITCalculator();
        for (PromiseMetricResult metrics : allMetrics) {
            ditCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.isInterface()
            );
        }

        for (PromiseMetricResult metrics : allMetrics) {
            int dit = ditCalculator.calculateDIT(metrics.getFullyQualifiedName());
            metrics.setDit(dit);
        }
    }

    private void calculateICForAllClasses(List<PromiseMetricResult> allMetrics) {
        ICCalculator icCalculator = new ICCalculator();
        for (PromiseMetricResult metrics : allMetrics) {
            icCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getMethodNames()
            );
        }

        for (PromiseMetricResult metrics : allMetrics) {
            int ic = icCalculator.calculateIC(
                    metrics.getFullyQualifiedName(),
                    metrics.getInvokedMethods()
            );
            metrics.setIc(ic);
        }
    }

    private void calculateCBMForAllClasses(List<PromiseMetricResult> allMetrics) {
        CBMCalculator cbmCalculator = new CBMCalculator();
        for (PromiseMetricResult metrics : allMetrics) {
            cbmCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getMethodNames()
            );
        }

        for (PromiseMetricResult metrics : allMetrics) {
            int cbm = cbmCalculator.calculateCBMSimple(
                    metrics.getFullyQualifiedName(),
                    metrics.getInheritedMethodInvocations()
            );
            metrics.setCbm(cbm);
        }
    }

    private void calculateMFAForAllClasses(List<PromiseMetricResult> allMetrics) {
        MFACalculator mfaCalculator = new MFACalculator();
        for (PromiseMetricResult metrics : allMetrics) {
            mfaCalculator.registerClass(
                    metrics.getFullyQualifiedName(),
                    metrics.getSuperclassName(),
                    metrics.getMethodNames()
            );
        }

        for (PromiseMetricResult metrics : allMetrics) {
            if (metrics.isInterface()) {
                metrics.setMfa(0.0);
            } else {
                double mfa = mfaCalculator.calculateMFA(metrics.getFullyQualifiedName());
                metrics.setMfa(mfa);
            }
        }
    }

    private List<String> getPromiseColumns() {
        return Arrays.asList("name", "wmc", "dit", "noc", "cbo", "rfc", "lcom", "ca", "ce", "npm",
                "lcom3", "loc", "dam", "moa", "mfa", "cam", "ic", "cbm", "amc", "max_cc", "avg_cc");
    }

    private List<String> getAeeemColumns() {
        return Arrays.asList(
                "name", "ck_oo_numberOfPrivateMethods", "LDHH_lcom", "LDHH_fanIn", "numberOfNonTrivialBugsFoundUntil:",
                "WCHU_numberOfPublicAttributes", "WCHU_numberOfAttributes", "CvsWEntropy", "LDHH_numberOfPublicMethods",
                "WCHU_fanIn", "LDHH_numberOfPrivateAttributes", "CvsEntropy", "LDHH_numberOfPublicAttributes",
                "WCHU_numberOfPrivateMethods", "WCHU_numberOfMethods", "ck_oo_numberOfPublicAttributes", "ck_oo_noc",
                "numberOfCriticalBugsFoundUntil:", "ck_oo_wmc", "LDHH_numberOfPrivateMethods", "WCHU_numberOfPrivateAttributes",
                "CvsLogEntropy", "WCHU_noc", "LDHH_numberOfAttributesInherited", "WCHU_wmc", "ck_oo_fanOut",
                "ck_oo_numberOfLinesOfCode", "ck_oo_numberOfAttributesInherited", "ck_oo_numberOfMethods", "ck_oo_dit",
                "ck_oo_fanIn", "LDHH_noc", "WCHU_dit", "ck_oo_lcom", "WCHU_numberOfAttributesInherited", "ck_oo_rfc",
                "LDHH_wmc", "LDHH_numberOfAttributes", "LDHH_numberOfLinesOfCode", "WCHU_fanOut", "WCHU_lcom", "ck_oo_cbo",
                "WCHU_rfc", "ck_oo_numberOfAttributes", "numberOfHighPriorityBugsFoundUntil:", "ck_oo_numberOfPrivateAttributes",
                "numberOfMajorBugsFoundUntil:", "WCHU_numberOfPublicMethods", "LDHH_dit", "WCHU_cbo", "CvsLinEntropy",
                "WCHU_numberOfMethodsInherited", "numberOfBugsFoundUntil:", "LDHH_fanOut", "LDHH_numberOfMethodsInherited",
                "LDHH_rfc", "ck_oo_numberOfMethodsInherited", "ck_oo_numberOfPublicMethods", "LDHH_cbo", "WCHU_numberOfLinesOfCode",
                "CvsExpEntropy", "LDHH_numberOfMethods", "class"
        );
    }
}
