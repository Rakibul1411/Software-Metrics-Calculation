import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { UiBadgeComponent } from './ui-badge/ui-badge.component';
import { UiBarChartComponent } from './ui-bar-chart/ui-bar-chart.component';
import { UiButterflyGraphComponent } from './ui-butterfly-graph/ui-butterfly-graph.component';
import { UiButtonComponent } from './ui-button/ui-button.component';
import { UiCardComponent } from './ui-card/ui-card.component';
import { UiConfirmDialogComponent } from './ui-confirm-dialog/ui-confirm-dialog.component';
import { UiConfusionMatrixComponent } from './ui-confusion-matrix/ui-confusion-matrix.component';
import { UiDeleteActionComponent } from './ui-delete-action/ui-delete-action.component';
import { UiDetailFieldsComponent } from './ui-detail-fields/ui-detail-fields.component';
import { UiDownloadMenuComponent } from './ui-download-menu/ui-download-menu.component';
import { UiEmptyStateComponent } from './ui-empty-state/ui-empty-state.component';
import { UiFilePickerComponent } from './ui-file-picker/ui-file-picker.component';
import { UiIconComponent } from './ui-icon/ui-icon.component';
import { UiInputComponent } from './ui-input/ui-input.component';
import { UiMetricCardComponent } from './ui-metric-card/ui-metric-card.component';
import { UiPageHeaderComponent } from './ui-page-header/ui-page-header.component';
import { UiPaginationComponent } from './ui-pagination/ui-pagination.component';
import { UiRadioGroupComponent } from './ui-radio-group/ui-radio-group.component';
import { UiSearchBarComponent } from './ui-search-bar/ui-search-bar.component';
import { UiSearchToggleComponent } from './ui-search-toggle/ui-search-toggle.component';
import { UiSelectComponent } from './ui-select/ui-select.component';
import { UiStateComponent } from './ui-state/ui-state.component';
import { UiTableCellDirective } from './ui-table/ui-table-cell.directive';
import { UiTableComponent } from './ui-table/ui-table.component';
import { UiToastComponent } from './ui-toast/ui-toast.component';
import { UiTreemapComponent } from './ui-treemap/ui-treemap.component';
import { UiViewToggleComponent } from './ui-view-toggle/ui-view-toggle.component';

const SHARED_COMPONENTS = [
  UiIconComponent,
  UiButtonComponent,
  UiCardComponent,
  UiBadgeComponent,
  UiBarChartComponent,
  UiTreemapComponent,
  UiButterflyGraphComponent,
  UiFilePickerComponent,
  UiMetricCardComponent,
  UiStateComponent,
  UiEmptyStateComponent,
  UiPageHeaderComponent,
  UiViewToggleComponent,
  UiSearchBarComponent,
  UiConfusionMatrixComponent,
  UiConfirmDialogComponent,
  UiDeleteActionComponent,
  UiDetailFieldsComponent,
  UiDownloadMenuComponent,
  UiInputComponent,
  UiPaginationComponent,
  UiRadioGroupComponent,
  UiSearchToggleComponent,
  UiSelectComponent,
  UiTableComponent,
  UiTableCellDirective,
  UiToastComponent
];

/**
 * Standard Angular SharedModule.
 * Encapsulates all reusable UI components, directives, and pipes so feature
 * modules and the root AppModule can import them in a clean, modular way.
 */
@NgModule({
  declarations: [
    ...SHARED_COMPONENTS
  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule
  ],
  exports: [
    CommonModule,
    FormsModule,
    RouterModule,
    ...SHARED_COMPONENTS
  ]
})
export class SharedModule {}
