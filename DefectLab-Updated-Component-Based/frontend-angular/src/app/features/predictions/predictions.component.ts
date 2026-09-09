import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseListComponent } from '../../core/base';
import { FAMILY_FILTER_OPTIONS } from '../../core/constants/dataset-filter.options';
import { PredictionRunSummary } from '../../core/models/defectlab.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { PredictionsFacade } from './predictions.facade';

@Component({
  selector: 'app-predictions',
  standalone: false,
  templateUrl: './predictions.component.html'
})
export class PredictionsComponent extends BaseListComponent<PredictionRunSummary> {
  familyFilter = '';

  readonly familyFilterOptions = FAMILY_FILTER_OPTIONS;

  readonly runsColumns: TableColumn[] = [
    { key: 'run', label: 'Run ID', sticky: 'start', className: 'dl-mono' },
    { key: 'group', label: 'Comparison group', className: 'dl-mono' },
    { key: 'target', label: 'Target dataset' },
    { key: 'family', label: 'Metric family' },
    { key: 'type', label: 'Data source' },
    { key: 'model', label: 'Model' },
    { key: 'buggy', label: 'Predicted buggy', align: 'right' },
    { key: 'created', label: 'Created at' },
    { key: 'inspect', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(private readonly facade: PredictionsFacade) {
    super();
  }

  /** Template alias for the base class's loaded collection. */
  get runs(): PredictionRunSummary[] {
    return this.rows;
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
    this.resetPage();
  }

  groupLabel(run: PredictionRunSummary): string {
    return this.facade.groupLabel(run);
  }

  setting(run: PredictionRunSummary): string {
    return this.facade.modelSetting(run);
  }

  protected fetch(): Observable<PredictionRunSummary[]> {
    return this.facade.list();
  }

  protected override matches(row: PredictionRunSummary, query: string): boolean {
    return this.facade.matchesSearch(row, query, this.familyFilter);
  }
}
