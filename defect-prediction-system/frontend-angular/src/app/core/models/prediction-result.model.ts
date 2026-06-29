export interface PredictionResultItem {
  class: string;
  prediction: string;
}

export interface PredictionResult {
  status: string;
  predictions: PredictionResultItem[];
}
