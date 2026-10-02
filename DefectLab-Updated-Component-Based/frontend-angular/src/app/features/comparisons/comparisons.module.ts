import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { ComparisonCreateComponent } from './comparison-create.component';
import { ComparisonDetailComponent } from './comparison-detail.component';
import { ComparisonsComponent } from './comparisons.component';

@NgModule({
  declarations: [
    ComparisonsComponent,
    ComparisonCreateComponent,
    ComparisonDetailComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    ComparisonsComponent,
    ComparisonCreateComponent,
    ComparisonDetailComponent
  ]
})
export class ComparisonsModule {}
