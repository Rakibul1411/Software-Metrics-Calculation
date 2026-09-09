import { DatePipe } from '@angular/common';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  MetricValue,
  PredictionExecution,
  PredictionRunDetail,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

/** Everything the prediction form needs to describe one run. */
export interface PredictionRequest {
  sourceId: number | null;
  manualId: number | null;
  predefinedId: number | null;
  k: number;
  coral: boolean;
  threshold: number;
}

/**
 * Owns every non-presentational decision the Predictions feature makes:
 * which datasets may be selected for a run, how a run is described, and how
 * its result table is shaped. The components only bind to what it returns.
 */
@Injectable({ providedIn: 'root' })
export class PredictionsFacade {
  private static readonly SEED = 42;
  readonly modelName = 'KNN';

  constructor(
    private readonly api: DefectLabApiService,
    private readonly datePipe: DatePipe
  ) {}

  /** The full UUID swamps the row; its first block still identifies a group. */
  groupLabel(run: PredictionRunSummary): string {
    return run.comparisonGroupId ? run.comparisonGroupId.slice(0, 8) : '—';
  }

  list(): Observable<PredictionRunSummary[]> {
    return this.api.listPredictionRuns();
  }

  get(id: number): Observable<PredictionRunDetail> {
    return this.api.predictionRun(id);
  }

  delete(id: number): Observable<unknown> {
    return this.api.deletePredictionRun(id);
  }

  run(request: PredictionRequest): Observable<PredictionExecution> {
    return this.api.runPrediction({
      sourceDatasetId: request.sourceId,
      manualTargetDatasetId: request.manualId,
      predefinedTargetDatasetId: request.predefinedId,
      modelName: this.modelName,
      k: request.k,
      coral: request.coral,
      threshold: request.threshold,
      seed: PredictionsFacade.SEED
    });
  }

  predictionDownloadUrl(id: number): string {
    return this.api.predictionDownloadUrl(id);
  }

  reportDownloadUrl(id: number): string {
    return this.api.reportDownloadUrl(id);
  }

  matchesSearch(run: PredictionRunSummary, query: string, family = ''): boolean {
    return (!query || run.targetDataset.displayName.toLowerCase().includes(query))
      && (!family || run.modelConfig.datasetFamily === family);
  }

  modelSetting(run: PredictionRunSummary): string {
    return `K=${run.modelConfig.k}`;
  }

  metric(value: MetricValue | { value: number | null }): string {
    return value?.value === null || value?.value === undefined
      ? 'N/A' : value.value.toFixed(3);
  }

  detailFields(run: PredictionRunDetail): DetailField[] {
    return [
      { label: 'Source dataset', value: run.sourceDataset.displayName },
      { label: 'Target dataset', value: run.targetDataset.displayName },
      {
        label: 'Target type',
        value: run.targetDataset.datasetType === 'PREDEFINED'
          ? 'Predefined' : 'Manual extracted'
      },
      { label: 'Metric family', value: run.modelConfig.datasetFamily },
      { label: 'Model', value: run.modelConfig.modelName },
      { label: 'Neighbors (K)', value: run.modelConfig.k },
      { label: 'Decision threshold', value: run.modelConfig.threshold },
      {
        label: 'Dataset alignment',
        value: run.modelConfig.coral ? 'Enabled' : 'Disabled'
      },
      { label: 'Created', value: this.datePipe.transform(run.createdAt, 'medium') }
    ];
  }

  /** The Actual column only exists when the target carries ground truth. */
  detailColumns(run: PredictionRunDetail | null): TableColumn[] {
    const hasActual = run?.targetDataset.datasetType === 'PREDEFINED';
    const columns: TableColumn[] = [
      {
        key: 'riskRank', label: 'Rank', align: 'right',
        className: 'dl-col-rank', sticky: 'start', width: '8%'
      },
      {
        key: 'classIdentifier', label: 'File / identifier',
        className: 'dl-col-identifier', width: hasActual ? '46%' : '58%'
      },
      {
        key: 'defectProbability', label: 'Probability', align: 'right',
        className: 'dl-col-probability', width: '14%'
      },
      { key: 'predictedLabel', label: 'Predicted', width: hasActual ? '16%' : '20%' }
    ];
    if (hasActual) {
      columns.push({ key: 'actualLabel', label: 'Actual', width: '16%' });
    }
    columns[columns.length - 1].sticky = 'end';
    return columns;
  }
}
