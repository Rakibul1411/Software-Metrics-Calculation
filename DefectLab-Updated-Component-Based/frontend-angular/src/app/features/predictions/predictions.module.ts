import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { PredictionCreateComponent } from './prediction-create.component';
import { PredictionDetailComponent } from './prediction-detail.component';
import { PredictionsComponent } from './predictions.component';

@NgModule({
  declarations: [
    PredictionsComponent,
    PredictionCreateComponent,
    PredictionDetailComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    PredictionsComponent,
    PredictionCreateComponent,
    PredictionDetailComponent
  ]
})
export class PredictionsModule {}
