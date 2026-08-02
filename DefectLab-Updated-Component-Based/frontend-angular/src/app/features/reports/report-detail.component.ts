import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  MetricValue,
  PredictionRow,
  PredictionRunGroup,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

interface MatchedPredictionRow {
  identifier: string;
  manualPrediction: number;
  predefinedPrediction: number;
  actualLabel: number | null;
  manualCorrect: boolean | null;
  predefinedCorrect: boolean | null;
  modelsAgree: boolean;
}

@Component({
  selector: 'app-report-detail',
  standalone: false,
  template: `
    <section class="dl-card">
      <div class="dl-card-head"><div>
        <span class="dl-card-kicker">Grouped prediction report</span>
        <h2>{{ groupTitle }}</h2>
        <p *ngIf="group">
          Source: {{ group.runs[0].sourceDataset.displayName }} ·
          Model: {{ group.runs[0].modelConfig.modelName }} ·
          Group: {{ group.comparisonGroupId || 'Single target run' }}
        </p>
      </div>
        <button class="dl-btn dl-btn-ghost" type="button" (click)="back()">
          Back to reports
        </button>
      </div>
      <div *ngIf="loading" class="dl-loading">
        <span class="dl-spinner"></span>Loading complete prediction details…
      </div>
      <div *ngIf="error" class="dl-alert dl-alert-error">{{ error }}</div>
    </section>

    <section class="dl-card" *ngIf="group && !loading">
      <div class="dl-card-head"><div>
        <span class="dl-card-kicker">Common run configuration</span>
        <h2>Shared source and model settings</h2>
        <p>These settings were used for both MANUAL and PREDEFINED predictions.</p>
      </div></div>
      <div class="dl-grid dl-grid-4">
        <article class="dl-data-card"><span>Source dataset</span>
          <strong>{{ group.runs[0].sourceDataset.displayName }}</strong></article>
        <article class="dl-data-card"><span>Dataset family</span>
          <strong>{{ group.runs[0].modelConfig.datasetFamily }}</strong></article>
        <article class="dl-data-card"><span>Model</span>
          <strong>{{ modelSetting(group.runs[0]) }}</strong></article>
        <article class="dl-data-card"><span>Threshold</span>
          <strong>{{ group.runs[0].modelConfig.threshold }}</strong></article>
        <article class="dl-data-card"><span>Log transform</span>
          <strong>{{ enabled(group.runs[0].modelConfig.logTransform) }}</strong></article>
        <article class="dl-data-card"><span>CORAL alignment</span>
          <strong>{{ enabled(group.runs[0].modelConfig.coral) }}</strong></article>
        <article class="dl-data-card"><span>Random seed</span>
          <strong>{{ group.runs[0].modelConfig.seed }}</strong></article>
      </div>
    </section>

    <div class="dl-grid dl-grid-2 dl-report-summary-grid" *ngIf="group && !loading">
      <section class="dl-card dl-report-target-card">
        <div class="dl-card-head"><div>
          <span class="dl-card-kicker">MANUAL target</span>
          <h2>{{ manualRun?.targetDataset?.displayName || 'Not included' }}</h2>
          <p *ngIf="manualRun">Prediction run #{{ manualRun.id }}</p>
        </div>
          <div class="dl-actions" *ngIf="manualRun as run">
            <a class="dl-btn dl-btn-ghost"
               [href]="api.predictionDownloadUrl(run.id)">Download labeled CSV</a>
            <a class="dl-btn"
               [href]="api.reportDownloadUrl(run.id)">Download report</a>
          </div>
        </div>

        <ng-container *ngIf="manualRun as run; else noManual">
          <div class="dl-grid dl-grid-3">
            <article class="dl-data-card"><span>Total files</span>
              <strong>{{ run.summary.totalRecords }}</strong></article>
            <article class="dl-data-card"><span>Predicted buggy files</span>
              <strong>{{ run.summary.predictedBuggy }}</strong></article>
            <article class="dl-data-card"><span>Predicted clean files</span>
              <strong>{{ run.summary.predictedClean }}</strong></article>
          </div>
        </ng-container>
        <ng-template #noManual>
          <div class="dl-empty">This report group has no MANUAL target.</div>
        </ng-template>
      </section>

      <section class="dl-card dl-report-target-card">
        <div class="dl-card-head"><div>
          <span class="dl-card-kicker">PREDEFINED target</span>
          <h2>{{ predefinedRun?.targetDataset?.displayName || 'Not included' }}</h2>
          <p *ngIf="predefinedRun">Prediction run #{{ predefinedRun.id }}</p>
        </div>
          <div class="dl-actions" *ngIf="predefinedRun as run">
            <a class="dl-btn"
               [href]="api.reportDownloadUrl(run.id)">Download report</a>
          </div>
        </div>

        <ng-container *ngIf="predefinedRun as run; else noPredefined">
          <div class="dl-grid dl-grid-4" *ngIf="run.evaluation as evaluation">
            <article class="dl-data-card"><span>True positive (TP)</span>
              <strong>{{ evaluation.confusionMatrix.truePositive }}</strong></article>
            <article class="dl-data-card"><span>True negative (TN)</span>
              <strong>{{ evaluation.confusionMatrix.trueNegative }}</strong></article>
            <article class="dl-data-card"><span>False positive (FP)</span>
              <strong>{{ evaluation.confusionMatrix.falsePositive }}</strong></article>
            <article class="dl-data-card"><span>False negative (FN)</span>
              <strong>{{ evaluation.confusionMatrix.falseNegative }}</strong></article>
          </div>
          <div class="dl-grid dl-grid-4 dl-report-score-grid"
               *ngIf="run.evaluation as evaluation">
            <article class="dl-data-card"><span>Accuracy</span>
              <strong>{{ metric(evaluation.accuracy) }}</strong></article>
            <article class="dl-data-card"><span>Precision</span>
              <strong>{{ metric(evaluation.precision) }}</strong></article>
            <article class="dl-data-card"><span>Recall</span>
              <strong>{{ metric(evaluation.recall) }}</strong></article>
            <article class="dl-data-card"><span>F1-score</span>
              <strong>{{ metric(evaluation.f1) }}</strong></article>
            <article class="dl-data-card"><span>ROC-AUC</span>
              <strong>{{ metric(evaluation.rocAuc) }}</strong></article>
          </div>
        </ng-container>
        <ng-template #noPredefined>
          <div class="dl-empty">This report group has no PREDEFINED target.</div>
        </ng-template>
      </section>
    </div>

    <div class="dl-grid dl-grid-2 dl-report-results-grid" *ngIf="group && !loading">
      <section class="dl-card">
        <div class="dl-card-head"><div>
          <span class="dl-card-kicker">MANUAL predictions</span>
          <h2>File-wise model output</h2>
          <p>{{ manualRows.length }} predicted files</p>
        </div></div>
        <div class="dl-table-wrap dl-report-predictions-scroll"
             *ngIf="manualRun; else noManualRows">
          <table class="dl-table">
            <thead><tr><th>Rank</th><th>File / identifier</th>
              <th>Probability</th><th>Model prediction</th></tr></thead>
            <tbody><tr *ngFor="let row of manualRows">
              <td>{{ row.riskRank }}</td>
              <td class="dl-mono">{{ row.classIdentifier }}</td>
              <td>{{ row.defectProbability | number:'1.4-4' }}</td>
              <td>{{ label(row.predictedLabel) }}</td>
            </tr></tbody>
          </table>
        </div>
        <ng-template #noManualRows>
          <div class="dl-empty">No MANUAL prediction rows.</div>
        </ng-template>
      </section>

      <section class="dl-card">
        <div class="dl-card-head"><div>
          <span class="dl-card-kicker">PREDEFINED predictions</span>
          <h2>File-wise model output and actual label</h2>
          <p>{{ predefinedRows.length }} evaluated files</p>
        </div></div>
        <div class="dl-table-wrap dl-report-predictions-scroll"
             *ngIf="predefinedRun; else noPredefinedRows">
          <table class="dl-table">
            <thead><tr><th>Rank</th><th>File / identifier</th>
              <th>Probability</th><th>Model prediction</th><th>Actual</th></tr></thead>
            <tbody><tr *ngFor="let row of predefinedRows">
              <td>{{ row.riskRank }}</td>
              <td class="dl-mono">{{ row.classIdentifier }}</td>
              <td>{{ row.defectProbability | number:'1.4-4' }}</td>
              <td>{{ label(row.predictedLabel) }}</td>
              <td>{{ row.actualLabel === null ? '—' : label(row.actualLabel) }}</td>
            </tr></tbody>
          </table>
        </div>
        <ng-template #noPredefinedRows>
          <div class="dl-empty">No PREDEFINED prediction rows.</div>
        </ng-template>
      </section>
    </div>

    <section class="dl-card" *ngIf="manualRun && predefinedRun && !loading">
      <div class="dl-card-head"><div>
        <span class="dl-card-kicker">Matched-file validation</span>
        <h2>MANUAL prediction compared with PREDEFINED actual label</h2>
        <p>
          Files are joined by normalized identifier. Correct/Wrong is determined
          against the PREDEFINED dataset's actual Buggy/Clean label.
        </p>
      </div></div>

      <div class="dl-grid dl-grid-3">
        <article class="dl-data-card"><span>Matched files</span>
          <strong>{{ matchedRows.length }}</strong></article>
        <article class="dl-data-card"><span>MANUAL prediction correct</span>
          <strong>{{ manualCorrectCount }}</strong></article>
        <article class="dl-data-card"><span>MANUAL prediction wrong</span>
          <strong>{{ manualWrongCount }}</strong></article>
        <article class="dl-data-card"><span>PREDEFINED prediction correct</span>
          <strong>{{ predefinedCorrectCount }}</strong></article>
        <article class="dl-data-card"><span>PREDEFINED prediction wrong</span>
          <strong>{{ predefinedWrongCount }}</strong></article>
        <article class="dl-data-card"><span>Models agree</span>
          <strong>{{ modelsAgreeCount }}</strong></article>
      </div>

      <div class="dl-table-wrap dl-report-predictions-scroll dl-matched-comparison-table"
           *ngIf="matchedRows.length; else noMatchedRows">
        <table class="dl-table">
          <thead><tr>
            <th>File / identifier</th>
            <th>MANUAL prediction</th>
            <th>PREDEFINED prediction</th>
            <th>Actual label</th>
            <th>MANUAL result</th>
            <th>PREDEFINED result</th>
            <th>Models agree</th>
          </tr></thead>
          <tbody><tr *ngFor="let row of matchedRows">
            <td class="dl-mono">{{ row.identifier }}</td>
            <td>{{ label(row.manualPrediction) }}</td>
            <td>{{ label(row.predefinedPrediction) }}</td>
            <td>{{ row.actualLabel === null ? '—' : label(row.actualLabel) }}</td>
            <td>
              <span class="dl-badge"
                    [class.dl-badge-green]="row.manualCorrect === true"
                    [class.dl-badge-red]="row.manualCorrect === false">
                {{ correctness(row.manualCorrect) }}
              </span>
            </td>
            <td>
              <span class="dl-badge"
                    [class.dl-badge-green]="row.predefinedCorrect === true"
                    [class.dl-badge-red]="row.predefinedCorrect === false">
                {{ correctness(row.predefinedCorrect) }}
              </span>
            </td>
            <td>
              <span class="dl-badge"
                    [class.dl-badge-green]="row.modelsAgree"
                    [class.dl-badge-amber]="!row.modelsAgree">
                {{ row.modelsAgree ? 'Yes' : 'No' }}
              </span>
            </td>
          </tr></tbody>
        </table>
      </div>
      <ng-template #noMatchedRows>
        <div class="dl-empty">
          No common file identifiers were found between the two targets.
        </div>
      </ng-template>
    </section>
  `
})
export class ReportDetailComponent implements OnInit {
  group: PredictionRunGroup | null = null;
  manualRun: PredictionRunSummary | null = null;
  predefinedRun: PredictionRunSummary | null = null;
  manualRows: PredictionRow[] = [];
  predefinedRows: PredictionRow[] = [];
  matchedRows: MatchedPredictionRow[] = [];
  loading = true;
  error = '';

