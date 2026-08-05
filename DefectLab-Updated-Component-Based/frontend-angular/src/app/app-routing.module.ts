import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard, GuestGuard } from './core/guards/auth.guard';
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

const routes: Routes = [
  { path: 'login', component: AuthPageComponent, canActivate: [GuestGuard] },
  {
    path: '',
    component: ShellComponent,
    canActivate: [AuthGuard],
    children: [
      { path: 'overview', component: OverviewComponent, title: 'Dashboard' },
      { path: 'analyze', component: AnalyzeComponent, title: 'Analyze source' },
      { path: 'datasets', component: DatasetsComponent, title: 'Metric storage' },
      { path: 'predictions', component: PredictionsComponent, title: 'Predictions' },
      {
        path: 'metric-comparisons',
        component: ComparisonsComponent,
        title: 'Compare Metrics'
      },
      {
        path: 'reports/:groupKey',
        component: ReportDetailComponent,
        title: 'Prediction report details'
      },
      { path: 'reports', component: ReportsComponent, title: 'Reports' },
      { path: 'account', component: AccountComponent, title: 'Account' },
      { path: '', pathMatch: 'full', redirectTo: 'overview' }
    ]
  },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
