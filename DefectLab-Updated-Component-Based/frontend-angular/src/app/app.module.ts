import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { AuthPageComponent } from './features/auth/auth-page.component';
import { AnalyzeComponent } from './features/analysis/analyze.component';
import { AccountComponent } from './features/account/account.component';
import { ComparisonsComponent } from './features/comparisons/comparisons.component';
import { DatasetsComponent } from './features/datasets/datasets.component';
import { OverviewComponent } from './features/dashboard/overview.component';
import { PredictionsComponent } from './features/predictions/predictions.component';
import { ReportDetailComponent } from './features/reports/report-detail.component';
import { ReportsComponent } from './features/reports/reports.component';
import { ShellComponent } from './features/shell/shell.component';
import { UiBadgeComponent } from './shared/ui-badge.component';
import { UiButtonComponent } from './shared/ui-button.component';
import { UiCardComponent } from './shared/ui-card.component';
import { UiIconComponent } from './shared/ui-icon.component';
import { UiFilePickerComponent } from './shared/ui-file-picker.component';
import { UiMetricCardComponent } from './shared/ui-metric-card.component';
import { UiStateComponent } from './shared/ui-state.component';

@NgModule({
  declarations: [
    UiIconComponent,
    UiButtonComponent,
    UiCardComponent,
    UiBadgeComponent,
    UiFilePickerComponent,
    UiMetricCardComponent,
    UiStateComponent,
    AppComponent,
    AuthPageComponent,
    ShellComponent,
    OverviewComponent,
    AnalyzeComponent,
    DatasetsComponent,
    PredictionsComponent,
    ComparisonsComponent,
    ReportsComponent,
    ReportDetailComponent,
    AccountComponent
  ],
  imports: [BrowserModule, HttpClientModule, FormsModule, AppRoutingModule],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {}
