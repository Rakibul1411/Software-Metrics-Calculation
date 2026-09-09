import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import {
  EvaluationMetrics,
  MetricValue,
  PredictionRow,
  PredictionRunGroup,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { MatchedPredictionRow, ReportDetailView, ReportsFacade } from './reports.facade';

@Component({
  selector: 'app-report-detail',
  standalone: false,
  templateUrl: './report-detail.component.html'
})
export class ReportDetailComponent extends BaseDetailComponent<ReportDetailView, string> {
  protected readonly listRoute = ['/reports'];
  protected readonly missingMessage = 'The report group was not specified.';
  protected override readonly routeParam = 'groupKey';

  constructor(readonly facade: ReportsFacade) {
    super();
  }

  get manualColumns() {
    return this.facade.manualColumns;
  }

  get predefinedColumns() {
    return this.facade.predefinedColumns;
  }

  get matchedColumns() {
    return this.facade.matchedColumns;
  }

  get group(): PredictionRunGroup | null {
    return this.item?.group ?? null;
  }

  get manualRun(): PredictionRunSummary | null {
    return this.item?.manualRun ?? null;
  }

  get predefinedRun(): PredictionRunSummary | null {
    return this.item?.predefinedRun ?? null;
  }

  manualPage = 1;
  manualPageSize = 10;

  predefinedPage = 1;
  predefinedPageSize = 10;

  matchedPage = 1;
  matchedPageSize = 10;

  get manualRows(): PredictionRow[] {
    return this.item?.manualRows ?? [];
  }

  get pagedManualRows(): PredictionRow[] {
    const start = (this.manualPage - 1) * this.manualPageSize;
    return this.manualRows.slice(start, start + this.manualPageSize);
  }

  get predefinedRows(): PredictionRow[] {
    return this.item?.predefinedRows ?? [];
  }

  get pagedPredefinedRows(): PredictionRow[] {
    const start = (this.predefinedPage - 1) * this.predefinedPageSize;
    return this.predefinedRows.slice(start, start + this.predefinedPageSize);
  }

  get matchedRows(): MatchedPredictionRow[] {
    return this.item?.matchedRows ?? [];
  }

  get pagedMatchedRows(): MatchedPredictionRow[] {
    const start = (this.matchedPage - 1) * this.matchedPageSize;
    return this.matchedRows.slice(start, start + this.matchedPageSize);
  }

  onManualPageChange(p: number): void {
    this.manualPage = p;
  }

  onManualPageSizeChange(s: number): void {
    this.manualPageSize = s;
    this.manualPage = 1;
  }

  onPredefinedPageChange(p: number): void {
    this.predefinedPage = p;
  }

  onPredefinedPageSizeChange(s: number): void {
    this.predefinedPageSize = s;
    this.predefinedPage = 1;
  }

  onMatchedPageChange(p: number): void {
    this.matchedPage = p;
  }

  onMatchedPageSizeChange(s: number): void {
    this.matchedPageSize = s;
    this.matchedPage = 1;
  }

  get groupTitle(): string {
    return this.facade.title(this.item);
  }

  get settingsFields(): DetailField[] {
    return this.facade.settingsFields(this.item);
  }

  /** Only the PREDEFINED run is scored, and only when the group includes one. */
  get evaluation(): EvaluationMetrics | null {
    return this.predefinedRun?.evaluation ?? null;
  }

  /** The identifier-joined panel only applies to PROMISE report groups. */
  get showMatchedComparison(): boolean {
    return !!this.manualRun && !!this.predefinedRun
      && this.facade.supportsIdentifierMatching(this.item);
  }

  get manualCorrectCount(): number {
    return this.matchedRows.filter(row => row.manualCorrect === true).length;
  }

  get manualWrongCount(): number {
    return this.matchedRows.filter(row => row.manualCorrect === false).length;
  }

  get predefinedCorrectCount(): number {
    return this.matchedRows.filter(row => row.predefinedCorrect === true).length;
  }

  get predefinedWrongCount(): number {
    return this.matchedRows.filter(row => row.predefinedCorrect === false).length;
  }

  get modelsAgreeCount(): number {
    return this.matchedRows.filter(row => row.modelsAgree).length;
  }

  label(value: number): string {
    return this.facade.label(value);
  }

  metric(value: MetricValue): string {
    return this.facade.metric(value);
  }

  correctness(value: boolean | null): string {
    return this.facade.correctness(value);
  }

  protected fetch(key: string): Observable<ReportDetailView> {
    this.manualPage = 1;
    this.predefinedPage = 1;
    this.matchedPage = 1;
    return this.facade.detail(key);
  }

  /** Report groups are addressed by a string key rather than a numeric id. */
  protected override readRouteKey(): string | null {
    return this.route.snapshot.paramMap.get(this.routeParam);
  }
}
