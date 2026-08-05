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

@NgModule({
  declarations: [
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
