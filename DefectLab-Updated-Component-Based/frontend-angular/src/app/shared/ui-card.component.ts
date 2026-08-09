import { Component, Input } from '@angular/core';

/**
 * Section container. `title` renders the heading row; anything projected into
 * [actions] is right-aligned beside it. Omitting the title gives a bare panel.
 */
@Component({
  selector: 'ui-card',
  standalone: false,
  templateUrl: './ui-card.component.html'
})
export class UiCardComponent {
  @Input() heading?: string;
}
