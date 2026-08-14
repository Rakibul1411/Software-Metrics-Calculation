import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SessionService } from '../../core/services/session.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-auth-page',
  standalone: false,
  templateUrl: './auth-page.component.html'
})
export class AuthPageComponent {
  mode: 'login' | 'register' | 'forgot' = 'login';
  name = '';
  email = '';
  password = '';
  loading = false;
  error: string | null = null;

  /** 'email' asks who they are, 'password' takes the replacement. */
  forgotStep: 'email' | 'password' = 'email';
  notice: string | null = null;
  showPassword = false;

  constructor(
    private readonly api: DefectLabApiService,
    private readonly session: SessionService,
    readonly theme: ThemeService,
    private readonly router: Router
  ) {}

  toggleTheme(): void {
    this.theme.toggle();
  }

  get canSubmit(): boolean {
    if (this.mode === 'forgot') {
      return this.forgotStep === 'email'
        ? this.email.trim().length > 0
        : this.password.length >= 8 && this.password.length <= 12;
    }
    const base = this.email.trim().length > 0 && this.password.length > 0;
    return this.mode === 'login' ? base : base && this.name.trim().length > 0;
  }

  setMode(mode: 'login' | 'register' | 'forgot'): void {
    this.mode = mode;
    this.error = null;
    this.notice = null;
    this.password = '';
    this.forgotStep = 'email';
  }

  submit(): void {
    if (!this.canSubmit || this.loading) {
      return;
    }
    if (this.mode === 'forgot') {
      this.submitForgot();
      return;
    }
    this.loading = true;
    this.error = null;

    const request = this.mode === 'login'
      ? this.api.login(this.email.trim(), this.password)
      : this.api.register(this.name.trim(), this.email.trim(), this.password);

    request.pipe(finalize(() => (this.loading = false))).subscribe({
      next: user => {
        this.session.setUser(user);
        this.router.navigate(['/overview']);
      },
      error: (failure: HttpErrorResponse) => {
        this.error = this.messageOf(failure);
      }
    });
  }

  private submitForgot(): void {
    this.loading = true;
    this.error = null;

    if (this.forgotStep === 'email') {
      this.api.forgotPassword(this.email.trim())
        .pipe(finalize(() => (this.loading = false)))
        .subscribe({
          next: () => {
            this.forgotStep = 'password';
            this.notice = 'Account found. Enter a new password.';
          },
          error: (failure: HttpErrorResponse) => (this.error = this.messageOf(failure))
        });
      return;
    }

    this.api.resetPassword(this.email.trim(), this.password)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: () => {
          this.mode = 'login';
          this.forgotStep = 'email';
          this.password = '';
          this.notice = 'Password updated. Sign in with your new password.';
        },
        error: (failure: HttpErrorResponse) => (this.error = this.messageOf(failure))
      });
  }

  private messageOf(failure: HttpErrorResponse): string {
    const payload = failure.error as { error?: string } | null;
    return payload?.error ?? 'The request could not be completed.';
  }
}
