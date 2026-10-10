import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ViewMode = 'table' | 'treemap' | 'tsne';

/**
 * Reusable view mode toggle between Tabular View, Architectural Treemap, and CORAL t-SNE.
 */
@Component({
  selector: 'ui-view-toggle',
  standalone: false,
  templateUrl: './ui-view-toggle.component.html'
})
export class UiViewToggleComponent {
  @Input() mode: ViewMode = 'table';
  @Input() showCoralTsne = false;
  @Input() tableTitle = 'Tabular prediction / metric viewer';
  @Input() treemapTitle = 'Architectural Hotspot Treemap';
  @Input() tsneTitle = 'CORAL Domain Adaptation t-SNE Projection';
  @Output() readonly modeChange = new EventEmitter<ViewMode>();

  selectMode(target: ViewMode): void {
    if (this.mode !== target) {
      this.mode = target;
      this.modeChange.emit(target);
    }
  }
}
