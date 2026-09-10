import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseDetailComponent } from '../../core/base';
import { DatasetPreview, DatasetSummary } from '../../core/models/defectlab.model';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { DatasetsFacade } from './datasets.facade';

@Component({
  selector: 'app-dataset-detail',
  standalone: false,
  templateUrl: './dataset-detail.component.html'
})
export class DatasetDetailComponent extends BaseDetailComponent<DatasetSummary> {
  preview: DatasetPreview | null = null;
  previewLoading = true;

  protected readonly listRoute = ['/datasets'];
  protected readonly missingMessage = 'The dataset was not specified.';

  constructor(readonly facade: DatasetsFacade) {
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

  get totalRows(): number {
    return this.allRows.length || (this.preview?.totalRows ?? this.dataset?.totalFiles ?? 0);
  }

  get previewRows(): Array<Record<string, string>> {
    const fromIndex = (this.page - 1) * this.pageSize;
    return this.allRows.slice(fromIndex, fromIndex + this.pageSize);
  }

  /** The stored rows load alongside the record, on their own indicator. */
  override load(id: number): void {
    super.load(id);
    this.previewLoading = true;
    this.watch(this.facade.preview(id)).subscribe({
      next: preview => {
        this.preview = preview;
        this.allRows = this.facade.previewRows(preview);
        this.page = 1;
        this.previewLoading = false;
      },
      error: () => (this.previewLoading = false)
    });
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
