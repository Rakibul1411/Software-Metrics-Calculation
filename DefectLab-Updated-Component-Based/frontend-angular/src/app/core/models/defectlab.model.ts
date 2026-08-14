export type DatasetFamily = 'PROMISE' | 'AEEEM';
export type DatasetType = 'MANUAL' | 'PREDEFINED';

export interface UserProfile {
  id: number;
  name: string;
  email: string;
  createdAt: string;
}

export interface DatasetSummary {
  id: number;
  projectName: string;
  projectVersion: string;
  displayName: string;
  datasetFamily: DatasetFamily;
  datasetType: DatasetType;
  hasActualLabel: boolean;
  totalFiles: number;
  totalMetrics: number;
  systemDataset: boolean;
  createdAt: string;
  features?: string[];
}

export interface DatasetPreview {
  headers: string[];
  rows: string[][];
  totalRows: number;
}

export interface MetricValue {
  value: number | null;
  reason: string | null;
}

export interface EvaluationMetrics {
  confusionMatrix: {
    truePositive: number;
    falsePositive: number;
    trueNegative: number;
    falseNegative: number;
  };
  accuracy: MetricValue;
  precision: MetricValue;
  recall: MetricValue;
  specificity: MetricValue;
  f1: MetricValue;
  balancedAccuracy: MetricValue;
  mcc: MetricValue;
  rocAuc: MetricValue;
  prAuc: MetricValue;
  recallAt20PercentLoc: MetricValue;
  aucec: MetricValue;
  labelledRows: number;
  buggyRows: number;
}

export interface ModelConfig {
  modelName: 'KNN';
  k: number;
  threshold: number;
  coral: boolean;
  seed: number;
  datasetFamily: DatasetFamily;
}

export interface PredictionSummary {
  totalRecords: number;
  predictedBuggy: number;
  predictedClean: number;
}

export interface PredictionRow {
  classIdentifier: string;
  defectScore: number;
  defectProbability: number;
  predictedLabel: number;
  actualLabel: number | null;
  riskRank: number;
  riskBand: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface PredictionRunSummary {
  id: number;
  comparisonGroupId: string | null;
  createdAt: string;
  sourceDataset: DatasetSummary;
  targetDataset: DatasetSummary;
  modelConfig: ModelConfig;
  status: 'COMPLETED';
  predictionFileAvailable: boolean;
  reportFileAvailable: boolean;
  summary: PredictionSummary;
  evaluation: EvaluationMetrics | null;
}

export interface PredictionRunDetail extends PredictionRunSummary {
  warnings: string[];
  predictions: PredictionRow[];
}

export interface PredictionRunGroup {
  comparisonGroupId: string | null;
  runs: PredictionRunSummary[];
}

export interface PredictionExecution {
  comparisonGroupId: string | null;
  runIds: number[];
  runs: PredictionRunDetail[];
}

export interface MetricComparisonSummary {
  id: number;
  createdAt: string;
  manualDatasetId: number;
  predefinedDatasetId: number;
  manualDataset: DatasetSummary;
  predefinedDataset: DatasetSummary;
  comparisonConfig: {
    hasIdentifierColumn: boolean;
    identifierColumnName: string | null;
    comparisonMode: 'AGGREGATE' | 'INSTANCE_WISE';
    absoluteTolerance: number;
    relativeTolerance: number;
  };
  reportFileAvailable: boolean;
}

export interface AggregateMetricStats {
  recordCount: number;
  mean: number | null;
  standardDeviation: number | null;
  median: number | null;
  minimum: number | null;
  maximum: number | null;
}

export interface AggregateMetricComparisonRow {
  metric: string;
  manual: AggregateMetricStats;
  predefined: AggregateMetricStats;
  meanDifference: number | null;
  sdDifference: number | null;
  percentageDifference: number | null;
}

export interface AggregateMetricComparisonResult {
  comparisonMode: 'AGGREGATE';
  commonNumericMetricCount: number;
  metrics: AggregateMetricComparisonRow[];
}

export interface InstanceMetricComparisonRow {
  identifier: string;
  metric: string;
  manualValue: number | null;
  predefinedValue: number | null;
  status: 'EXACT_MATCH' | 'CLOSE_MATCH' | 'MISMATCH' | 'NOT_COMPARABLE';
}

export interface InstanceMetricComparisonResult {
  comparisonMode: 'INSTANCE_WISE';
  commonNumericMetricCount: number;
  statusCounts: Record<string, number>;
  matchedIdentifiers: number;
  manualOnly: string[];
  predefinedOnly: string[];
  comparisons: InstanceMetricComparisonRow[];
  metrics: AggregateMetricComparisonRow[];
}

export type MetricComparisonResult =
  AggregateMetricComparisonResult | InstanceMetricComparisonResult;

export interface MetricComparisonDetail extends MetricComparisonSummary {
  result: MetricComparisonResult;
  cacheHit: boolean;
}

export interface MetricComparisonPair {
  key: string;
  displayName: string;
  datasetFamily: DatasetFamily;
  projectName: string;
  projectVersion: string;
  manualDatasetId: number;
  predefinedDatasetId: number;
  comparisonId: number | null;
  cached: boolean;
}

export interface DashboardData {
  totalDatasets: number;
  manualDatasets: number;
  predefinedDatasets: number;
  labeledDatasets: number;
  comparisonRuns: number;
  recentDatasets: DatasetSummary[];
  recentRuns: PredictionRunSummary[];
}
