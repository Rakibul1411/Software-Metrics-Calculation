import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { OverviewComponent } from './overview.component';

@NgModule({
  declarations: [
    OverviewComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    OverviewComponent
  ]
})
export class DashboardModule {}
