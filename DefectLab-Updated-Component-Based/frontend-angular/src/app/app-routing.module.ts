import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard, GuestGuard } from './core/guards/auth.guard';
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
  { path: 'login', component: AuthPageComponent, canActivate: [GuestGuard] },
  {
    path: '',
    component: ShellComponent,
    canActivate: [AuthGuard],
    children: [
      { path: 'overview', component: OverviewComponent, title: 'Dashboard — DefectLab' },
      { path: 'analyze', component: AnalyzeComponent, title: 'Analyze Source — DefectLab' },
      { path: 'datasets/new', component: DatasetCreateComponent, title: 'Add Dataset — DefectLab' },
      { path: 'datasets/:id', component: DatasetDetailComponent, title: 'Dataset Details — DefectLab' },
      { path: 'datasets', component: DatasetsComponent, title: 'Metric Storage — DefectLab' },
      { path: 'predictions/new', component: PredictionCreateComponent, title: 'Run Prediction — DefectLab' },
      { path: 'predictions/:id', component: PredictionDetailComponent, title: 'Prediction Run Details — DefectLab' },
      { path: 'predictions', component: PredictionsComponent, title: 'Predictions — DefectLab' },
      {
        path: 'metric-comparisons/new',
        component: ComparisonCreateComponent,
        title: 'New Comparison — DefectLab'
      },
      {
        path: 'metric-comparisons/:id',
        component: ComparisonDetailComponent,
        title: 'Metric Comparison Details — DefectLab'
      },
      {
        path: 'metric-comparisons',
        component: ComparisonsComponent,
        title: 'Compare Metrics — DefectLab'
      },
      {
        path: 'reports/:groupKey',
        component: ReportDetailComponent,
        title: 'Prediction Report Details — DefectLab'
      },
      { path: 'reports', component: ReportsComponent, title: 'Prediction Reports — DefectLab' },
      { path: 'account', component: AccountComponent, title: 'Account Settings — DefectLab' },
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
