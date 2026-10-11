import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { BaseDetailComponent } from '../../core/base';
import {
  DatasetPreview,
  EvaluationMetrics,
  MetricValue,
  PredictionRow,
  PredictionRunGroup,
  PredictionRunSummary,
  CoralTsneResponse
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ViewMode } from '../../shared/ui-view-toggle/ui-view-toggle.component';
import { MatchedPredictionRow, ReportDetailView, ReportsFacade } from './reports.facade';

import { CodeSmellService } from '../../core/services/code-smell.service';
import { ClassAnalysisResult } from '../../core/models/code-smell.model';

@Component({
  selector: 'app-report-detail',
  standalone: false,
  templateUrl: './report-detail.component.html'
})
export class ReportDetailComponent extends BaseDetailComponent<ReportDetailView, string> {
  protected readonly listRoute = ['/prediction-reports'];
  protected readonly missingMessage = 'The report group was not specified.';
  protected override readonly routeParam = 'groupKey';
  protected override autoRefreshEnabled = false;

  viewMode: ViewMode = 'table';
  coralTsneData: CoralTsneResponse | null = null;
  loadingCoralTsne = false;
  manualDatasetRows: Array<Record<string, string | number>> = [];
  predefinedDatasetRows: Array<Record<string, string | number>> = [];

  manualTreemapItems: ClassAnalysisResult[] = [];
  predefinedTreemapItems: ClassAnalysisResult[] = [];

  constructor(
    readonly facade: ReportsFacade,
    readonly codeSmellService: CodeSmellService,
    private readonly api: DefectLabApiService
  ) {
    super();
  }

  get isCoralEnabled(): boolean {
    const run = this.activeRun;
    return !!(run?.modelConfig?.coral);
  }

