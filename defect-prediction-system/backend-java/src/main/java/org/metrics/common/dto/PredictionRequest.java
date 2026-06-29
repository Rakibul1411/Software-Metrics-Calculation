package org.metrics.common.dto;

public class PredictionRequest {
    private String targetDatasetId;
    private String labelColumn;
    private int knnValue = 5;
    private boolean coralOption = true;

    public String getTargetDatasetId() { return targetDatasetId; }
    public void setTargetDatasetId(String id) { this.targetDatasetId = id; }

    public String getLabelColumn() { return labelColumn; }
    public void setLabelColumn(String col) { this.labelColumn = col; }

    public int getKnnValue() { return knnValue; }
    public void setKnnValue(int val) { this.knnValue = val; }

    public boolean isCoralOption() { return coralOption; }
    public void setCoralOption(boolean opt) { this.coralOption = opt; }
}
