import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { AuthPageComponent } from './features/auth/auth-page.component';
import { AnalyzeComponent } from './features/analysis/analyze.component';
import { AccountComponent } from './features/account/account.component';
import { ComparisonCreateComponent } from './features/comparisons/comparison-create.component';
import { ComparisonDetailComponent } from './features/comparisons/comparison-detail.component';
import { ComparisonsComponent } from './features/comparisons/comparisons.component';
import { DatasetCreateComponent } from './features/datasets/dataset-create.component';
import { DatasetDetailComponent } from './features/datasets/dataset-detail.component';
import { DatasetsComponent } from './features/datasets/datasets.component';
import { OverviewComponent } from './features/dashboard/overview.component';
import { PredictionCreateComponent } from './features/predictions/prediction-create.component';
import { PredictionDetailComponent } from './features/predictions/prediction-detail.component';
import { PredictionsComponent } from './features/predictions/predictions.component';
import { ReportDetailComponent } from './features/reports/report-detail.component';
import { ReportsComponent } from './features/reports/reports.component';
import { ShellComponent } from './features/shell/shell.component';
import { UiBadgeComponent } from './shared/ui-badge/ui-badge.component';
import { UiButtonComponent } from './shared/ui-button/ui-button.component';
import { UiCardComponent } from './shared/ui-card/ui-card.component';
import { UiConfirmDialogComponent } from './shared/ui-confirm-dialog/ui-confirm-dialog.component';
import { UiDeleteActionComponent } from './shared/ui-delete-action/ui-delete-action.component';
import { UiDetailFieldsComponent } from './shared/ui-detail-fields/ui-detail-fields.component';
import { UiDownloadMenuComponent } from './shared/ui-download-menu/ui-download-menu.component';
import { UiIconComponent } from './shared/ui-icon/ui-icon.component';
import { UiFilePickerComponent } from './shared/ui-file-picker/ui-file-picker.component';
import { UiInputComponent } from './shared/ui-input/ui-input.component';
import { UiMetricCardComponent } from './shared/ui-metric-card/ui-metric-card.component';
import { UiRadioGroupComponent } from './shared/ui-radio-group/ui-radio-group.component';
import { UiSearchToggleComponent } from './shared/ui-search-toggle/ui-search-toggle.component';
import { UiSelectComponent } from './shared/ui-select/ui-select.component';
import { UiStateComponent } from './shared/ui-state/ui-state.component';
import { UiTableCellDirective } from './shared/ui-table/ui-table-cell.directive';
import { UiTableComponent } from './shared/ui-table/ui-table.component';
import { UiToastComponent } from './shared/ui-toast/ui-toast.component';

@NgModule({
  declarations: [
    UiIconComponent,
    UiButtonComponent,
    UiCardComponent,
    UiBadgeComponent,
    UiFilePickerComponent,
    UiMetricCardComponent,
    UiStateComponent,
    UiConfirmDialogComponent,
    UiDeleteActionComponent,
    UiDetailFieldsComponent,
    UiDownloadMenuComponent,
    UiInputComponent,
    UiRadioGroupComponent,
    UiSearchToggleComponent,
    UiSelectComponent,
    UiTableComponent,
    UiTableCellDirective,
    UiToastComponent,
    AppComponent,
    AuthPageComponent,
    ShellComponent,
    OverviewComponent,
    AnalyzeComponent,
    DatasetsComponent,
    DatasetCreateComponent,
    DatasetDetailComponent,
    PredictionsComponent,
    PredictionCreateComponent,
    PredictionDetailComponent,
    ComparisonsComponent,
    ComparisonCreateComponent,
    ComparisonDetailComponent,
    ReportsComponent,
    ReportDetailComponent,
    AccountComponent
  ],
  imports: [BrowserModule, HttpClientModule, FormsModule, AppRoutingModule],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {}
