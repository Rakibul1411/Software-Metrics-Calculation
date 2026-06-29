import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { MetricsExtractionComponent } from './features/metrics-extraction/metrics-extraction.component';
import { PredictionComponent } from './features/prediction/prediction.component';

@NgModule({
  declarations: [
    MetricsExtractionComponent,
    PredictionComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule
  ],
  bootstrap: []
})
export class AppModule { }
