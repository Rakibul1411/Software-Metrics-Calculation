package org.metrics.common.dto;

import java.util.List;
import java.util.Map;

public class PredictionResponse {
    private String status;
    private List<Map<String, Object>> predictions;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Map<String, Object>> getPredictions() { return predictions; }
    public void setPredictions(List<Map<String, Object>> predictions) { this.predictions = predictions; }
}
