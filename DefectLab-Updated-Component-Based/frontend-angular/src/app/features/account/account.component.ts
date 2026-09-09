import { Component, inject } from '@angular/core';
import { BaseFormComponent } from '../../core/base';
import { SessionService } from '../../core/services/session.service';
import { AccountFacade } from './account.facade';

@Component({
  selector: 'app-account',
  standalone: false,
  templateUrl: './account.component.html'
})
export class AccountComponent extends BaseFormComponent {
  currentPassword = '';
  newPassword = '';
  saved = false;

  readonly session = inject(SessionService);

  constructor(private readonly facade: AccountFacade) {
    super();
  }

  get canSubmit(): boolean {
    return this.facade.isValidChange(this.currentPassword, this.newPassword);
  }

  submit(): void {
    if (!this.canSubmit) {
      return;
    }
    this.saved = false;
    this.submitWith(
      this.facade.changePassword(this.currentPassword, this.newPassword),
      {
        success: 'Password updated successfully.',
        onSuccess: () => {
          this.saved = true;
          this.currentPassword = '';
          this.newPassword = '';
        }
      });
  }
}
