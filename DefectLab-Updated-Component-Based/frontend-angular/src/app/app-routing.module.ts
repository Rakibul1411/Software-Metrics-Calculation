import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
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

const routes: Routes = [
  { path: 'login', component: AuthPageComponent, canActivate: [guestGuard], title: 'Sign In — DefectLab' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      // Primary meaningful domain routes
      { path: 'dashboard', component: OverviewComponent, title: 'Dashboard — DefectLab' },
      { path: 'source-analysis', component: AnalyzeComponent, title: 'Source Analysis — DefectLab' },
      { path: 'metric-storage/new', component: DatasetCreateComponent, title: 'Add Dataset — DefectLab' },
      { path: 'metric-storage/:id', component: DatasetDetailComponent, title: 'Dataset Details — DefectLab' },
      { path: 'metric-storage', component: DatasetsComponent, title: 'Metric Storage — DefectLab' },
      { path: 'defect-predictions/new', component: PredictionCreateComponent, title: 'Run Prediction — DefectLab' },
      { path: 'defect-predictions/:id', component: PredictionDetailComponent, title: 'Prediction Run Details — DefectLab' },
      { path: 'defect-predictions', component: PredictionsComponent, title: 'Defect Predictions — DefectLab' },
      { path: 'metric-comparisons/new', component: ComparisonCreateComponent, title: 'New Comparison — DefectLab' },
      { path: 'metric-comparisons/:id', component: ComparisonDetailComponent, title: 'Metric Comparison Details — DefectLab' },
      { path: 'metric-comparisons', component: ComparisonsComponent, title: 'Metric Comparisons — DefectLab' },
      { path: 'prediction-reports/:groupKey', component: ReportDetailComponent, title: 'Prediction Report Details — DefectLab' },
      { path: 'prediction-reports', component: ReportsComponent, title: 'Prediction Reports — DefectLab' },
      { path: 'account-settings', component: AccountComponent, title: 'Account Settings — DefectLab' },

      // Aliases and backwards-compatible redirects
      { path: 'overview', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'analyze', redirectTo: 'source-analysis', pathMatch: 'full' },
      { path: 'datasets/new', redirectTo: 'metric-storage/new', pathMatch: 'full' },
      { path: 'datasets/:id', redirectTo: 'metric-storage/:id' },
      { path: 'datasets', redirectTo: 'metric-storage', pathMatch: 'full' },
      { path: 'predictions/new', redirectTo: 'defect-predictions/new', pathMatch: 'full' },
      { path: 'predictions/:id', redirectTo: 'defect-predictions/:id' },
      { path: 'predictions', redirectTo: 'defect-predictions', pathMatch: 'full' },
      { path: 'comparisons/new', redirectTo: 'metric-comparisons/new', pathMatch: 'full' },
      { path: 'comparisons/:id', redirectTo: 'metric-comparisons/:id' },
      { path: 'comparisons', redirectTo: 'metric-comparisons', pathMatch: 'full' },
      { path: 'reports/:groupKey', redirectTo: 'prediction-reports/:groupKey' },
      { path: 'reports', redirectTo: 'prediction-reports', pathMatch: 'full' },
      { path: 'account', redirectTo: 'account-settings', pathMatch: 'full' },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' }
    ]
  },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
