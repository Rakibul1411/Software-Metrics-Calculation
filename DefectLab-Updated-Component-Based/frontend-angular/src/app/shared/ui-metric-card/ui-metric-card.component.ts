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
  templateUrl: './ui-metric-card.component.html'
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
