import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { ReportDetailComponent } from './report-detail.component';
import { ReportsComponent } from './reports.component';

@NgModule({
  declarations: [
    ReportsComponent,
    ReportDetailComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    ReportsComponent,
    ReportDetailComponent
  ]
})
export class ReportsModule {}
