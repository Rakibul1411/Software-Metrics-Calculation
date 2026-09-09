import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseListComponent } from '../../core/base';
import { FAMILY_FILTER_OPTIONS } from '../../core/constants/dataset-filter.options';
import { MetricComparisonPair } from '../../core/models/defectlab.model';
import { ComparisonsFacade } from './comparisons.facade';

@Component({
  selector: 'app-comparisons',
  standalone: false,
  templateUrl: './comparisons.component.html'
})
export class ComparisonsComponent extends BaseListComponent<MetricComparisonPair> {
  familyFilter = '';

  readonly familyFilterOptions = FAMILY_FILTER_OPTIONS;

  constructor(private readonly facade: ComparisonsFacade) {
    super();
  }

  /** Template alias for the base class's loaded collection. */
  get pairs(): MetricComparisonPair[] {
    return this.rows;
  }

  get columns() {
    return this.facade.listColumns;
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
    this.resetPage();
  }

  view(pair: MetricComparisonPair): void {
    if (pair.comparisonId) {
      this.navigateTo(['/metric-comparisons', pair.comparisonId]);
    }
  }

  protected fetch(): Observable<MetricComparisonPair[]> {
    return this.facade.comparedPairs();
  }

  protected override matches(row: MetricComparisonPair, query: string): boolean {
    return this.facade.matches(row, query, this.familyFilter);
  }
}
