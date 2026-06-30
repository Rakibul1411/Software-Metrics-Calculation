export interface PredictionResultItem {
  class: string;
  prediction: string;
  label?: 'Buggy' | 'Clean';
  isBuggy?: boolean;
  actualLabel?: 'Buggy' | 'Clean';
  actualIsBuggy?: boolean;
  correct?: boolean;
}

export interface EvaluationMetrics {
  accuracy: number;
  precision: number;
  recall: number;
  f1: number;
}

export interface ConfusionMatrix {
  truePositive: number;
  trueNegative: number;
  falsePositive: number;
  falseNegative: number;
}

export interface EvaluationResult {
  status: string;
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
  predictions: PredictionResultItem[];
  summary?: PredictionSummary;
  message?: string;
}
