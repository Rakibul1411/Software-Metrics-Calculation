package org.metrics.common.dto;

import java.util.List;

public class MetricsExtractionResponse {
    private String targetDatasetId;
    private List<String> extractedColumns;
    private List<String> csvPreview;
    private String downloadUrl;

    public MetricsExtractionResponse() {}

    public MetricsExtractionResponse(String targetDatasetId, List<String> extractedColumns, List<String> csvPreview, String downloadUrl) {
        this.targetDatasetId = targetDatasetId;
        this.extractedColumns = extractedColumns;
        this.csvPreview = csvPreview;
        this.downloadUrl = downloadUrl;
    }

    public String getTargetDatasetId() { return targetDatasetId; }
    public void setTargetDatasetId(String id) { this.targetDatasetId = id; }

    public List<String> getExtractedColumns() { return extractedColumns; }
    public void setExtractedColumns(List<String> columns) { this.extractedColumns = columns; }

    public List<String> getCsvPreview() { return csvPreview; }
    public void setCsvPreview(List<String> preview) { this.csvPreview = preview; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String url) { this.downloadUrl = url; }
}
