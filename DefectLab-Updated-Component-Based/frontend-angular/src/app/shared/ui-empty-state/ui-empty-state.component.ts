import { Component, Input } from '@angular/core';
import { UiIconName } from '../ui-icon/ui-icon.component';

/**
 * Standard empty state card across DefectLab catalog, prediction, comparison and report screens.
 * Replaces repetitive inline empty card markup and SVGs.
 */
@Component({
  selector: 'ui-empty-state',
  standalone: false,
  templateUrl: './ui-empty-state.component.html'
})
export class UiEmptyStateComponent {
  @Input() icon: UiIconName = 'database';
  @Input() title = '';
  @Input() description = '';
}
