export interface PredictionResultItem {
  class: string;
  prediction: string;
  label?: 'Buggy' | 'Clean';
  isBuggy?: boolean;
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
