import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { AuthPageComponent } from './auth-page.component';

@NgModule({
  declarations: [
    AuthPageComponent
  ],
  imports: [
    SharedModule
  ],
  exports: [
    AuthPageComponent
  ]
})
export class AuthModule {}