  constructor(
    readonly api: DefectLabApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const key = this.route.snapshot.paramMap.get('groupKey');
    if (!key) {
      this.error = 'The report group was not specified.';
      this.loading = false;
      return;
    }
    this.load(key);
  }

  get groupTitle(): string {
    if (!this.group?.runs.length) return 'Prediction report details';
    return this.group.runs.map(run => run.targetDataset.displayName)
      .filter((name, index, names) => names.indexOf(name) === index)
      .join(' + ');
  }

  get manualCorrectCount(): number {
    return this.matchedRows.filter(row => row.manualCorrect === true).length;
  }

  get manualWrongCount(): number {
    return this.matchedRows.filter(row => row.manualCorrect === false).length;
  }

  get predefinedCorrectCount(): number {
    return this.matchedRows.filter(row => row.predefinedCorrect === true).length;
  }

  get predefinedWrongCount(): number {
    return this.matchedRows.filter(row => row.predefinedCorrect === false).length;
  }

  get modelsAgreeCount(): number {
    return this.matchedRows.filter(row => row.modelsAgree).length;
  }

  load(key: string): void {
    this.loading = true;
    this.api.listPredictionGroups().subscribe({
      next: groups => {
        const group = groups.find(item =>
          this.groupKey(item) === key && this.isCompleteGroup(item));
        if (!group) {
          this.error = 'A report requires both MANUAL and PREDEFINED target runs.';
          this.loading = false;
          return;
        }
        this.group = group;
        this.manualRun = group.runs.find(
          run => run.targetDataset.datasetType === 'MANUAL') ?? null;
        this.predefinedRun = group.runs.find(
          run => run.targetDataset.datasetType === 'PREDEFINED') ?? null;
        this.loadPredictions(group);
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the report group.';
        this.loading = false;
      }
    });
  }

