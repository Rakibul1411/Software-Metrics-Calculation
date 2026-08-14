import { Component, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { Toast, ToastKind } from './toast.model';
import { ToastService } from './toast.service';
import { UiIconName } from '../ui-icon/ui-icon.component';

const ICONS: Record<ToastKind, UiIconName> = {
  success: 'check',
  error: 'warning',
  info: 'info'
};

/** Renders the app-wide toast queue fixed to the top-right corner. */
@Component({
  selector: 'ui-toast',
  standalone: false,
  templateUrl: './ui-toast.component.html'
})
export class UiToastComponent implements OnDestroy {
  toasts: Toast[] = [];
  private readonly subscription: Subscription;

  constructor(private readonly toastService: ToastService) {
    this.subscription = this.toastService.toasts$.subscribe(toasts => this.toasts = toasts);
  }

  icon(kind: ToastKind): UiIconName {
    return ICONS[kind];
  }

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
