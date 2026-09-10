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

  activeTab: 'manual' | 'predefined' | 'matched' = 'manual';

  manualPage = 1;
  manualPageSize = 10;
  manualFilter: 'all' | 'buggy' | 'clean' = 'all';
  manualSearch = '';

  predefinedPage = 1;
  predefinedPageSize = 10;
  predefinedFilter: 'all' | 'buggy' | 'clean' = 'all';
  predefinedSearch = '';

  matchedPage = 1;
  matchedPageSize = 10;
  matchedFilter: 'all' | 'disagree' | 'buggy' = 'all';
  matchedSearch = '';

  get manualRows(): PredictionRow[] {
    return this.item?.manualRows ?? [];
  }

  get filteredManualRows(): PredictionRow[] {
    let rows = this.manualRows;
    if (this.manualFilter === 'buggy') {
      rows = rows.filter(r => r.predictedLabel === 1);
    } else if (this.manualFilter === 'clean') {
      rows = rows.filter(r => r.predictedLabel === 0);
    }
    if (this.manualSearch.trim()) {
      const q = this.manualSearch.trim().toLowerCase();
      rows = rows.filter(r => r.classIdentifier.toLowerCase().includes(q));
    }
    return rows;
  }

  get pagedManualRows(): PredictionRow[] {
    const start = (this.manualPage - 1) * this.manualPageSize;
    return this.filteredManualRows.slice(start, start + this.manualPageSize);
  }

  get manualBuggyCount(): number {
    return this.manualRows.filter(r => r.predictedLabel === 1).length;
  }

  get manualCleanCount(): number {
    return this.manualRows.filter(r => r.predictedLabel === 0).length;
  }

  get predefinedRows(): PredictionRow[] {
    return this.item?.predefinedRows ?? [];
  }

  get filteredPredefinedRows(): PredictionRow[] {
    let rows = this.predefinedRows;
    if (this.predefinedFilter === 'buggy') {
      rows = rows.filter(r => r.predictedLabel === 1);
    } else if (this.predefinedFilter === 'clean') {
      rows = rows.filter(r => r.predictedLabel === 0);
    }
    if (this.predefinedSearch.trim()) {
      const q = this.predefinedSearch.trim().toLowerCase();
      rows = rows.filter(r => r.classIdentifier.toLowerCase().includes(q));
    }
    return rows;
  }

  get pagedPredefinedRows(): PredictionRow[] {
    const start = (this.predefinedPage - 1) * this.predefinedPageSize;
    return this.filteredPredefinedRows.slice(start, start + this.predefinedPageSize);
  }

  get predefinedBuggyCount(): number {
    return this.predefinedRows.filter(r => r.predictedLabel === 1).length;
  }

  get predefinedCleanCount(): number {
    return this.predefinedRows.filter(r => r.predictedLabel === 0).length;
  }

  get matchedRows(): MatchedPredictionRow[] {
    return this.item?.matchedRows ?? [];
  }

  get filteredMatchedRows(): MatchedPredictionRow[] {
    let rows = this.matchedRows;
    if (this.matchedFilter === 'disagree') {
      rows = rows.filter(r => !r.modelsAgree);
    } else if (this.matchedFilter === 'buggy') {
      rows = rows.filter(r => r.manualPrediction === 1 || r.predefinedPrediction === 1);
    }
    if (this.matchedSearch.trim()) {
      const q = this.matchedSearch.trim().toLowerCase();
      rows = rows.filter(r => r.identifier.toLowerCase().includes(q));
    }
    return rows;
  }

  get pagedMatchedRows(): MatchedPredictionRow[] {
    const start = (this.matchedPage - 1) * this.matchedPageSize;
    return this.filteredMatchedRows.slice(start, start + this.matchedPageSize);
  }

  get matchedDisagreeCount(): number {
    return this.matchedRows.filter(r => !r.modelsAgree).length;
  }

  get matchedBuggyCount(): number {
    return this.matchedRows.filter(r => r.manualPrediction === 1 || r.predefinedPrediction === 1).length;
  }

  setManualFilter(filter: 'all' | 'buggy' | 'clean'): void {
    this.manualFilter = filter;
    this.manualPage = 1;
  }

  onManualSearch(q: string): void {
    this.manualSearch = q;
    this.manualPage = 1;
  }

  setPredefinedFilter(filter: 'all' | 'buggy' | 'clean'): void {
    this.predefinedFilter = filter;
    this.predefinedPage = 1;
  }

  onPredefinedSearch(q: string): void {
    this.predefinedSearch = q;
    this.predefinedPage = 1;
  }

  setMatchedFilter(filter: 'all' | 'disagree' | 'buggy'): void {
    this.matchedFilter = filter;
    this.matchedPage = 1;
  }

  onMatchedSearch(q: string): void {
    this.matchedSearch = q;
    this.matchedPage = 1;
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
