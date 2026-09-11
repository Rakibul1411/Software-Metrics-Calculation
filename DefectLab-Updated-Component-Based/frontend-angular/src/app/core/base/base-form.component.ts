import { Directive } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseComponent } from './base.component';

/** Optional hooks around a submit; errors already surface as global toasts. */
export interface SubmitOptions<T> {
  success: string | ((result: T) => string);
  redirect?: unknown[];
  onSuccess?: (result: T) => void;
  /** Runs after success and after failure -- for releasing external state. */
  onSettled?: () => void;
}

/**
 * A screen that submits one request at a time.
 *
 * {@link submitWith} owns the guard-set-busy / toast / redirect / release
 * sequence every create page was repeating by hand, which is also what kept
 * the `busy` flag correct on the error path.
 */
@Directive()
export abstract class BaseFormComponent extends BaseComponent {
  busy = false;

  /** Where the cancel/back control returns to; empty on standalone forms. */
  protected readonly listRoute: unknown[] = [];

  back(): void {
    if (this.listRoute.length) {
      this.navigateTo(this.listRoute);
    }
  }

  protected submitWith<T>(work$: Observable<T>, options: SubmitOptions<T>): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.watch(work$).subscribe({
      next: result => {
        this.busy = false;
        options.onSettled?.();
        options.onSuccess?.(result);
        const successMessage = typeof options.success === 'function'
          ? options.success(result)
          : options.success;
        if (successMessage) {
          this.toast.success(successMessage);
        }
        if (options.redirect) {
          this.navigateTo(options.redirect);
        }
      },
      error: () => {
        this.busy = false;
        options.onSettled?.();
      }
    });
  }
}
