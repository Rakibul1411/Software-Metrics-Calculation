import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { AnalyzeComponent } from './analyze.component';

@NgModule({
  declarations: [
    AnalyzeComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    AnalyzeComponent
  ]
})
export class AnalysisModule {}
