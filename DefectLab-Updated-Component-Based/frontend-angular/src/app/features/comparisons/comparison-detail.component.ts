import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import {
  AggregateMetricComparisonRow,
  InstanceMetricComparisonRow,
  MetricComparisonDetail
} from '../../core/models/defectlab.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ComparisonsFacade } from './comparisons.facade';

@Component({
  selector: 'app-comparison-detail',
  standalone: false,
  templateUrl: './comparison-detail.component.html'
})
export class ComparisonDetailComponent extends BaseDetailComponent<MetricComparisonDetail> {
  protected readonly listRoute = ['/metric-comparisons'];
  protected readonly missingMessage = 'The comparison was not specified.';

  activeTab: 'metrics' | 'files' = 'metrics';
  metricPage = 1;
  metricPageSize = 10;
  filePage = 1;
  filePageSize = 10;
  statusFilter: string = 'ALL';

  constructor(readonly facade: ComparisonsFacade) {
    super();
  }

  /** Template alias for the base class's resolved record. */
  get comparison(): MetricComparisonDetail | null {
    return this.item;
  }

  get metricStatsColumns() {
    return this.facade.metricStatsColumns;
  }

  get instanceRowColumns() {
    return this.facade.instanceRowColumns;
  }

  get aggregateRows(): AggregateMetricComparisonRow[] {
    return this.facade.aggregateRows(this.item);
  }

  get pagedAggregateRows(): AggregateMetricComparisonRow[] {
    const start = (this.metricPage - 1) * this.metricPageSize;
    return this.aggregateRows.slice(start, start + this.metricPageSize);
  }

  get instanceRows(): InstanceMetricComparisonRow[] {
    return this.facade.instanceRows(this.item);
  }

  get statusCounts(): {
    total: number;
    exact: number;
    close: number;
    mismatch: number;
    notComparable: number;
  } {
    let exact = 0;
    let close = 0;
    let mismatch = 0;
    let notComparable = 0;
    for (const r of this.instanceRows) {
      if (r.status === 'EXACT_MATCH') exact++;
      else if (r.status === 'CLOSE_MATCH') close++;
      else if (r.status === 'MISMATCH') mismatch++;
      else if (r.status === 'NOT_COMPARABLE') notComparable++;
    }
    return {
      total: this.instanceRows.length,
      exact,
      close,
      mismatch,
      notComparable
    };
  }

  get statusFilterOptions(): SelectOption[] {
    const s = this.statusCounts;
    return [
      { value: 'ALL', label: `All statuses (${s.total.toLocaleString()})` },
      { value: 'EXACT_MATCH', label: `Exact match (${s.exact.toLocaleString()})` },
      { value: 'CLOSE_MATCH', label: `Close match (${s.close.toLocaleString()})` },
      { value: 'MISMATCH', label: `Mismatch (${s.mismatch.toLocaleString()})` },
      { value: 'NOT_COMPARABLE', label: `Not comparable (${s.notComparable.toLocaleString()})` }
    ];
  }

  onStatusFilterChange(value: string | number | null): void {
    this.statusFilter = (value as string) || 'ALL';
    this.filePage = 1;
  }

  get filteredInstanceRows(): InstanceMetricComparisonRow[] {
    if (this.statusFilter === 'ALL') {
      return this.instanceRows;
    }
    return this.instanceRows.filter(r => r.status === this.statusFilter);
  }

  get totalInstanceRows(): number {
    return this.filteredInstanceRows.length;
  }

  get pagedInstanceRows(): InstanceMetricComparisonRow[] {
    const start = (this.filePage - 1) * this.filePageSize;
    return this.filteredInstanceRows.slice(start, start + this.filePageSize);
  }

  formatStatus(status: string): string {
    switch (status) {
      case 'EXACT_MATCH': return 'Exact Match';
      case 'CLOSE_MATCH': return 'Close Match';
      case 'MISMATCH': return 'Mismatch';
      case 'NOT_COMPARABLE': return 'Not Comparable';
      default: return status || '-';
    }
  }

  statusTone(status: string): string {
    switch (status) {
      case 'EXACT_MATCH': return 'clean';
      case 'CLOSE_MATCH': return 'warn';
      case 'MISMATCH': return 'danger';
      case 'NOT_COMPARABLE': return 'muted';
      default: return 'muted';
    }
  }

  get instanceMetricStats(): AggregateMetricComparisonRow[] {
    return this.facade.instanceMetricStats(this.item);
  }

  get pagedInstanceMetricStats(): AggregateMetricComparisonRow[] {
    const start = (this.metricPage - 1) * this.metricPageSize;
    return this.instanceMetricStats.slice(start, start + this.metricPageSize);
  }

  get matchedIdentifiers(): number {
    return this.facade.matchedIdentifiers(this.item);
  }

  get commonMetricCount(): number {
    return this.facade.commonMetricCount(this.item);
  }

  get manualOnlyCount(): number {
    return this.facade.manualOnlyCount(this.item);
  }

  get predefinedOnlyCount(): number {
    return this.facade.predefinedOnlyCount(this.item);
  }

  onMetricPageChange(page: number): void {
    this.metricPage = page;
  }

  onMetricPageSizeChange(size: number): void {
    this.metricPageSize = size;
    this.metricPage = 1;
  }

  onFilePageChange(page: number): void {
    this.filePage = page;
  }

  onFilePageSizeChange(size: number): void {
    this.filePageSize = size;
    this.filePage = 1;
  }

  number(value: number | null | undefined): string {
    return this.facade.number(value);
  }

  percentage(value: number | null | undefined): string {
    return this.facade.percentage(value);
  }

  deleteComparison = (): Observable<unknown> => this.facade.delete(this.item!.id);

  override load(id: number): void {
    this.metricPage = 1;
    this.filePage = 1;
    this.statusFilter = 'ALL';
    super.load(id);
  }

  protected fetch(id: number): Observable<MetricComparisonDetail> {
    return this.facade.get(id);
  }
}
