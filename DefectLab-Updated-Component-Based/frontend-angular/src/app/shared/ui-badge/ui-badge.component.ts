import { Component, Input } from '@angular/core';

export type BadgeTone =
  | 'blue'
  | 'green'
  | 'amber'
  | 'red'
  | 'purple'
  | 'promise'
  | 'aeeem'
  | string;

/** Status pill. `tone` maps to the shared badge colours. */
@Component({
  selector: 'ui-badge',
  standalone: false,
  template: `<span class="dl-badge" [ngClass]="badgeClass"><ng-content></ng-content></span>`
})
export class UiBadgeComponent {
  @Input() tone: BadgeTone = 'blue';

  get badgeClass(): string {
    const t = (this.tone || 'blue').toLowerCase();
    return `dl-badge-${t}`;
  }
}
