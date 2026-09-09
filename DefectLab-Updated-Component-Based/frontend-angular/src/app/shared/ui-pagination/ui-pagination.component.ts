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

  get singularNoun(): string {
    if (this.noun.endsWith('ies')) {
      return this.noun.slice(0, -3) + 'y';
    }
    if (this.noun.endsWith('es')) {
      return this.noun.slice(0, -2);
    }
    if (this.noun.endsWith('s')) {
      return this.noun.slice(0, -1);
    }
    return this.noun;
  }

  get rangeText(): string {
    if (this.total === 0) {
      return `No ${this.noun}`;
    }
    if (this.total === 1) {
      return `Showing 1 ${this.singularNoun}`;
    }
    if (this.total <= this.pageSize) {
      return `Showing all ${this.total} ${this.noun}`;
    }
    return `Showing ${this.firstRow}–${this.lastRow} of ${this.total} ${this.noun}`;
  }

  get hasPrevious(): boolean {
    return this.page > 1;
  }

  get hasNext(): boolean {
    return this.page < this.totalPages;
  }

  get pages(): (number | string)[] {
    const total = this.totalPages;
    const current = this.page;
    if (total <= 5) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    if (current <= 3) {
      return [1, 2, 3, '…', total];
    }
    if (current >= total - 2) {
      return [1, '…', total - 2, total - 1, total];
    }
    return [1, '…', current, '…', total];
  }

  isNumber(val: number | string): boolean {
    return typeof val === 'number';
  }

  goToPage(p: number | string): void {
    if (typeof p === 'number' && p >= 1 && p <= this.totalPages && p !== this.page) {
      this.pageChange.emit(p);
    }
  }

  first(): void {
    if (this.hasPrevious) {
      this.pageChange.emit(1);
    }
  }

  last(): void {
    if (this.hasNext) {
      this.pageChange.emit(this.totalPages);
    }
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
