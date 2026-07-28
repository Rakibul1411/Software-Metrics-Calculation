import { Component } from '@angular/core';
import { PredictionApiService } from '../../core/services/prediction-api.service';
import { PredictionResult } from '../../core/models/prediction-result.model';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent {
  targetDatasetId = '';
  sourceFiles: File[] = [];
  labelColumn = 'bug';
  coralOption = true;
  result: PredictionResult | null = null;
  loading = false;
  error: string | null = null;

  constructor(private predictionApiService: PredictionApiService) {}

  onSourceFilesChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.sourceFiles = Array.from(input.files);
    }
  }

  runPrediction(): void {
    this.loading = true;
    this.error = null;
    this.predictionApiService.runPrediction(
      this.targetDatasetId,
      this.sourceFiles,
      this.labelColumn,
      {
        classifierType: 'knn',
        coralOption: this.coralOption,
        topK: 3,
        thresholdBeta: 2
      }
    ).subscribe({
      next: (data) => {
        this.result = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.message || 'Prediction failed';
        this.loading = false;
      }
    });
  }
}
