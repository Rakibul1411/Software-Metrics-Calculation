import { Directive, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { BaseComponent } from './base.component';

/**
 * A screen that resolves one record from a route parameter.
 *
 * Reading and validating the parameter, the loading flag, and the
 * back/after-delete navigation are identical on every detail page, so they
 * live here; subclasses declare where they came from ({@link listRoute})
 * and how to load the record ({@link fetch}).
 */
@Directive()
export abstract class BaseDetailComponent<T, K = number>
  extends BaseComponent
  implements OnInit {
  item: T | null = null;
  loading = true;
  /** The record could not be resolved — the screen shows why instead of a blank card. */
  failed = false;

  protected readonly route = inject(ActivatedRoute);

  /** Where back and post-delete navigation land. */
  protected abstract readonly listRoute: unknown[];
  /** Shown when the URL carries no usable identifier. */
  protected abstract readonly missingMessage: string;
  /** Shown when the identifier is well-formed but resolves to nothing. */
  protected readonly notFoundMessage: string =
    'This record is not available. It may have been deleted — use the link above to go back.';
  protected readonly routeParam: string = 'id';

  ngOnInit(): void {
    const key = this.readRouteKey();
    if (key === null) {
      this.toast.error(this.missingMessage);
      this.loading = false;
      this.failed = true;
      return;
    }
    this.load(key);
  }

  load(key: K): void {
    this.loading = true;
    this.failed = false;
    this.watch(this.fetch(key)).subscribe({
      next: item => {
        this.item = item;
        this.loading = false;
      },
      error: error => {
        this.loading = false;
        this.failed = true;
        this.reportLocalError(error);
      }
    });
  }

  back(): void {
    this.navigateTo(this.listRoute);
  }

  onDeleted(): void {
    this.navigateTo(this.listRoute);
  }

  /** Loads the record this screen displays. */
  protected abstract fetch(key: K): Observable<T>;

  /** Numeric ids by default; string-keyed screens override this. */
  protected readRouteKey(): K | null {
    const raw = this.route.snapshot.paramMap.get(this.routeParam);
    const id = Number(raw);
    return raw && id ? (id as unknown as K) : null;
  }
}
