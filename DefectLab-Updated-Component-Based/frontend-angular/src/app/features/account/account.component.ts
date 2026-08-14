import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SessionService } from '../../core/services/session.service';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-account',
  standalone: false,
  templateUrl: './account.component.html'
})
export class AccountComponent {
  currentPassword = '';
  newPassword = '';
  saving = false;
  saved = false;
  error: string | null = null;

  constructor(
    readonly session: SessionService,
    private readonly api: DefectLabApiService,
    private readonly toast: ToastService
  ) {}

  get canSubmit(): boolean {
    return this.currentPassword.length > 0
      && this.newPassword.length >= 8 && this.newPassword.length <= 12;
  }

  submit(): void {
    if (!this.canSubmit || this.saving) {
      return;
    }
    this.saving = true;
    this.error = null;
    this.saved = false;
    this.api.changePassword(this.currentPassword, this.newPassword)
      .pipe(finalize(() => (this.saving = false)))
      .subscribe({
        next: () => {
          this.saved = true;
          this.currentPassword = '';
          this.newPassword = '';
          this.toast.success('Password updated successfully.');
        },
        error: (failure: HttpErrorResponse) => {
          const message = typeof failure.error?.error === 'string'
            ? failure.error.error : 'Password could not be updated.';
          this.error = message;
          this.toast.error(message);
        }
      });
  }
}
