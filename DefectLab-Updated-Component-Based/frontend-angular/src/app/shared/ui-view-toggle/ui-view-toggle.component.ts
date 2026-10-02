import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ViewMode = 'table' | 'treemap';

/**
 * Reusable view mode toggle between Tabular View and Architectural Treemap.
 */
@Component({
  selector: 'ui-view-toggle',
  standalone: false,
  templateUrl: './ui-view-toggle.component.html'
})
export class UiViewToggleComponent {
  @Input() mode: ViewMode = 'table';
  @Input() tableTitle = 'Tabular prediction / metric viewer';
  @Input() treemapTitle = 'Architectural Hotspot Treemap';
  @Output() readonly modeChange = new EventEmitter<ViewMode>();

  selectMode(target: ViewMode): void {
    if (this.mode !== target) {
      this.mode = target;
      this.modeChange.emit(target);
    }
  }
}
