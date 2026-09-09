import { Injectable } from '@angular/core';
import { Observable, forkJoin, of, throwError } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import {
  MetricValue,
  PredictionRow,
  PredictionRunGroup,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

/** One file scored by both the MANUAL and the PREDEFINED run. */
export interface MatchedPredictionRow {
  identifier: string;
  manualPrediction: number;
  predefinedPrediction: number;
  actualLabel: number | null;
  manualCorrect: boolean | null;
  predefinedCorrect: boolean | null;
  modelsAgree: boolean;
}

/** The fully-assembled side-by-side report a detail screen renders. */
export interface ReportDetailView {
  group: PredictionRunGroup;
  manualRun: PredictionRunSummary | null;
  predefinedRun: PredictionRunSummary | null;
  manualRows: PredictionRow[];
  predefinedRows: PredictionRow[];
  matchedRows: MatchedPredictionRow[];
}

/**
 * Owns report assembly: finding the complete MANUAL+PREDEFINED group,
 * loading both runs' predictions, and aligning them file-by-file. The detail
 * screen receives one finished view model rather than orchestrating three
 * requests and a matching pass itself.
 */
@Injectable({ providedIn: 'root' })
export class ReportsFacade {
  private static readonly MAX_ROWS = 5000;

  readonly listColumns: TableColumn[] = [
    { key: 'group', label: 'Group', sticky: 'start', className: 'dl-mono' },
    { key: 'source', label: 'Source' },
    { key: 'targets', label: 'Targets' },
    { key: 'model', label: 'Model' },
    { key: 'created', label: 'Created at' },
    { key: 'view', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  readonly manualColumns: TableColumn[] = [
    { key: 'classIdentifier', label: 'File / identifier', className: 'dl-mono', sticky: 'start', width: '46%' },
    { key: 'riskRank', label: 'Rank', width: '10%' },
    { key: 'defectProbability', label: 'Probability', width: '22%' },
    { key: 'predictedLabel', label: 'Model prediction', sticky: 'end', width: '22%' }
  ];

  readonly predefinedColumns: TableColumn[] = [
    { key: 'classIdentifier', label: 'File / identifier', className: 'dl-mono', sticky: 'start', width: '38%' },
    { key: 'riskRank', label: 'Rank', width: '8%' },
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

  constructor(private readonly api: DefectLabApiService) {}

  /** Only groups holding both a MANUAL and a PREDEFINED run are reportable. */
  listReportableGroups(): Observable<PredictionRunGroup[]> {
    return this.api.listPredictionGroups().pipe(
      switchMap(groups => of(groups.filter(group => this.isComplete(group)))));
  }

  /** Resolves one report end to end: group lookup, both runs, then matching. */
  detail(key: string): Observable<ReportDetailView> {
    return this.api.listPredictionGroups().pipe(
      switchMap(groups => {
        const group = groups.find(
          item => this.groupKey(item) === key && this.isComplete(item));
        if (!group) {
          return throwError(() => new Error(
            'A report requires both MANUAL and PREDEFINED target runs.'));
        }
        const requests = group.runs.map(
          run => this.api.predictions(run.id, false, ReportsFacade.MAX_ROWS));
        return forkJoin(requests).pipe(
          switchMap(results => of(this.assemble(group, results))));
      }));
  }

  groupKey(group: PredictionRunGroup): string {
    return group.comparisonGroupId || `run-${group.runs[0].id}`;
  }

  /** Same abbreviation the prediction list uses, so a group reads the same way. */
  groupLabel(group: PredictionRunGroup): string {
    const id = group.comparisonGroupId;
    return id ? id.slice(0, 8) : `run-${group.runs[0].id}`;
  }

  matches(group: PredictionRunGroup, query: string): boolean {
    if (!query) {
      return true;
    }
    const haystack = [
      this.groupKey(group),
      group.runs[0].sourceDataset.displayName,
      this.targetNames(group)
    ].join(' ').toLowerCase();
    return haystack.includes(query);
  }

  targetNames(group: PredictionRunGroup): string {
    return group.runs
      .map(run => `${run.targetDataset.displayName} (${run.targetDataset.datasetType})`)
      .join(' + ');
  }

  title(view: ReportDetailView | null): string {
    if (!view?.group.runs.length) {
      return 'Prediction report details';
    }
    return view.group.runs.map(run => run.targetDataset.displayName)
      .filter((name, index, names) => names.indexOf(name) === index)
      .join(' + ');
  }

  settingsFields(view: ReportDetailView | null): DetailField[] {
    const run = view?.group.runs[0];
    if (!run) {
      return [];
    }
    return [
      { label: 'Source dataset', value: run.sourceDataset.displayName },
      { label: 'Dataset family', value: run.modelConfig.datasetFamily },
      { label: 'Model', value: `KNN · K=${run.modelConfig.k}` },
      { label: 'Threshold', value: run.modelConfig.threshold },
      { label: 'CORAL alignment', value: run.modelConfig.coral ? 'Enabled' : 'Disabled' },
      { label: 'Random seed', value: run.modelConfig.seed }
    ];
  }

  /**
   * AEEEM rows are keyed by synthetic row ids rather than class names, so the
   * two targets share no file identifiers and the side-by-side match is only
   * meaningful for PROMISE.
   */
  supportsIdentifierMatching(view: ReportDetailView | null): boolean {
    return view?.group.runs[0]?.modelConfig.datasetFamily === 'PROMISE';
  }

  label(value: number): string {
    return value === 1 ? 'Buggy (1)' : 'Clean (0)';
  }

  metric(value: MetricValue): string {
    return value?.value === null || value?.value === undefined
      ? 'N/A' : value.value.toFixed(3);
  }

  correctness(value: boolean | null): string {
    if (value === null) {
      return 'No actual label';
    }
    return value ? 'Correct' : 'Wrong';
  }

  predictionDownloadUrl(id: number): string {
    return this.api.predictionDownloadUrl(id);
  }

  reportDownloadUrl(id: number): string {
    return this.api.reportDownloadUrl(id);
  }

  private assemble(
    group: PredictionRunGroup,
    results: PredictionRow[][]
  ): ReportDetailView {
    let manualRows: PredictionRow[] = [];
    let predefinedRows: PredictionRow[] = [];
    group.runs.forEach((run, index) => {
      if (run.targetDataset.datasetType === 'MANUAL') {
        manualRows = results[index];
      } else {
        predefinedRows = results[index];
      }
    });
    return {
      group,
      manualRun: group.runs.find(
        run => run.targetDataset.datasetType === 'MANUAL') ?? null,
      predefinedRun: group.runs.find(
        run => run.targetDataset.datasetType === 'PREDEFINED') ?? null,
      manualRows,
      predefinedRows,
      matchedRows: this.match(manualRows, predefinedRows)
    };
  }

  /**
   * Pairs the two runs by class identifier. Identifiers are normalized the
   * same way the backend comparison does it, so a `a\b\C.java` path and
   * `a/b/C` name still line up.
   */
  private match(
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
      if (!predefined) {
        continue;
      }
      const actual = predefined.actualLabel;
      result.push({
        identifier: manual.classIdentifier,
        manualPrediction: manual.predictedLabel,
        predefinedPrediction: predefined.predictedLabel,
        actualLabel: actual,
        manualCorrect: actual === null ? null : manual.predictedLabel === actual,
        predefinedCorrect: actual === null ? null : predefined.predictedLabel === actual,
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

  private isComplete(group: PredictionRunGroup): boolean {
    return !!group.comparisonGroupId &&
      group.runs.some(run => run.targetDataset.datasetType === 'MANUAL') &&
      group.runs.some(run => run.targetDataset.datasetType === 'PREDEFINED');
  }
}
