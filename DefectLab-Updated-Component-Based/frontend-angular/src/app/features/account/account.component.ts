import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SessionService } from '../../core/services/session.service';

@Component({
  selector: 'app-account',
  standalone: false,
  template: `
    <div class="dl-grid dl-grid-2">
      <section class="dl-card">
        <div class="dl-card-head">
          <div>
            <h2>Profile</h2>
            <p>Your datasets, runs and comparisons are scoped to this account.</p>
          </div>
        </div>
        <dl class="dl-kv" *ngIf="session.user as user">
          <dt>Name</dt><dd>{{ user.name }}</dd>
          <dt>Email</dt><dd>{{ user.email }}</dd>
          <dt>Member since</dt><dd>{{ user.createdAt | date:'medium' }}</dd>
        </dl>
        <div class="dl-actions" style="margin-top:18px">
          <button type="button" class="dl-btn dl-btn-ghost" (click)="session.signOut()">
            Sign out
          </button>
        </div>
      </section>

      <section class="dl-card">
        <div class="dl-card-head">
          <div>
            <h2>Change password</h2>
            <p>Passwords are stored as BCrypt hashes, never in plain text.</p>
          </div>
        </div>
        <form (ngSubmit)="submit()" class="dl-grid" style="gap:14px">
          <label class="dl-field">
            <span>Current password</span>
            <input type="password" autocomplete="current-password"
                   [(ngModel)]="currentPassword" name="currentPassword" [disabled]="saving">
          </label>
          <label class="dl-field">
            <span>New password</span>
            <input type="password" autocomplete="new-password"
                   minlength="8" maxlength="72"
                   [(ngModel)]="newPassword" name="newPassword" [disabled]="saving">
            <small>8–72 characters.</small>
          </label>

          <div class="dl-alert dl-alert-error" *ngIf="error" role="alert">{{ error }}</div>
          <div class="dl-alert dl-alert-success" *ngIf="saved">Password updated.</div>

          <button type="submit" class="dl-btn" [disabled]="!canSubmit || saving">
            <span class="dl-spinner" *ngIf="saving" aria-hidden="true"></span>
            {{ saving ? 'Saving…' : 'Update password' }}
          </button>
        </form>
      </section>
    </div>
  `
})
export class AccountComponent {
  currentPassword = '';
  newPassword = '';
  saving = false;
  saved = false;
  error: string | null = null;

  constructor(
    readonly session: SessionService,
    private readonly api: DefectLabApiService
  ) {}

  get canSubmit(): boolean {
    return this.currentPassword.length > 0 && this.newPassword.length >= 8;
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
        },
        error: (failure: HttpErrorResponse) => {
          this.error = typeof failure.error?.error === 'string'
            ? failure.error.error : 'Password could not be updated.';
        }
      });
  }
}
