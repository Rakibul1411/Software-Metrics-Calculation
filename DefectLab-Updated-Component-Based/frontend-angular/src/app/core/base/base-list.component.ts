import { Directive, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { PAGE_SIZE_OPTIONS } from '../../shared/ui-pagination/ui-pagination.component';
import { BaseComponent } from './base.component';

/**
 * A screen that loads a collection once and filters it client-side.
 *
 * Subclasses supply the request ({@link fetch}) and, when the page has a
 * search box or dropdown filters, the row predicate ({@link matches}).
 * Loading state, reload-on-demand and the `filtered` view are shared.
 */
@Directive()
export abstract class BaseListComponent<T> extends BaseComponent implements OnInit {
  rows: T[] = [];
  loading = true;
  page = 1;
  pageSize = PAGE_SIZE_OPTIONS[0];

  private query = '';

  /** Typing narrows the list, so the user belongs back on its first page. */
  get search(): string {
    return this.query;
  }

  set search(value: string) {
    this.query = value;
    this.page = 1;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.watch(this.fetch()).subscribe({
      next: rows => {
        this.rows = rows;
        this.page = 1;
        this.loading = false;
      },
      error: error => {
        this.loading = false;
        this.reportLocalError(error);
      }
    });
  }

  /** Rows surviving the search box and any subclass filters. */
  get filtered(): T[] {
    const query = this.query.trim().toLowerCase();
    return this.rows.filter(row => this.matches(row, query));
  }

  /** The slice of {@link filtered} the current page shows. */
  get pageRows(): T[] {
    const filtered = this.filtered;
    const start = (this.currentPage(filtered.length) - 1) * this.pageSize;
    return filtered.slice(start, start + this.pageSize);
  }

  onPageChange(page: number): void {
    this.page = page;
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 1;
  }

  /** Subclass filters call this so narrowing never strands the user on a page
      that no longer exists. */
  protected resetPage(): void {
    this.page = 1;
  }

  /** Clamps a page the filters may have just emptied. */
  private currentPage(total: number): number {
    const pages = Math.max(1, Math.ceil(total / this.pageSize));
    if (this.page > pages) {
      this.page = pages;
    }
    return this.page;
  }

  /** The list request this screen (re)loads from. */
  protected abstract fetch(): Observable<T[]>;

  /** Narrows the list. `query` arrives already trimmed and lowercased. */
  protected matches(_row: T, _query: string): boolean {
    return true;
  }
}
