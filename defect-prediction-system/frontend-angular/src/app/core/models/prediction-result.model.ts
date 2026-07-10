export interface RiskMetric {
  metric: string;
  value: number;
  sourceMean: number;
  zScore: number;
  severity: 'High' | 'Moderate' | string;
}

export interface NearestBuggyClass {
  class: string;
  dataset: string;
  distance: number;
}

export interface SourceRankingItem {
  rank: number;
  dataset: string;
  distance: number;
  rows: number;
  buggyRows: number;
  cleanRows: number;
  selected: boolean;
}

export interface PredictionResultItem {
  class: string;
  prediction: string;
  label?: 'Buggy' | 'Clean';
  isBuggy?: boolean;
  riskScore?: number;
  riskPercent?: number;
  confidence?: 'High' | 'Medium' | 'Low' | string;
  topRiskyMetrics?: RiskMetric[];
  nearestBuggyClasses?: NearestBuggyClass[];
  actualLabel?: 'Buggy' | 'Clean';
  actualIsBuggy?: boolean;
  correct?: boolean;
}

export interface EvaluationMetrics {
  accuracy: number;
  precision: number;
  recall: number;
  f1: number;
  mcc?: number;
  rocAuc?: number | null;
  prAuc?: number | null;
}

export interface ConfusionMatrix {
  truePositive: number;
  trueNegative: number;
  falsePositive: number;
  falseNegative: number;
}

export interface EvaluationResult {
  status: string;
  method?: string;
  selectedSources?: SourceRankingItem[];
  sourceRanking?: SourceRankingItem[];
  metrics: EvaluationMetrics;
  confusionMatrix: ConfusionMatrix;
  predictions: PredictionResultItem[];
  message?: string;
}

export interface PredictionSummary {
  total: number;
  buggy: number;
  clean: number;
}

export interface PredictionResult {
  status: string;
  method?: string;
  selectedSources?: SourceRankingItem[];
  sourceRanking?: SourceRankingItem[];
  predictions: PredictionResultItem[];
  summary?: PredictionSummary;
  message?: string;
}
