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

  get instanceRows(): InstanceMetricComparisonRow[] {
    return this.facade.instanceRows(this.item);
  }

  get instanceMetricStats(): AggregateMetricComparisonRow[] {
    return this.facade.instanceMetricStats(this.item);
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

  number(value: number | null | undefined): string {
    return this.facade.number(value);
  }

  percentage(value: number | null | undefined): string {
    return this.facade.percentage(value);
  }

  deleteComparison = (): Observable<unknown> => this.facade.delete(this.item!.id);

  protected fetch(id: number): Observable<MetricComparisonDetail> {
    return this.facade.get(id);
  }
}
