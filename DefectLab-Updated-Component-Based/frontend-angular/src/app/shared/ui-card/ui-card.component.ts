import { Component, Input } from '@angular/core';

/**
 * Section container. `heading` renders the title row, `subtitle` an optional
 * line beneath it, and anything projected into [actions] is right-aligned
 * beside them. Omitting the heading gives a bare panel.
 */
@Component({
  selector: 'ui-card',
  standalone: false,
  templateUrl: './ui-card.component.html'
})
export class UiCardComponent {
  @Input() heading?: string;
  @Input() subtitle?: string;
  /** Extra class(es) applied to the root section, alongside dl-card. */
  @Input() cardClass?: string;
}
