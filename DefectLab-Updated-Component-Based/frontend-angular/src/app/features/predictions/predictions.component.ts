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
  template: `
    <section class="dl-grid dl-grid-2 dl-prediction-layout">
      <article class="dl-card dl-prediction-form-card">
        <div class="dl-card-head"><div>
          <span class="dl-card-kicker">New run</span>
          <h2>Predict one or two targets</h2>
          <p>Select a labeled source and at least one target. A dual request saves two grouped rows.</p>
        </div></div>

        <form class="dl-form-stack dl-prediction-form" (ngSubmit)="run()">
          <label class="dl-field">
            <span>Dataset metric type</span>
            <select [(ngModel)]="datasetFamily" name="datasetFamily"
                    (ngModelChange)="datasetFamilyChanged()">
              <option value="PROMISE">PROMISE · 20 metrics</option>
              <option value="AEEEM">AEEEM · 56 metrics</option>
            </select>
          </label>

          <label class="dl-field">
            <span>Labeled source dataset *</span>
            <select [(ngModel)]="sourceId" name="sourceId"
                    (ngModelChange)="sourceChanged()">
              <option [ngValue]="null">Choose source</option>
              <option *ngFor="let item of sourceOptions" [ngValue]="item.id">
                {{ item.displayName }} · {{ item.datasetFamily }} · {{ item.datasetType }}
              </option>
            </select>
          </label>

          <label class="dl-field">
            <span>MANUAL target (optional)</span>
            <select [(ngModel)]="manualId" name="manualId"
                    (ngModelChange)="manualChanged()">
              <option [ngValue]="null">No manual target</option>
              <option *ngFor="let item of manualOptions" [ngValue]="item.id">
                {{ item.displayName }} · {{ item.datasetFamily }} · MANUAL
              </option>
            </select>
          </label>

          <label class="dl-field">
            <span>PREDEFINED target (optional)</span>
            <select [(ngModel)]="predefinedId" name="predefinedId">
              <option [ngValue]="null">No predefined target</option>
              <option *ngFor="let item of predefinedOptions" [ngValue]="item.id">
                {{ item.displayName }} · {{ item.datasetFamily }} · PREDEFINED
              </option>
            </select>
          </label>

          <div class="dl-form-row">
            <label class="dl-field"><span>Model</span>
              <select [(ngModel)]="modelName" name="modelName">
                <option value="KNN">K-Nearest Neighbors</option>
                <option value="SVM">Support Vector Machine</option>
              </select>
            </label>
            <label class="dl-field" *ngIf="modelName === 'KNN'"><span>K</span>
              <select [(ngModel)]="k" name="k">
                <option *ngFor="let value of [1,2,3,4,5]" [ngValue]="value">{{ value }}</option>
              </select>
            </label>
            <label class="dl-field" *ngIf="modelName === 'SVM'"><span>C</span>
              <input type="number" min="0.1" max="1000" step="0.1" [(ngModel)]="c" name="c">
            </label>
          </div>

          <div class="dl-form-row">
            <label class="dl-field" *ngIf="modelName === 'SVM'"><span>Kernel</span>
              <select [(ngModel)]="kernel" name="kernel">
                <option value="RBF">RBF</option><option value="LINEAR">Linear</option>
                <option value="POLY">Polynomial</option><option value="SIGMOID">Sigmoid</option>
              </select>
            </label>
            <label class="dl-field"><span>Threshold</span>
              <input type="number" min="0.05" max="0.95" step="0.05"
                     [(ngModel)]="threshold" name="threshold">
            </label>
          </div>

          <div *ngIf="error" class="dl-alert dl-alert-error">{{ error }}</div>
          <button class="dl-btn" type="submit" [disabled]="busy || !canRun()">
            <span *ngIf="busy" class="dl-spinner"></span>
            {{ busy ? 'Training and generating reports…' : 'Run prediction' }}
          </button>
        </form>
      </article>

      <article class="dl-card">
        <div class="dl-card-head"><div>
          <span class="dl-card-kicker">Artifacts</span>
          <h2>Output contract</h2>
          <p>Every target gets a PDF. Only MANUAL targets get a labeled metrics CSV.</p>
        </div></div>
        <div class="dl-step-overview">
          <div class="dl-step"><span class="dl-step-order">M</span><div>
            <strong>MANUAL</strong><p>Original metric columns plus predicted_label.</p>
          </div></div>
          <div class="dl-step"><span class="dl-step-order">P</span><div>
            <strong>PREDEFINED</strong><p>PDF with accuracy, precision, recall, F1, ROC-AUC and confusion matrix.</p>
          </div></div>
        </div>
        <div *ngIf="created" class="dl-alert dl-alert-success">
          Completed run IDs {{ created.runIds.join(', ') }}
          <span *ngIf="created.comparisonGroupId"> · group {{ created.comparisonGroupId }}</span>
        </div>
      </article>
    </section>

    <section class="dl-card">
      <div class="dl-card-head"><div>
        <h2>Saved prediction runs</h2>
        <p>Dual-target results share one comparison group ID.</p>
      </div><button class="dl-btn dl-btn-ghost dl-btn-sm" type="button" (click)="load()">Refresh</button></div>
      <div class="dl-table-wrap" *ngIf="runs.length; else emptyRuns">
        <table class="dl-table">
          <thead><tr><th>Run</th><th>Group</th><th>Target</th><th>Type</th>
            <th>Model</th><th>Defective</th><th>Created</th><th></th></tr></thead>
          <tbody><tr *ngFor="let item of runs">
            <td class="dl-mono">#{{ item.id }}</td>
            <td class="dl-mono">{{ item.comparisonGroupId || '—' }}</td>
            <td>{{ item.targetDataset.displayName }}</td>
            <td>{{ item.targetDataset.datasetType }}</td>
            <td>{{ item.modelConfig.modelName }} · {{ setting(item) }}</td>
            <td class="dl-num">{{ item.summary.predictedDefective }}</td>
            <td>{{ item.createdAt | date:'medium' }}</td>
            <td><button class="dl-btn dl-btn-ghost dl-btn-sm" type="button"
                        (click)="open(item)">Inspect</button></td>
          </tr></tbody>
        </table>
      </div>
      <ng-template #emptyRuns><div class="dl-empty">No prediction runs yet.</div></ng-template>
    </section>

    <section class="dl-card" *ngIf="selectedRun as run">
      <div class="dl-card-head"><div>
        <h2>Run #{{ run.id }} · {{ run.targetDataset.displayName }}</h2>
        <p>Ranked by defect probability, highest first.</p>
      </div><div class="dl-actions">
        <a *ngIf="run.predictionFileAvailable" class="dl-btn dl-btn-ghost dl-btn-sm"
           [href]="api.predictionDownloadUrl(run.id)">Download labeled CSV</a>
        <a class="dl-btn dl-btn-sm" [href]="api.reportDownloadUrl(run.id)">Download PDF</a>
      </div></div>

      <div *ngIf="run.evaluation as metrics" class="dl-grid dl-grid-4">
        <article class="dl-data-card"><span>Accuracy</span><strong>{{ metric(metrics.accuracy) }}</strong></article>
        <article class="dl-data-card"><span>Precision</span><strong>{{ metric(metrics.precision) }}</strong></article>
        <article class="dl-data-card"><span>Recall</span><strong>{{ metric(metrics.recall) }}</strong></article>
        <article class="dl-data-card"><span>F1-score</span><strong>{{ metric(metrics.f1) }}</strong></article>
        <article class="dl-data-card"><span>ROC-AUC</span><strong>{{ metric(metrics.rocAuc) }}</strong></article>
      </div>

      <div *ngIf="predictionLoading" class="dl-loading"><span class="dl-spinner"></span>Loading…</div>
      <div class="dl-table-wrap dl-scroll-y" *ngIf="predictions.length">
        <table class="dl-table">
          <thead><tr><th>Rank</th><th>File / identifier</th><th>Probability</th>
            <th>Predicted</th><th *ngIf="run.targetDataset.datasetType === 'PREDEFINED'">Actual</th></tr></thead>
          <tbody><tr *ngFor="let row of predictions">
            <td class="dl-num">{{ row.riskRank }}</td>
            <td class="dl-mono">{{ row.classIdentifier }}</td>
            <td class="dl-num">{{ row.defectProbability | number:'1.4-4' }}</td>
            <td>{{ row.predictedLabel === 1 ? 'Defective' : 'Non-defective' }}</td>
            <td *ngIf="run.targetDataset.datasetType === 'PREDEFINED'">
              {{ row.actualLabel === 1 ? 'Defective' : 'Non-defective' }}
            </td>
          </tr></tbody>
        </table>
      </div>
    </section>
  `
})
export class PredictionsComponent implements OnInit {
  datasets: DatasetSummary[] = [];
  runs: PredictionRunSummary[] = [];
  sourceId: number | null = null;
  datasetFamily: DatasetFamily = 'PROMISE';
  manualId: number | null = null;
  predefinedId: number | null = null;
  modelName: 'KNN' | 'SVM' = 'KNN';
  k = 3;
  c = 1;
  kernel = 'RBF';
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
    const manual = this.datasets.find(item => item.id === this.manualId);
    const predefined = this.datasets.find(item => item.id === this.predefinedId);
    return this.uniqueByIdentity(this.datasets.filter(item =>
      item.hasActualLabel &&
      item.datasetFamily === this.datasetFamily &&
      item.id !== this.manualId &&
      item.id !== this.predefinedId &&
      (!manual || !this.sameIdentity(item, manual)) &&
      (!predefined || !this.sameIdentity(item, predefined))));
  }

  get selectedSource(): DatasetSummary | undefined {
    return this.datasets.find(item => item.id === this.sourceId);
  }

  get manualOptions(): DatasetSummary[] {
    const source = this.selectedSource;
    return this.uniqueByIdentity(this.datasets.filter(item =>
      item.datasetType === 'MANUAL' &&
      item.datasetFamily === this.datasetFamily &&
      item.id !== this.sourceId &&
      (!source || !this.sameIdentity(item, source)) &&
      (!source || item.datasetFamily === source.datasetFamily)));
  }

  get predefinedOptions(): DatasetSummary[] {
    const source = this.selectedSource;
    return this.uniqueByIdentity(this.datasets.filter(item =>
      item.datasetType === 'PREDEFINED' && item.hasActualLabel &&
      item.datasetFamily === this.datasetFamily &&
      item.id !== this.sourceId &&
      (!source || !this.sameIdentity(item, source)) &&
      (!source || item.datasetFamily === source.datasetFamily)));
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
    if (this.predefinedId === source.id) this.predefinedId = null;

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

  private uniqueByIdentity(items: DatasetSummary[]): DatasetSummary[] {
    const seen = new Set<string>();
    return items.filter(item => {
      const key = this.identityKey(item);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  private sameIdentity(left: DatasetSummary, right: DatasetSummary): boolean {
    return this.identityKey(left) === this.identityKey(right);
  }

  private identityKey(item: DatasetSummary): string {
    return [
      item.datasetFamily,
      item.datasetType,
      this.normalizeIdentityPart(item.projectName),
      this.normalizeIdentityPart(item.projectVersion)
    ].join('|');
  }

  private normalizeIdentityPart(value: string | null | undefined): string {
    return (value || '').trim().toLowerCase().replace(/\s+/g, ' ');
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
      threshold: this.threshold,
      seed: 42
    };
    if (this.modelName === 'KNN') payload['k'] = this.k;
    else {
      payload['c'] = this.c;
      payload['kernel'] = this.kernel;
    }
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
    return run.modelConfig.modelName === 'KNN'
      ? `K=${run.modelConfig.k}` : `C=${run.modelConfig.c}, ${run.modelConfig.kernel}`;
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
