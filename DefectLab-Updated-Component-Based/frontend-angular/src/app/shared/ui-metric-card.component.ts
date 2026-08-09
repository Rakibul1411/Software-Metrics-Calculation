import { Component, Input } from '@angular/core';

/** Shared numeric/text summary tile used by dataset, prediction and report views. */
@Component({
  selector: 'ui-metric-card',
  standalone: false,
  template: `
    <article
      [class.dl-card]="variant === 'stat'"
      [class.dl-stat]="variant === 'stat'"
      [class.dl-data-card]="variant === 'metric'">
      <span [class.dl-stat-label]="variant === 'stat'">{{ label }}</span>
      <strong [class.dl-stat-value]="variant === 'stat'">{{ value ?? '—' }}</strong>
    </article>
  `
})
export class UiMetricCardComponent {
  @Input() label = '';
  @Input() value: string | number | null | undefined = '—';
  @Input() variant: 'metric' | 'stat' = 'metric';
}
