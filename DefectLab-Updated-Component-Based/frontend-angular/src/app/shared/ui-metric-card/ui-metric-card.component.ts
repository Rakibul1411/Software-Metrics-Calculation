import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Shared card tile: a numeric/text summary readout, optionally clickable.
 * Every card in the app — dashboard stats, dataset/report readouts, and the
 * Analyze page's mode selector — renders from this one label/value markup,
 * so no two cards can drift to different sizes.
 */
@Component({
  selector: 'ui-metric-card',
  standalone: false,
  template: `
    <article
        [class.dl-card]="variant === 'stat'"
        [class.dl-stat]="variant === 'stat'"
        [class.dl-action-card]="clickable"
        [class.dl-data-card]="variant === 'metric' || selected"
        [class.dl-disabled]="disabled"
        (click)="onPress()">
      <span [class.dl-stat-label]="variant === 'stat'">{{ label }}</span>
      <strong [class.dl-stat-value]="variant === 'stat'" [class.dl-stat-value-sm]="compact">{{ value ?? '—' }}</strong>
    </article>
  `
})
export class UiMetricCardComponent {
  @Input() label = '';
  @Input() value: string | number | null | undefined = '—';
  @Input() variant: 'metric' | 'stat' = 'metric';
  /** Adds pointer/hover affordance and makes `pressed` fire on click. */
  @Input() clickable = false;
  /** Value reads as a short heading rather than a number — smaller, non-tabular. */
  @Input() compact = false;
  /** Shows the selected accent frame. */
  @Input() selected = false;
  /** Blocks the press output. */
  @Input() disabled = false;
  @Output() pressed = new EventEmitter<void>();

  onPress(): void {
    if (!this.clickable || this.disabled) return;
    this.pressed.emit();
  }
}
