import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import { PredictionRow, PredictionRunDetail } from '../../core/models/defectlab.model';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { PredictionsFacade } from './predictions.facade';

export type PredictionFilterMode =
  | 'all'
  | 'pred_buggy'
  | 'pred_clean'
  | 'actual_buggy'
  | 'actual_clean';

@Component({
  selector: 'app-prediction-detail',
  standalone: false,
  templateUrl: './prediction-detail.component.html'
})
export class PredictionDetailComponent extends BaseDetailComponent<PredictionRunDetail> {
  protected readonly listRoute = ['/defect-predictions'];
  protected readonly missingMessage = 'The prediction run was not specified.';

  page = 1;
  pageSize = 10;
  filter: PredictionFilterMode = 'all';

  constructor(readonly facade: PredictionsFacade) {
    super();
  }

  /** Template alias for the base class's resolved record. */
  get run(): PredictionRunDetail | null {
    return this.item;
  }

  get runFields(): DetailField[] {
    return this.item ? this.facade.detailFields(this.item) : [];
  }

  get predictionDetailColumns(): TableColumn[] {
    return this.facade.detailColumns(this.item);
  }

  get predictionStats(): {
    total: number;
    predClean: number;
    predBuggy: number;
    hasActual: boolean;
    actualClean: number;
    actualBuggy: number;
  } {
    const list = this.run?.predictions || [];
    let predClean = 0;
    let predBuggy = 0;
    let actualClean = 0;
    let actualBuggy = 0;
    let hasActual = false;

    for (const p of list) {
      if (p.predictedLabel === 1) predBuggy++;
      else predClean++;

      if (p.actualLabel !== null && p.actualLabel !== undefined) {
        hasActual = true;
        if (p.actualLabel === 1) actualBuggy++;
        else actualClean++;
      }
    }

    return {
      total: list.length,
      predClean,
      predBuggy,
      hasActual,
      actualClean,
      actualBuggy
    };
  }

  get filterOptions(): SelectOption[] {
    const s = this.predictionStats;
    const options: SelectOption[] = [
      { value: 'all', label: `All classes (${s.total.toLocaleString()})` },
      { value: 'pred_clean', label: `Predicted Clean (${s.predClean.toLocaleString()})` },
      { value: 'pred_buggy', label: `Predicted Buggy (${s.predBuggy.toLocaleString()})` }
    ];

    if (s.hasActual) {
      options.push(
        { value: 'actual_clean', label: `Actual Clean (${s.actualClean.toLocaleString()})` },
        { value: 'actual_buggy', label: `Actual Buggy (${s.actualBuggy.toLocaleString()})` }
      );
    }

    return options;
  }

  onFilterChange(value: string | number | null): void {
    this.filter = (value as PredictionFilterMode) || 'all';
    this.page = 1;
  }

  get filteredPredictions(): PredictionRow[] {
    const list = this.run?.predictions || [];
    if (this.filter === 'all') return list;
    if (this.filter === 'pred_buggy') return list.filter(p => p.predictedLabel === 1);
    if (this.filter === 'pred_clean') return list.filter(p => p.predictedLabel === 0);
    if (this.filter === 'actual_buggy') return list.filter(p => p.actualLabel === 1);
    if (this.filter === 'actual_clean') return list.filter(p => p.actualLabel === 0);
    return list;
  }

  get totalPredictions(): number {
    return this.filteredPredictions.length;
  }

  get pagedPredictions(): PredictionRow[] {
    const start = (this.page - 1) * this.pageSize;
    return this.filteredPredictions.slice(start, start + this.pageSize);
  }

  onPageChange(page: number): void {
    this.page = page;
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 1;
  }

  metric(value: { value: number | null }): string {
    return this.facade.metric(value);
  }

  deletePredictionRun = (): Observable<unknown> => this.facade.delete(this.item!.id);

  override load(id: number): void {
    this.page = 1;
    this.filter = 'all';
    super.load(id);
  }

  protected fetch(id: number): Observable<PredictionRunDetail> {
    return this.facade.get(id);
  }
}