  loadPredictions(group: PredictionRunGroup): void {
    const requests = group.runs.map(run => this.api.predictions(run.id, false, 5000));
    forkJoin(requests).subscribe({
      next: results => {
        group.runs.forEach((run, index) => {
          if (run.targetDataset.datasetType === 'MANUAL') {
            this.manualRows = results[index];
          } else {
            this.predefinedRows = results[index];
          }
        });
        this.matchedRows = this.matchPredictions(
          this.manualRows, this.predefinedRows);
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load prediction rows.';
        this.loading = false;
      }
    });
  }

  groupKey(group: PredictionRunGroup): string {
    return group.comparisonGroupId || `run-${group.runs[0].id}`;
  }

  label(value: number): string {
    return value === 1 ? 'Buggy (1)' : 'Clean (0)';
  }

  modelSetting(run: PredictionRunSummary): string {
    const config = run.modelConfig;
    return `KNN · K=${config.k}`;
  }

  enabled(value: boolean): string {
    return value ? 'Enabled' : 'Disabled';
  }

  metric(value: MetricValue): string {
    return value?.value === null || value?.value === undefined
      ? 'N/A' : value.value.toFixed(3);
  }

  correctness(value: boolean | null): string {
    if (value === null) return 'No actual label';
    return value ? 'Correct' : 'Wrong';
  }

