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
  threshold?: number;
  accuracy: number;
  balancedAccuracy?: number;
  precision: number;
  recall: number;
  specificity?: number;
  f1: number;
  f2?: number;
  mcc?: number;
  gMean?: number;
  rocAuc?: number | null;
  prAuc?: number | null;
  averagePrecision?: number | null;
  positivePrevalence?: number;
  prAucNoSkillBaseline?: number;
}

export type ClassifierType = 'knn' | 'svm';

export interface PredictionRequestOptions {
  classifierType: ClassifierType;
  coralOption: boolean;
  topK: number;
  decisionThreshold?: number;
  thresholdBeta?: number;
}

export interface ModelConfiguration {
  classifierType?: ClassifierType;
  classifier?: string;
  selectedK?: number | null;
  autoTuneK?: boolean;
  selectedSvmC?: number | null;
  selectedC?: number | null;
  autoTuneSvmC?: boolean;
  classWeight?: 'balanced' | string | null;
  decisionThreshold?: number;
  thresholdSelection?: string;
  thresholdBeta?: number;
  coralEnabled?: boolean;
  coralType?: string;
  targetLabelUsage?: string;
  riskScoreMeaning?: string;
  sourceRankingMethod?: string;
  sourceDominanceWarning?: boolean;
  largestSourceRowShare?: number;
  modelSelectionStrategy?: string;
  hyperparameterSelection?: string;
  imbalanceHandling?: string;
}

export interface MetricAggregate {
  mean?: number | null;
  std?: number | null;
  validFoldCount: number;
}

export interface ModelSelection {
  strategy: string;
  sourceProjectCount: number;
  foldCount?: number;
  selectedClassifier?: ClassifierType;
  selectedK?: number | null;
  selectedC?: number | null;
  decisionThreshold: number;
  hyperparameterSelectionMetric?: string | null;
  thresholdSelectionMetric?: string | null;
  candidateValues?: Array<number>;
  candidateResults?: Array<Record<string, unknown>>;
  thresholdCandidateResults?: Array<Record<string, unknown>>;
  aggregateMetrics?: Record<string, MetricAggregate>;
  foldResults?: Array<Record<string, unknown>>;
  warnings?: string[];
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
  modelConfiguration?: ModelConfiguration;
  modelSelection?: ModelSelection;
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
  modelConfiguration?: ModelConfiguration;
  modelSelection?: ModelSelection;
  selectedSources?: SourceRankingItem[];
  sourceRanking?: SourceRankingItem[];
  predictions: PredictionResultItem[];
  summary?: PredictionSummary;
  message?: string;
}
