import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import { DatasetPreview, DatasetSummary } from '../../core/models/defectlab.model';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ViewMode } from '../../shared/ui-view-toggle/ui-view-toggle.component';
import { DatasetsFacade } from './datasets.facade';

import { CodeSmellService } from '../../core/services/code-smell.service';
import { ClassAnalysisResult } from '../../core/models/code-smell.model';

export type ClassFilterMode = 'all' | 'clean' | 'buggy';

@Component({
  selector: 'app-dataset-detail',
  standalone: false,
  templateUrl: './dataset-detail.component.html'
})
export class DatasetDetailComponent extends BaseDetailComponent<DatasetSummary> {
  preview: DatasetPreview | null = null;
  previewLoading = true;
  classAnalysisList: ClassAnalysisResult[] = [];
  viewMode: ViewMode = 'table';
  classFilter: ClassFilterMode = 'all';

  protected readonly listRoute = ['/metric-storage'];
  protected readonly missingMessage = 'The dataset was not specified.';

  constructor(
    readonly facade: DatasetsFacade,
    readonly codeSmellService: CodeSmellService
  ) {
    super();
  }

  /** Template alias for the base class's resolved record. */
  get dataset(): DatasetSummary | null {
    return this.item;
  }

  get detailFields(): DetailField[] {
    return this.item ? this.facade.detailFields(this.item) : [];
  }

  get previewColumns(): TableColumn[] {
    return this.facade.previewColumns(this.preview);
  }

  allRows: Array<Record<string, string>> = [];

  page = 1;
  pageSize = 10;

  getRowLabel(r: Record<string, string>): 'clean' | 'buggy' | null {
    for (const [key, val] of Object.entries(r)) {
      const lowerKey = key.toLowerCase().trim();
      if (
        lowerKey === 'class' ||
        lowerKey === 'bug' ||
        lowerKey === 'bugs' ||
        lowerKey === 'defective' ||
        lowerKey === 'label' ||
        lowerKey === 'is_buggy' ||
        lowerKey === 'defect'
      ) {
        const strVal = String(val).toLowerCase().trim();
        if (strVal === 'buggy' || strVal === 'true' || strVal === '1' || strVal === 'defective' || strVal === 'yes') {
          return 'buggy';
        }
        const num = parseFloat(strVal);
        if (!isNaN(num) && num > 0) {
          return 'buggy';
        }
        if (strVal === 'clean' || strVal === 'false' || strVal === '0' || strVal === 'no') {
          return 'clean';
        }
      }
    }
    return null;
  }

  get labelStats(): { hasLabels: boolean; total: number; clean: number; buggy: number; defectRate: string } {
    let clean = 0;
    let buggy = 0;
    for (const r of this.allRows) {
      const label = this.getRowLabel(r);
      if (label === 'clean') clean++;
      else if (label === 'buggy') buggy++;
    }
    const labeledTotal = clean + buggy;
    const rate = labeledTotal > 0 ? ((buggy / labeledTotal) * 100).toFixed(1) + '%' : '0%';
    return {
      hasLabels: labeledTotal > 0,
      total: this.allRows.length,
      clean,
      buggy,
      defectRate: rate
    };
  }

  get classFilterOptions(): SelectOption[] {
    const stats = this.labelStats;
    return [
      { value: 'all', label: `All classes (${stats.total})` },
      { value: 'clean', label: `Clean only (${stats.clean})` },
      { value: 'buggy', label: `Buggy only (${stats.buggy})` }
    ];
  }

  onClassFilterChange(value: string | number | null): void {
    this.classFilter = (value as ClassFilterMode) || 'all';
    this.page = 1;
  }

  get filteredRows(): Array<Record<string, string>> {
    if (this.classFilter === 'all' || !this.labelStats.hasLabels) {
      return this.allRows;
    }
    return this.allRows.filter(r => this.getRowLabel(r) === this.classFilter);
  }

  get totalRows(): number {
    return this.filteredRows.length;
  }

  get previewRows(): Array<Record<string, string>> {
    const fromIndex = (this.page - 1) * this.pageSize;
    return this.filteredRows.slice(fromIndex, fromIndex + this.pageSize);
  }

  isCleanCell(val: string | null | undefined): boolean {
    if (!val) return true;
    const str = String(val).toLowerCase().trim();
    return str === 'clean' || str === '0' || str === 'false' || str === 'no';
  }

  /** The stored rows load alongside the record, on their own indicator. */
  override load(id: number): void {
    super.load(id);
    this.previewLoading = true;
    this.classFilter = 'all';
    this.watch(this.facade.preview(id)).subscribe({
      next: preview => {
        this.preview = preview;
        this.allRows = this.facade.previewRows(preview);
        this.classAnalysisList = this.codeSmellService.parseClassAnalysisList(this.allRows);
        this.page = 1;
        this.previewLoading = false;
      },
      error: () => (this.previewLoading = false)
    });
  }

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
  }

  onPageChange(page: number): void {
    this.page = page;
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 1;
  }

  deleteDataset = (): Observable<unknown> => this.facade.delete(this.item!.id);

  protected fetch(id: number): Observable<DatasetSummary> {
    return this.facade.get(id);
  }
}
