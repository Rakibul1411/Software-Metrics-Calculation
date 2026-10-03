import { Directive, OnInit } from '@angular/core';
import { Observable, timer } from 'rxjs';
import { PAGE_SIZE_OPTIONS } from '../../shared/ui-pagination/ui-pagination.component';
import { BaseComponent } from './base.component';

/**
 * A screen that loads a collection once and filters it client-side, with
 * silent automatic background refresh to keep the UI in sync without
 * disrupting user interactions, active pagination, search input or scrolling.
 */
@Directive()
export abstract class BaseListComponent<T> extends BaseComponent implements OnInit {
  rows: T[] = [];
  loading = true;
  page = 1;
  pageSize = PAGE_SIZE_OPTIONS[0];

  protected autoRefreshEnabled = true;
  protected autoRefreshIntervalMs = 5000;

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
    this.initAutoRefresh();
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

  /**
   * Background silent auto-refresh: polls internally to sync records
   * without triggering loading spinners, without resetting the user's active page,
   * without disturbing search query / dropdown state, and without toast interruptions.
   */
  protected initAutoRefresh(intervalMs = this.autoRefreshIntervalMs): void {
    if (!this.autoRefreshEnabled) {
      return;
    }
    this.watch(timer(intervalMs, intervalMs)).subscribe(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
        return;
      }
      if (this.loading) {
        return;
      }
      this.watch(this.fetch()).subscribe({
        next: freshRows => {
          if (!this.areRowsEqual(this.rows, freshRows)) {
            this.rows = freshRows;
            this.clampPage();
          }
        },
        error: () => {
          // Silently ignore background polling errors to protect usability
        }
      });
    });
  }

  protected areRowsEqual(current: T[], incoming: T[]): boolean {
    if (current === incoming) return true;
    if (!current || !incoming) return false;
    if (current.length !== incoming.length) return false;
    try {
      return JSON.stringify(current) === JSON.stringify(incoming);
    } catch {
      return false;
    }
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

  private clampPage(): void {
    const total = this.filtered.length;
    const maxPage = Math.max(1, Math.ceil(total / this.pageSize));
    if (this.page > maxPage) {
      this.page = maxPage;
    }
  }

  /** The list request this screen (re)loads from. */
  protected abstract fetch(): Observable<T[]>;

  /** Narrows the list. `query` arrives already trimmed and lowercased. */
  protected matches(_row: T, _query: string): boolean {
    return true;
  }
}
