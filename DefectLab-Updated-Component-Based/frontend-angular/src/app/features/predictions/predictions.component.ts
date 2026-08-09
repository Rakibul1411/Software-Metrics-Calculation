import { Component, OnInit } from '@angular/core';
import {
  DatasetFamily,
  DatasetSummary,
  PredictionExecution,
  PredictionRow,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-predictions',
  standalone: false,
  templateUrl: './predictions.component.html'
})
export class PredictionsComponent implements OnInit {
  datasets: DatasetSummary[] = [];
  runs: PredictionRunSummary[] = [];
  sourceId: number | null = null;
  datasetFamily: DatasetFamily = 'PROMISE';
  manualId: number | null = null;
  predefinedId: number | null = null;
  readonly modelName = 'KNN';
  k = 3;
  coral = true;
  threshold = 0.5;
  busy = false;
  error = '';
  created: PredictionExecution | null = null;
  selectedRun: PredictionRunSummary | null = null;
  predictions: PredictionRow[] = [];
  predictionLoading = false;

  constructor(readonly api: DefectLabApiService) {}

  ngOnInit(): void { this.load(); }

  get sourceOptions(): DatasetSummary[] {
    return this.datasets.filter(item =>
      item.hasActualLabel === true &&
      item.datasetFamily === this.datasetFamily &&
      item.id !== this.manualId);
  }

  get selectedSource(): DatasetSummary | undefined {
    return this.datasets.find(item => item.id === this.sourceId);
  }

  get manualOptions(): DatasetSummary[] {
    const source = this.selectedSource;
    return this.datasets.filter(item =>
      item.datasetType === 'MANUAL' &&
      item.datasetFamily === this.datasetFamily &&
      item.id !== this.sourceId &&
      (!source || item.datasetFamily === source.datasetFamily));
  }

  get predefinedOptions(): DatasetSummary[] {
    const source = this.selectedSource;
    return this.datasets.filter(item =>
      item.datasetType === 'PREDEFINED' && item.hasActualLabel &&
      item.datasetFamily === this.datasetFamily &&
      (!source || item.datasetFamily === source.datasetFamily));
  }

  load(): void {
    this.api.listDatasets().subscribe({
      next: rows => this.datasets = rows,
      error: error => this.error = error?.error?.error ?? 'Could not load datasets.'
    });
    this.api.listPredictionRuns().subscribe({
      next: rows => this.runs = rows,
      error: error => this.error = error?.error?.error ?? 'Could not load prediction runs.'
    });
  }

  datasetFamilyChanged(): void {
    this.sourceId = null;
    this.manualId = null;
    this.predefinedId = null;
    this.created = null;
  }

  sourceChanged(): void {
    const source = this.selectedSource;
    if (!source) return;
    if (this.manualId === source.id) this.manualId = null;

    const manual = this.datasets.find(item => item.id === this.manualId);
    if (manual && manual.datasetFamily !== source.datasetFamily) {
      this.manualId = null;
    }
    const predefined = this.datasets.find(item => item.id === this.predefinedId);
    if (predefined && predefined.datasetFamily !== source.datasetFamily) {
      this.predefinedId = null;
    }
    this.clearUnavailablePredefined();
  }

  manualChanged(): void {
    if (this.manualId === this.sourceId) this.manualId = null;
    this.clearUnavailablePredefined();
  }

  private clearUnavailablePredefined(): void {
    if (this.predefinedId !== null &&
        !this.predefinedOptions.some(item => item.id === this.predefinedId)) {
      this.predefinedId = null;
    }
  }

  datasetScope(item: DatasetSummary): string {
    return item.systemDataset ? 'BUNDLED' : 'YOUR DATA';
  }

  canRun(): boolean {
    return !!this.sourceId && (!!this.manualId || !!this.predefinedId);
  }

  run(): void {
    if (!this.canRun()) return;
    this.busy = true;
    this.error = '';
    const payload: Record<string, unknown> = {
      sourceDatasetId: this.sourceId,
      manualTargetDatasetId: this.manualId,
      predefinedTargetDatasetId: this.predefinedId,
      modelName: this.modelName,
      k: this.k,
      coral: this.coral,
      threshold: this.threshold,
      seed: 42
    };
    this.api.runPrediction(payload).subscribe({
      next: result => {
        this.created = result;
        this.busy = false;
        this.load();
        if (result.runs.length) this.open(result.runs[0]);
      },
      error: error => {
        this.error = error?.error?.error ?? 'Prediction run failed.';
        this.busy = false;
      }
    });
  }

  setting(run: PredictionRunSummary): string {
    return `K=${run.modelConfig.k}`;
  }

  metric(value: { value: number | null }): string {
    return value?.value === null || value?.value === undefined
      ? 'N/A' : value.value.toFixed(3);
  }

  open(run: PredictionRunSummary): void {
    this.selectedRun = run;
    this.predictionLoading = true;
    this.api.predictions(run.id, false, 500).subscribe({
      next: rows => {
        this.predictions = rows;
        this.predictionLoading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load predictions.';
        this.predictionLoading = false;
      }
    });
  }
}
