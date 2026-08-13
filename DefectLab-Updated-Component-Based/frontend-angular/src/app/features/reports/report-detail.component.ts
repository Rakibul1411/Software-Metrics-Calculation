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
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

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
  templateUrl: './report-detail.component.html'
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

  readonly manualColumns: TableColumn[] = [
    { key: 'riskRank', label: 'Rank', sticky: 'start', width: '10%' },
    { key: 'classIdentifier', label: 'File / identifier', className: 'dl-mono', width: '46%' },
    { key: 'defectProbability', label: 'Probability', width: '22%' },
    { key: 'predictedLabel', label: 'Model prediction', sticky: 'end', width: '22%' }
  ];

  readonly predefinedColumns: TableColumn[] = [
    { key: 'riskRank', label: 'Rank', sticky: 'start', width: '8%' },
    { key: 'classIdentifier', label: 'File / identifier', className: 'dl-mono', width: '38%' },
    { key: 'defectProbability', label: 'Probability', width: '18%' },
    { key: 'predictedLabel', label: 'Model prediction', width: '18%' },
    { key: 'actualLabel', label: 'Actual', sticky: 'end', width: '18%' }
  ];

  readonly matchedColumns: TableColumn[] = [
    { key: 'identifier', label: 'File / identifier', sticky: 'start', className: 'dl-mono', width: '26%' },
    { key: 'manualPrediction', label: 'MANUAL prediction', width: '13%' },
    { key: 'predefinedPrediction', label: 'PREDEFINED prediction', width: '13%' },
    { key: 'actualLabel', label: 'Actual label', width: '12%' },
    { key: 'manualResult', label: 'MANUAL result', width: '12%' },
    { key: 'predefinedResult', label: 'PREDEFINED result', width: '12%' },
    { key: 'modelsAgree', label: 'Models agree', sticky: 'end', width: '12%' }
  ];

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

  get settingsFields(): DetailField[] {
    const run = this.group?.runs[0];
    if (!run) return [];
    return [
      { label: 'Source dataset', value: run.sourceDataset.displayName },
      { label: 'Dataset family', value: run.modelConfig.datasetFamily },
      { label: 'Model', value: this.modelSetting(run) },
      { label: 'Threshold', value: run.modelConfig.threshold },
      { label: 'Log transform', value: this.enabled(run.modelConfig.logTransform) },
      { label: 'CORAL alignment', value: this.enabled(run.modelConfig.coral) },
      { label: 'Random seed', value: run.modelConfig.seed }
    ];
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
