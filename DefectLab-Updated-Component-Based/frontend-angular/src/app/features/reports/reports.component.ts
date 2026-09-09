import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseListComponent } from '../../core/base';
import { PredictionRunGroup } from '../../core/models/defectlab.model';
import { ReportsFacade } from './reports.facade';

@Component({
  selector: 'app-reports',
  standalone: false,
  templateUrl: './reports.component.html'
})
export class ReportsComponent extends BaseListComponent<PredictionRunGroup> {
  constructor(private readonly facade: ReportsFacade) {
    super();
  }

  /** Template alias for the base class's loaded collection. */
  get groups(): PredictionRunGroup[] {
    return this.rows;
  }

  get columns() {
    return this.facade.listColumns;
  }

  groupLabel(group: PredictionRunGroup): string {
    return this.facade.groupLabel(group);
  }

  groupKey(group: PredictionRunGroup): string {
    return this.facade.groupKey(group);
  }

  targetNames(group: PredictionRunGroup): string {
    return this.facade.targetNames(group);
  }

  view(group: PredictionRunGroup): void {
    this.navigateTo(['/reports', this.groupKey(group)]);
  }

  protected fetch(): Observable<PredictionRunGroup[]> {
    return this.facade.listReportableGroups();
  }

  protected override matches(row: PredictionRunGroup, query: string): boolean {
    return this.facade.matches(row, query);
  }
}
