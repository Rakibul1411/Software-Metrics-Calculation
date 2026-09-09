import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import {
  AggregateMetricComparisonRow,
  InstanceMetricComparisonRow,
  MetricComparisonDetail
} from '../../core/models/defectlab.model';
import { ComparisonsFacade } from './comparisons.facade';

@Component({
  selector: 'app-comparison-detail',
  standalone: false,
  templateUrl: './comparison-detail.component.html'
})
export class ComparisonDetailComponent extends BaseDetailComponent<MetricComparisonDetail> {
  protected readonly listRoute = ['/metric-comparisons'];
  protected readonly missingMessage = 'The comparison was not specified.';

  metricPage = 1;
  metricPageSize = 10;
  filePage = 1;
  filePageSize = 10;

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

  get pagedInstanceRows(): InstanceMetricComparisonRow[] {
    const start = (this.filePage - 1) * this.filePageSize;
    return this.instanceRows.slice(start, start + this.filePageSize);
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

  protected fetch(id: number): Observable<MetricComparisonDetail> {
    this.metricPage = 1;
    this.filePage = 1;
    return this.facade.get(id);
  }
}
