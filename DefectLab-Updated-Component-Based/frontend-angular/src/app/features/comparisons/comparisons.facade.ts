import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  AggregateMetricComparisonRow,
  InstanceMetricComparisonRow,
  MetricComparisonDetail,
  MetricComparisonPair
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

/**
 * Owns the metric-comparison feature's data access and every read of the
 * mode-dependent result shape, so neither the list nor the detail screen has
 * to branch on `comparisonMode` itself.
 */
@Injectable({ providedIn: 'root' })
export class ComparisonsFacade {
  readonly metricStatsColumns: TableColumn[] = [
    { key: 'metric', label: 'Metric', sticky: 'start', className: 'dl-mono', width: '14%' },
    { key: 'meanManual', label: 'Mean manual', width: '18%' },
    { key: 'meanPredefined', label: 'Mean predefined', width: '18%' },
    { key: 'stdManual', label: 'Std manual', width: '16%' },
    { key: 'stdPredefined', label: 'Std predefined', width: '16%' },
    { key: 'percentageDifference', label: 'Percentage difference', sticky: 'end', width: '18%' }
  ];

  readonly instanceRowColumns: TableColumn[] = [
    { key: 'identifier', label: 'File / Identifier', sticky: 'start', className: 'dl-mono', width: '34%' },
    { key: 'metric', label: 'Metric', className: 'dl-mono', width: '14%' },
    { key: 'manualValue', label: 'Manual Value', width: '18%' },
    { key: 'predefinedValue', label: 'Predefined Value', width: '18%' },
    { key: 'status', label: 'Status', sticky: 'end', width: '16%' }
  ];

  readonly listColumns: TableColumn[] = [
    { key: 'pair', label: 'Dataset pair', sticky: 'start' },
    { key: 'family', label: 'Metric family' },
    { key: 'actions', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(private readonly api: DefectLabApiService) {}

  pairs(): Observable<MetricComparisonPair[]> {
    return this.api.metricComparisonPairs();
  }

  /** Pairs with a stored result — the only ones the list screen shows. */
  comparedPairs(): Observable<MetricComparisonPair[]> {
    return this.pairs().pipe(map(rows => rows.filter(pair => pair.cached)));
  }

  get(id: number): Observable<MetricComparisonDetail> {
    return this.api.metricComparison(id);
  }

  run(pair: MetricComparisonPair): Observable<MetricComparisonDetail> {
    return this.api.runMetricComparison({
      manualDatasetId: pair.manualDatasetId,
      predefinedDatasetId: pair.predefinedDatasetId
    });
  }

  delete(id: number): Observable<unknown> {
    return this.api.deleteMetricComparison(id);
  }

  reportUrl(id: number): string {
    return this.api.metricComparisonReportUrl(id);
  }

  matches(pair: MetricComparisonPair, query: string, family: string): boolean {
    return (!query || pair.projectName.toLowerCase().includes(query))
      && (!family || pair.datasetFamily === family);
  }

  aggregateRows(detail: MetricComparisonDetail | null): AggregateMetricComparisonRow[] {
    const result = detail?.result;
    return result?.comparisonMode === 'AGGREGATE' ? result.metrics : [];
  }

  instanceRows(detail: MetricComparisonDetail | null): InstanceMetricComparisonRow[] {
    const result = detail?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.comparisons : [];
  }

  instanceMetricStats(detail: MetricComparisonDetail | null): AggregateMetricComparisonRow[] {
    const result = detail?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.metrics : [];
  }

  matchedIdentifiers(detail: MetricComparisonDetail | null): number {
    const result = detail?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.matchedIdentifiers : 0;
  }

  commonMetricCount(detail: MetricComparisonDetail | null): number {
    return detail?.result.commonNumericMetricCount ?? 0;
  }

  manualOnlyCount(detail: MetricComparisonDetail | null): number {
    const result = detail?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.manualOnly.length : 0;
  }

  predefinedOnlyCount(detail: MetricComparisonDetail | null): number {
    const result = detail?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.predefinedOnly.length : 0;
  }

  number(value: number | null | undefined): string {
    return value === null || value === undefined || !Number.isFinite(value)
      ? '—' : value.toFixed(4);
  }

  percentage(value: number | null | undefined): string {
    return value === null || value === undefined || !Number.isFinite(value)
      ? '—' : `${value.toFixed(2)}%`;
  }
}
