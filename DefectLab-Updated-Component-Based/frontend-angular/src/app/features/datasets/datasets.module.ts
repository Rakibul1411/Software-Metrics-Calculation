import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { DatasetCreateComponent } from './dataset-create.component';
import { DatasetDetailComponent } from './dataset-detail.component';
import { DatasetsComponent } from './datasets.component';

@NgModule({
  declarations: [
    DatasetsComponent,
    DatasetCreateComponent,
    DatasetDetailComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    DatasetsComponent,
    DatasetCreateComponent,
    DatasetDetailComponent
  ]
})
export class DatasetsModule {}