  private matchPredictions(
    manualRows: PredictionRow[],
    predefinedRows: PredictionRow[]
  ): MatchedPredictionRow[] {
    const predefinedByIdentifier = new Map<string, PredictionRow>();
    for (const row of predefinedRows) {
      const key = this.normalizeIdentifier(row.classIdentifier);
      if (key && !predefinedByIdentifier.has(key)) {
        predefinedByIdentifier.set(key, row);
      }
    }

    const result: MatchedPredictionRow[] = [];
    for (const manual of manualRows) {
      const predefined = predefinedByIdentifier.get(
        this.normalizeIdentifier(manual.classIdentifier));
      if (!predefined) continue;
      const actual = predefined.actualLabel;
      result.push({
        identifier: manual.classIdentifier,
        manualPrediction: manual.predictedLabel,
        predefinedPrediction: predefined.predictedLabel,
        actualLabel: actual,
        manualCorrect: actual === null
          ? null : manual.predictedLabel === actual,
        predefinedCorrect: actual === null
          ? null : predefined.predictedLabel === actual,
        modelsAgree: manual.predictedLabel === predefined.predictedLabel
      });
    }
    return result;
  }

  private normalizeIdentifier(value: string): string {
    return (value || '').trim().toLowerCase()
      .replace(/\\/g, '/')
      .replace(/\.java$/, '')
      .replace(/\s+/g, '');
  }

  private isCompleteGroup(group: PredictionRunGroup): boolean {
    return !!group.comparisonGroupId &&
      group.runs.some(run => run.targetDataset.datasetType === 'MANUAL') &&
      group.runs.some(run => run.targetDataset.datasetType === 'PREDEFINED');
  }

  back(): void {
    this.router.navigate(['/reports']);
  }
}
