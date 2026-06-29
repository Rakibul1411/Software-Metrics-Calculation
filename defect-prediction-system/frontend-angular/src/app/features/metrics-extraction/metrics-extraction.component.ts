import { Component } from '@angular/core';
import { MetricsApiService } from '../../core/services/metrics-api.service';
import { MetricsPreview } from '../../core/models/metrics-preview.model';

@Component({
  selector: 'app-metrics-extraction',
  templateUrl: './metrics-extraction.component.html',
  styleUrls: ['./metrics-extraction.component.css']
})
export class MetricsExtractionComponent {
  sourceDirectory = '';
  datasetFormat = 'promise';
  result: MetricsPreview | null = null;
  loading = false;
  error: string | null = null;

  constructor(private metricsApiService: MetricsApiService) {}

  extract(): void {
    this.loading = true;
    this.error = null;
    this.metricsApiService.extractMetrics(this.sourceDirectory, this.datasetFormat)
      .subscribe({
        next: (data) => {
          this.result = data;
          this.loading = false;
        },
        error: (err) => {
          this.error = err.message || 'Extraction failed';
          this.loading = false;
        }
      });
  }

  getDownloadUrl(): string {
    if (!this.result) return '';
    return this.metricsApiService.downloadDataset(this.result.targetDatasetId);
  }
}
