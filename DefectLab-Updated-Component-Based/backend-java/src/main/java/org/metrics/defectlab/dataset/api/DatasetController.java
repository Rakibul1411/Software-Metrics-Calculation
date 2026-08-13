package org.metrics.defectlab.dataset.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.dataset.application.DatasetService;
import org.metrics.defectlab.dataset.application.DatasetSummaryMapper;
import org.metrics.defectlab.dataset.domain.DatasetQuality;
import org.metrics.defectlab.dataset.domain.DatasetTable;
import org.metrics.defectlab.dataset.domain.FeatureProfile;
import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.metrics.defectlab.dataset.infrastructure.DatasetFileWriter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private static final int PREVIEW_ROWS = 25;

    private final DatasetService datasetService;
    private final CurrentUser currentUser;

    public DatasetController(DatasetService datasetService, CurrentUser currentUser) {
        this.datasetService = datasetService;
        this.currentUser = currentUser;
    }

    /**
     * Registers a ready metric CSV/ARFF. Source-code extraction is owned by the
     * dedicated {@code /api/analysis} component.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("datasetFile") MultipartFile datasetFile,
            @RequestParam(value = "projectName", required = false) String projectName,
            @RequestParam(value = "projectVersion", required = false) String projectVersion,
            @RequestParam(value = "datasetFamily", defaultValue = "PROMISE") String familyValue,
            @RequestParam(value = "datasetType", defaultValue = "PREDEFINED") String typeValue,
            HttpServletRequest request) throws IOException {
        Long userId = currentUser.requireUserId(request);
        MetricDataset.Family family = parseFamily(familyValue);
        MetricDataset dataset = datasetService.createFromUpload(
                userId, projectName, projectVersion, parseType(typeValue), datasetFile);
        if (dataset.getDatasetFamily() != family) {
            datasetService.delete(userId, dataset.getId());
            throw new IllegalArgumentException(
                    "The file contains " + dataset.getDatasetFamily()
                    + " metrics but " + family + " was selected.");
        }
        return ResponseEntity.ok(DatasetSummaryMapper.toSummary(dataset));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = currentUser.requireUserId(request);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MetricDataset dataset : datasetService.list(userId)) {
            result.add(DatasetSummaryMapper.toSummary(dataset));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable("id") Long id, HttpServletRequest request) {
        MetricDataset dataset = datasetService.require(
                currentUser.requireUserId(request), id);
        Map<String, Object> body = DatasetSummaryMapper.toSummary(dataset);
        body.put("features", datasetService.profileFor(dataset).getFeatures());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        MetricDataset dataset = datasetService.require(
                currentUser.requireUserId(request), id);
        DatasetTable table = datasetService.load(dataset);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headers", table.getHeaders());
        body.put("rows", table.getRows().subList(0, Math.min(PREVIEW_ROWS, table.getRowCount())));
        body.put("totalRows", table.getRowCount());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/quality")
    public ResponseEntity<Map<String, Object>> quality(
            @PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        MetricDataset dataset = datasetService.require(
                currentUser.requireUserId(request), id);
        DatasetTable table = datasetService.load(dataset);
        FeatureProfile profile = datasetService.profileFor(dataset);
        DatasetQuality quality = DatasetQuality.inspect(table, profile);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usable", quality.isUsable());
        body.put("blockingIssues", quality.getBlockingIssues());
        body.put("warnings", quality.getWarnings());
        body.put("columns", quality.getColumns());
        body.put("hasActualLabel", dataset.hasActualLabel());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("id") Long id,
            @RequestParam(value = "format", required = false) String format,
            HttpServletRequest request) throws IOException {
        MetricDataset dataset = datasetService.require(
                currentUser.requireUserId(request), id);
        Path file = Paths.get(dataset.getMetricsFilePath());
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String storedExtension = file.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(".arff") ? "arff" : "csv";
        String requestedFormat = parseDownloadFormat(format, storedExtension);

        if (requestedFormat.equals(storedExtension)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + downloadName(dataset, storedExtension) + "\"")
                    .body(new FileSystemResource(file.toFile()));
        }

        DatasetTable table = datasetService.load(dataset);
        String converted = "arff".equals(requestedFormat)
                ? DatasetFileWriter.toArff(table, DatasetFileWriter.sanitizedRelationName(
                        dataset.getProjectName(), dataset.getProjectVersion()))
                : DatasetFileWriter.toCsv(table);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadName(dataset, requestedFormat) + "\"")
                .body(new ByteArrayResource(converted.getBytes(StandardCharsets.UTF_8)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        datasetService.delete(currentUser.requireUserId(request), id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private static MetricDataset.Family parseFamily(String value) {
        try {
            return MetricDataset.Family.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Dataset family must be PROMISE or AEEEM.");
        }
    }

    private static MetricDataset.Type parseType(String value) {
        try {
            return MetricDataset.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Dataset type must be PREDEFINED or MANUAL.");
        }
    }

    private static String parseDownloadFormat(String requested, String fallback) {
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        String normalized = requested.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("csv") && !normalized.equals("arff")) {
            throw new IllegalArgumentException("The download format must be csv or arff.");
        }
        return normalized;
    }

    private static String downloadName(MetricDataset dataset, String extension) {
        return (dataset.getProjectName() + "-"
                + (dataset.getProjectVersion() == null ? "metrics" : dataset.getProjectVersion()))
                .replaceAll("[^A-Za-z0-9._-]", "_") + "." + extension;
    }
}
