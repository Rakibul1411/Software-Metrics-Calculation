import { Component, Input } from '@angular/core';

/** Consistent loading, empty and feedback states across feature pages. */
@Component({
  selector: 'ui-state',
  standalone: false,
  template: `
    <div *ngIf="kind === 'loading'; else messageState"
         class="dl-loading" role="status" aria-live="polite">
      <span class="dl-spinner" aria-hidden="true"></span>{{ message }}
    </div>
    <ng-template #messageState>
      <div [class]="classes" [attr.role]="kind === 'error' ? 'alert' : 'status'">
        {{ message }}
      </div>
    </ng-template>
  `
})
export class UiStateComponent {
  @Input() kind: 'loading' | 'empty' | 'error' | 'success' | 'info' = 'info';
  @Input() message = '';

  get classes(): string {
    if (this.kind === 'empty') return 'dl-empty';
    if (this.kind === 'error') return 'dl-alert dl-alert-error';
    if (this.kind === 'success') return 'dl-alert dl-alert-success';
    return 'dl-alert';
  }
}