  get activeRun(): PredictionRunSummary | null {
    if (this.activeTab === 'manual') return this.manualRun;
    if (this.activeTab === 'predefined') return this.predefinedRun;
    return this.manualRun || this.predefinedRun;
  }

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
    if (mode === 'tsne') {
      this.loadCoralTsne();
    }
  }

  onTabChange(tab: 'manual' | 'predefined' | 'matched'): void {
    this.activeTab = tab;
    if (this.viewMode === 'tsne') {
      const run = this.activeRun;
      if (run?.coralTsne) {
        this.coralTsneData = run.coralTsne;
        this.loadingCoralTsne = false;
      } else if (run?.id && run.modelConfig?.coral) {
        this.coralTsneData = null;
        this.loadCoralTsne();
      }
    }
  }

  loadCoralTsne(force = false): void {
    const run = this.activeRun;
    if (!run?.id) return;

    if (!force && run.coralTsne) {
      this.coralTsneData = run.coralTsne;
      this.loadingCoralTsne = false;
      return;
    }

    if (this.loadingCoralTsne) return;

    this.loadingCoralTsne = true;
    this.api.getCoralTsne(run.id).subscribe({
      next: res => {
        this.coralTsneData = res;
        this.loadingCoralTsne = false;
        if (run) {
          run.coralTsne = res;
        }
      },
      error: err => {
        console.error('Failed to load CORAL t-SNE', err);
        this.loadingCoralTsne = false;
      }
    });
  }

  updateTreemapItems(view?: ReportDetailView): void {
    const v = view || this.item;
    const mRows = v?.manualRows ?? [];
    const pRows = v?.predefinedRows ?? [];
    this.manualTreemapItems = this.codeSmellService.parsePredictionWithDatasetRows(
      mRows, this.manualDatasetRows);
    this.predefinedTreemapItems = this.codeSmellService.parsePredictionWithDatasetRows(
      pRows, this.predefinedDatasetRows);
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
  predefinedFilter: 'all' | 'buggy' | 'clean' | 'actual_buggy' | 'actual_clean' = 'all';
  predefinedSearch = '';

  matchedPage = 1;
  matchedPageSize = 10;
  matchedFilter: 'all' | 'disagree' | 'agree' | 'buggy' = 'all';
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

  get manualDefectRate(): string {
    const total = this.manualRows.length;
    return total > 0 ? ((this.manualBuggyCount / total) * 100).toFixed(1) + '%' : '0%';
  }

  get manualFilterOptions(): SelectOption[] {
    return [
      { value: 'all', label: `All files (${this.manualRows.length.toLocaleString()})` },
      { value: 'clean', label: `Clean only (${this.manualCleanCount.toLocaleString()})` },
      { value: 'buggy', label: `Buggy only (${this.manualBuggyCount.toLocaleString()})` }
    ];
  }

  get predefinedRows(): PredictionRow[] {
    return this.item?.predefinedRows ?? [];
  }

  get predefinedHasActual(): boolean {
    return this.predefinedRows.some(r => r.actualLabel !== null && r.actualLabel !== undefined);
  }

  get predefinedActualBuggyCount(): number {
    return this.predefinedRows.filter(r => r.actualLabel === 1).length;
  }

  get predefinedActualCleanCount(): number {
    return this.predefinedRows.filter(r => r.actualLabel === 0).length;
  }

  get predefinedDefectRate(): string {
    const total = this.predefinedRows.length;
    return total > 0 ? ((this.predefinedBuggyCount / total) * 100).toFixed(1) + '%' : '0%';
  }

  get predefinedFilterOptions(): SelectOption[] {
    const opts: SelectOption[] = [
      { value: 'all', label: `All files (${this.predefinedRows.length.toLocaleString()})` },
      { value: 'clean', label: `Predicted Clean (${this.predefinedCleanCount.toLocaleString()})` },
      { value: 'buggy', label: `Predicted Buggy (${this.predefinedBuggyCount.toLocaleString()})` }
    ];
    if (this.predefinedHasActual) {
      opts.push(
        { value: 'actual_clean', label: `Actual Clean (${this.predefinedActualCleanCount.toLocaleString()})` },
        { value: 'actual_buggy', label: `Actual Buggy (${this.predefinedActualBuggyCount.toLocaleString()})` }
      );
    }
    return opts;
  }

  get filteredPredefinedRows(): PredictionRow[] {
    let rows = this.predefinedRows;
    if (this.predefinedFilter === 'buggy') {
      rows = rows.filter(r => r.predictedLabel === 1);
    } else if (this.predefinedFilter === 'clean') {
      rows = rows.filter(r => r.predictedLabel === 0);
    } else if (this.predefinedFilter === 'actual_buggy') {
      rows = rows.filter(r => r.actualLabel === 1);
    } else if (this.predefinedFilter === 'actual_clean') {
      rows = rows.filter(r => r.actualLabel === 0);
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

  get matchedFilterOptions(): SelectOption[] {
    return [
      { value: 'all', label: `All matched (${this.matchedRows.length.toLocaleString()})` },
      { value: 'agree', label: `Models agree (${this.modelsAgreeCount.toLocaleString()})` },
      { value: 'disagree', label: `Models disagree (${this.matchedDisagreeCount.toLocaleString()})` },
      { value: 'buggy', label: `Buggy in either (${this.matchedBuggyCount.toLocaleString()})` }
    ];
  }

  get filteredMatchedRows(): MatchedPredictionRow[] {
    let rows = this.matchedRows;
    if (this.matchedFilter === 'disagree') {
      rows = rows.filter(r => !r.modelsAgree);
    } else if (this.matchedFilter === 'agree') {
      rows = rows.filter(r => r.modelsAgree);
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

  onManualFilterChange(val: string | number | null): void {
    this.manualFilter = (val as 'all' | 'buggy' | 'clean') || 'all';
    this.manualPage = 1;
  }

  onManualSearch(q: string): void {
    this.manualSearch = q;
    this.manualPage = 1;
  }

  setPredefinedFilter(filter: 'all' | 'buggy' | 'clean' | 'actual_buggy' | 'actual_clean'): void {
    this.predefinedFilter = filter;
    this.predefinedPage = 1;
  }

  onPredefinedFilterChange(val: string | number | null): void {
    this.predefinedFilter = (val as 'all' | 'buggy' | 'clean' | 'actual_buggy' | 'actual_clean') || 'all';
    this.predefinedPage = 1;
  }

  onPredefinedSearch(q: string): void {
    this.predefinedSearch = q;
    this.predefinedPage = 1;
  }

  setMatchedFilter(filter: 'all' | 'disagree' | 'agree' | 'buggy'): void {
    this.matchedFilter = filter;
    this.matchedPage = 1;
  }

  onMatchedFilterChange(val: string | number | null): void {
    this.matchedFilter = (val as 'all' | 'disagree' | 'agree' | 'buggy') || 'all';
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

  /** The identifier-joined panel applies when both runs share matching class/file identifiers. */
  get showMatchedComparison(): boolean {
    return !!this.manualRun && !!this.predefinedRun
      && (this.matchedRows.length > 0 || this.facade.supportsIdentifierMatching(this.item));
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

  override load(key: string): void {
    this.manualPage = 1;
    this.predefinedPage = 1;
    this.matchedPage = 1;
    if (!this.item || this.readRouteKey() !== key) {
      this.coralTsneData = null;
    }
    super.load(key);
  }

  private clampPages(): void {
    const maxManual = Math.max(1, Math.ceil(this.filteredManualRows.length / this.manualPageSize));
    if (this.manualPage > maxManual) this.manualPage = maxManual;

    const maxPredefined = Math.max(1, Math.ceil(this.filteredPredefinedRows.length / this.predefinedPageSize));
    if (this.predefinedPage > maxPredefined) this.predefinedPage = maxPredefined;

    const maxMatched = Math.max(1, Math.ceil(this.filteredMatchedRows.length / this.matchedPageSize));
    if (this.matchedPage > maxMatched) this.matchedPage = maxMatched;
  }

  protected override fetch(key: string): Observable<ReportDetailView> {
    return this.facade.detail(key).pipe(
      tap(view => {
        if (!this.item) {
          if (!view.manualRun && view.predefinedRun) {
            this.activeTab = 'predefined';
          } else if (view.manualRun && !view.predefinedRun) {
            this.activeTab = 'manual';
          }
        }
        if (view.manualRun?.coralTsne) {
          this.coralTsneData = view.manualRun.coralTsne;
        } else if (view.predefinedRun?.coralTsne) {
          this.coralTsneData = view.predefinedRun.coralTsne;
        }
        this.updateTreemapItems(view);
        this.clampPages();
        if (view.manualRun?.targetDataset?.id && !this.manualDatasetRows.length) {
          this.api.previewDataset(view.manualRun.targetDataset.id, 0, 5000).subscribe({
            next: preview => {
              this.manualDatasetRows = this.toDictRows(preview);
              this.updateTreemapItems(view);
            },
            error: () => {}
          });
        }
        if (view.predefinedRun?.targetDataset?.id && !this.predefinedDatasetRows.length) {
          this.api.previewDataset(view.predefinedRun.targetDataset.id, 0, 5000).subscribe({
            next: preview => {
              this.predefinedDatasetRows = this.toDictRows(preview);
              this.updateTreemapItems(view);
            },
            error: () => {}
          });
        }
      })
    );
  }

  private toDictRows(preview: DatasetPreview): Array<Record<string, string | number>> {
    if (!preview?.headers || !preview?.rows) return [];
    return preview.rows.map(rowVals => {
      const obj: Record<string, string | number> = {};
      preview.headers.forEach((h, i) => {
        const val = rowVals[i];
        const num = Number(val);
        const parsed = !isNaN(num) && val !== '' && val !== null && val !== undefined ? num : val;
        obj[h] = parsed;
        obj[h.toLowerCase()] = parsed;
      });
      return obj;
    });
  }

  /** Report groups are addressed by a string key rather than a numeric id. */
  protected override readRouteKey(): string | null {
    return this.route.snapshot.paramMap.get(this.routeParam);
  }
}
