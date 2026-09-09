import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Directive, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ToastService } from '../../shared/ui-toast/toast.service';

/**
 * Root of the screen hierarchy: the actions every DefectLab page repeats.
 *
 * Dependencies are resolved with `inject()` rather than constructor
 * parameters so subclasses never have to thread them through their own
 * constructors -- adding a base dependency later touches this file only.
 */
@Directive()
export abstract class BaseComponent {
  protected readonly destroyRef = inject(DestroyRef);
  protected readonly router = inject(Router);
  protected readonly toast = inject(ToastService);

  /**
   * Ties a stream to this component's lifetime. Every subscription in a
   * feature screen goes through here, so navigating away can never leave a
   * late response writing into a destroyed component.
   */
  protected watch<T>(source$: Observable<T>): Observable<T> {
    return source$.pipe(takeUntilDestroyed(this.destroyRef));
  }

  protected navigateTo(commands: unknown[]): void {
    void this.router.navigate(commands);
  }

  /** Bound by every download control; the label names what is being saved. */
  downloadStarted(label = 'PDF report'): void {
    this.toast.info(`${label} download started.`);
  }

  /**
   * Surfaces a failure the global HTTP interceptor will not.
   *
   * Request failures are already toasted centrally, so re-reporting them
   * here would show the same message twice; errors a facade raises itself
   * (an unusable route key, an unsatisfiable view model) have no other way
   * to reach the user.
   */
  protected reportLocalError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      return;
    }
    const message = error instanceof Error ? error.message : '';
    if (message) {
      this.toast.error(message);
    }
  }
}
