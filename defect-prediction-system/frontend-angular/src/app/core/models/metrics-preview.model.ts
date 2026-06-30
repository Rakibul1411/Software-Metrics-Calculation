export interface MetricsPreview {
  targetDatasetId: string;
  datasetFormat: 'promise' | 'aeeem';
  rowCount: number;
  extractedColumns: string[];
  csvPreview: string[];
  downloadUrl: string;
}
