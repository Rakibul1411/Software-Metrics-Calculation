import { Component, Input } from '@angular/core';

/** Status pill. `tone` maps to the shared badge colours. */
@Component({
  selector: 'ui-badge',
  standalone: false,
  template: `<span class="dl-badge" [class]="'dl-badge-' + tone"><ng-content></ng-content></span>`
})
export class UiBadgeComponent {
  @Input() tone: 'blue' | 'green' | 'amber' | 'red' = 'blue';
}
