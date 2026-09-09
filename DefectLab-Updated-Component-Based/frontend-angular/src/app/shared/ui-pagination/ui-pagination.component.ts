import { Component, EventEmitter, Input, Output } from '@angular/core';
import { SelectOption } from '../ui-select/ui-select.model';

/** The page sizes every list offers. */
export const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100];

/**
 * The footer every list table shares: rows-per-page, the visible range, and
 * page stepping. Paging is client-side — the screens already hold the whole
 * collection for search and filtering — so this component only reports what
 * the user asked for and the list slices its own rows.
 */
@Component({
  selector: 'ui-pagination',
  standalone: false,
  templateUrl: './ui-pagination.component.html'
})
export class UiPaginationComponent {
  /** Rows surviving search and filters, i.e. what is being paged. */
  @Input({ required: true }) total = 0;
  @Input() page = 1;
  @Input() pageSize = PAGE_SIZE_OPTIONS[0];
  /** Names the rows in the range read-out, e.g. "datasets". */
  @Input() noun = 'rows';

  @Output() pageChange = new EventEmitter<number>();
  @Output() pageSizeChange = new EventEmitter<number>();

  readonly sizeOptions: SelectOption[] =
    PAGE_SIZE_OPTIONS.map(size => ({ value: size, label: `${size}` }));

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  get firstRow(): number {
    return this.total ? (this.page - 1) * this.pageSize + 1 : 0;
  }

  get lastRow(): number {
    return Math.min(this.page * this.pageSize, this.total);
  }

  get hasPrevious(): boolean {
    return this.page > 1;
  }

  get hasNext(): boolean {
    return this.page < this.totalPages;
  }

  previous(): void {
    if (this.hasPrevious) {
      this.pageChange.emit(this.page - 1);
    }
  }

  next(): void {
    if (this.hasNext) {
      this.pageChange.emit(this.page + 1);
    }
  }

  onSizeChange(value: string | number | null): void {
    const size = Number(value);
    if (size) {
      this.pageSizeChange.emit(size);
    }
  }
}
