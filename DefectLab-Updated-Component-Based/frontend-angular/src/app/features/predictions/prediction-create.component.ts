import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-prediction-create',
  standalone: false,
  templateUrl: './prediction-create.component.html'
})
export class PredictionCreateComponent implements OnInit {
  datasets: DatasetSummary[] = [];
  sourceId: number | null = null;
  datasetFamily: DatasetFamily = 'PROMISE';

  readonly familyOptions: RadioOption[] = [
    { value: 'PROMISE', label: 'PROMISE · 20 metrics' },
    { value: 'AEEEM', label: 'AEEEM · 56 metrics' }
  ];
  manualId: number | null = null;
  predefinedId: number | null = null;
  readonly modelName = 'KNN';
  readonly kOptions: SelectOption[] = [1, 2, 3, 4, 5].map(value => ({ value, label: String(value) }));
  k = 3;
  coral = true;
  threshold = 0.5;
  busy = false;
  error = '';

  constructor(
    private readonly api: DefectLabApiService,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

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

  get sourceSelectOptions(): SelectOption[] {
    return this.sourceOptions.map(item => ({
      value: item.id,
      label: `${item.displayName} · ${item.datasetFamily} · ${item.datasetType} · LABELED · ${this.datasetScope(item)}`
    }));
  }

  get manualSelectOptions(): SelectOption[] {
    return [
      { value: null, label: 'No manual target' },
      ...this.manualOptions.map(item => ({
        value: item.id,
        label: `${item.displayName} · ${item.datasetFamily} · MANUAL · ${this.datasetScope(item)}`
      }))
    ];
  }

  get predefinedSelectOptions(): SelectOption[] {
    return [
      { value: null, label: 'No predefined target' },
      ...this.predefinedOptions.map(item => ({
        value: item.id,
        label: `${item.displayName} · ${item.datasetFamily} · PREDEFINED · ${this.datasetScope(item)}`
      }))
    ];
  }

  load(): void {
    this.api.listDatasets().subscribe({
      next: rows => this.datasets = rows,
      error: error => this.error = error?.error?.error ?? 'Could not load datasets.'
    });
  }

  datasetFamilyChanged(): void {
    this.sourceId = null;
    this.manualId = null;
    this.predefinedId = null;
  }

  onFamilyChange(value: string): void {
    this.datasetFamily = value as DatasetFamily;
    this.datasetFamilyChanged();
  }

  onThresholdChange(value: string): void {
    this.threshold = Number(value);
  }

  onSourceChange(value: string | number | null): void {
    this.sourceId = value as number | null;
    this.sourceChanged();
  }

  onManualChange(value: string | number | null): void {
    this.manualId = value as number | null;
    this.manualChanged();
  }

  onPredefinedChange(value: string | number | null): void {
    this.predefinedId = value as number | null;
  }

  onKChange(value: string | number | null): void {
    this.k = value as number;
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
    if (!this.canRun() || this.busy) return;
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
      next: () => {
        this.busy = false;
        this.toast.success('Prediction run completed successfully.');
        this.router.navigate(['/predictions']);
      },
      error: error => {
        const message = error?.error?.error ?? 'Prediction run failed.';
        this.error = message;
        this.busy = false;
        this.toast.error(message);
      }
    });
  }

  back(): void {
    this.router.navigate(['/predictions']);
  }
}
