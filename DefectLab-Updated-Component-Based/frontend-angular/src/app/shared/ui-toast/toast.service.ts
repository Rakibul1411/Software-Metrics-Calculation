import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Toast, ToastKind } from './toast.model';

/** App-wide toast queue, rendered by <ui-toast> in the top-right corner. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly toastsSubject = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this.toastsSubject.asObservable();

  private nextId = 1;

  show(message: string, kind: ToastKind = 'info', durationMs = 4000): void {
    // One failing screen can fire several requests that fail the same way —
    // opening a dataset loads the record and its preview — and stacking the
    // identical message twice reads as two separate problems.
    if (this.toastsSubject.value.some(open => open.message === message && open.kind === kind)) {
      return;
    }
    const toast: Toast = { id: this.nextId++, message, kind };
    this.toastsSubject.next([...this.toastsSubject.value, toast]);
    setTimeout(() => this.dismiss(toast.id), durationMs);
  }

  success(message: string, durationMs?: number): void {
    this.show(message, 'success', durationMs);
  }

  error(message: string, durationMs?: number): void {
    this.show(message, 'error', durationMs);
  }

  info(message: string, durationMs?: number): void {
    this.show(message, 'info', durationMs);
  }

  dismiss(id: number): void {
    this.toastsSubject.next(this.toastsSubject.value.filter(toast => toast.id !== id));
  }
}
