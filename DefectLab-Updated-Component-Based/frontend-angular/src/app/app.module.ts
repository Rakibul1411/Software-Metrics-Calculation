import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { DatePipe } from '@angular/common';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { ErrorToastInterceptor } from './core/interceptors/error-toast.interceptor';
import { SharedModule } from './shared/shared.module';

import { AccountModule } from './features/account/account.module';
import { AnalysisModule } from './features/analysis/analysis.module';
import { AuthModule } from './features/auth/auth.module';
import { ComparisonsModule } from './features/comparisons/comparisons.module';
import { DashboardModule } from './features/dashboard/dashboard.module';
import { DatasetsModule } from './features/datasets/datasets.module';
import { PredictionsModule } from './features/predictions/predictions.module';
import { ReportsModule } from './features/reports/reports.module';
import { ShellModule } from './features/shell/shell.module';

/**
 * Root Application Module.
 * Adheres to official Angular Style Guide (Rule 04-06 to 04-10):
 * - Clean root orchestrator that declares only AppComponent.
 * - Imports core infrastructure, SharedModule, and dedicated Feature Modules.
 */
@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    SharedModule,
    ShellModule,
    AuthModule,
    DashboardModule,
    AnalysisModule,
    DatasetsModule,
    PredictionsModule,
    ComparisonsModule,
    ReportsModule,
    AccountModule
  ],
  providers: [
    provideHttpClient(withInterceptorsFromDi()),
    { provide: HTTP_INTERCEPTORS, useClass: ErrorToastInterceptor, multi: true },
    DatePipe
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
