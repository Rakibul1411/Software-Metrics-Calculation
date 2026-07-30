export interface AeeemAnalysisSummary {
  profileId: string;
  profileName: string;
  historyStart: string;
  releaseDate: string;
  releaseCommit: string;
  snapshotCount: number;
  branch: string;
  modulePath: string;
  referenceSnapshotCount: number;
  referenceRowCount: number;
  releaseResolution?: string;
  warnings?: string[];
}

export interface MetricsPreview {
  targetDatasetId: string;
  datasetFormat: 'promise' | 'aeeem';
  rowCount: number;
  extractedColumns: string[];
  csvPreview: string[];
  csvDownloadUrl: string;
  arffDownloadUrl: string;
  aeeemAnalysis?: AeeemAnalysisSummary | null;
}
