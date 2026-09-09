import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseListComponent } from '../../core/base';
import {
  FAMILY_FILTER_OPTIONS,
  ORIGIN_FILTER_OPTIONS
} from '../../core/constants/dataset-filter.options';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DatasetsFacade } from './datasets.facade';

@Component({
  selector: 'app-datasets',
  standalone: false,
  templateUrl: './datasets.component.html'
})
export class DatasetsComponent extends BaseListComponent<DatasetSummary> {
  familyFilter = '';
  originFilter = '';

  readonly familyFilterOptions = FAMILY_FILTER_OPTIONS;
  readonly originFilterOptions = ORIGIN_FILTER_OPTIONS;

  constructor(readonly facade: DatasetsFacade) {
    super();
  }

  get columns() {
    return this.facade.columns;
  }

  countFamily(family: DatasetFamily): number {
    return this.facade.countByFamily(this.rows, family);
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
    this.resetPage();
  }

  onOriginFilterChange(value: string | number | null): void {
    this.originFilter = (value as string) ?? '';
    this.resetPage();
  }

  protected fetch(): Observable<DatasetSummary[]> {
    return this.facade.list();
  }

  protected override matches(row: DatasetSummary, query: string): boolean {
    return this.facade.matches(row, {
      search: query,
      family: this.familyFilter,
      origin: this.originFilter
    });
  }
}
