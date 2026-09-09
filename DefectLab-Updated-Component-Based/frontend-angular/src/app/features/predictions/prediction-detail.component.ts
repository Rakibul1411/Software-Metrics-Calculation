import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import { PredictionRunDetail } from '../../core/models/defectlab.model';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { PredictionsFacade } from './predictions.facade';

@Component({
  selector: 'app-prediction-detail',
  standalone: false,
  templateUrl: './prediction-detail.component.html'
})
export class PredictionDetailComponent extends BaseDetailComponent<PredictionRunDetail> {
  protected readonly listRoute = ['/predictions'];
  protected readonly missingMessage = 'The prediction run was not specified.';

  page = 1;
  pageSize = 10;

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

  get pagedPredictions() {
    const list = this.run?.predictions || [];
    const start = (this.page - 1) * this.pageSize;
    return list.slice(start, start + this.pageSize);
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

  protected fetch(id: number): Observable<PredictionRunDetail> {
    this.page = 1;
    return this.facade.get(id);
  }
}
