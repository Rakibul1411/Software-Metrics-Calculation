import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SessionService } from '../../core/services/session.service';

@Component({
  selector: 'app-auth-page',
  standalone: false,
  template: `
    <div class="dl-auth">
      <aside class="dl-auth-aside">
        <div class="dl-brand">
          <span class="dl-brand-mark" aria-hidden="true">DL</span>
          <span>
            <strong>DefectLab</strong>
            <small>Cross-project defect prediction</small>
          </span>
        </div>
        <h2>From source code to a defensible prediction.</h2>
        <p>
          Extract PROMISE or AEEEM metrics from Java source, validate the schema
          against a feature registry, then rank target classes with source-fitted
          scaling, optional shallow CORAL alignment, and user-configured KNN.
        </p>
        <div class="dl-auth-points">
          <div class="dl-auth-point">
            <b>Leakage-free</b>
            <span>Imputation, scaling and model fitting use labeled source data only.</span>
          </div>
          <div class="dl-auth-point">
            <b>Reproducible</b>
            <span>Every run stores its datasets, alignment choice, threshold, pipeline and seed.</span>
          </div>
          <div class="dl-auth-point">
            <b>Honest metrics</b>
            <span>An undefined metric reports why, never a fake zero.</span>
          </div>
        </div>
      </aside>

      <section class="dl-auth-form">
        <div class="dl-auth-card">
          <div class="dl-tabs" role="tablist">
            <button type="button" role="tab"
                    [class.active]="mode === 'login'"
                    [attr.aria-selected]="mode === 'login'"
                    (click)="setMode('login')">Sign in</button>
            <button type="button" role="tab"
                    [class.active]="mode === 'register'"
                    [attr.aria-selected]="mode === 'register'"
                    (click)="setMode('register')">Create account</button>
          </div>

          <div class="dl-card">
            <div class="dl-card-head">
              <div>
                <h2>{{ mode === 'login' ? 'Sign in to DefectLab' : 'Create your account' }}</h2>
                <p>{{ mode === 'login'
                    ? 'Your datasets and runs are scoped to your account.'
                    : 'Use at least 8 characters for the password.' }}</p>
              </div>
            </div>

            <form (ngSubmit)="submit()" class="dl-grid" style="gap:14px">
              <label class="dl-field" *ngIf="mode === 'register'">
                <span>Full name</span>
                <input type="text" name="name" autocomplete="name"
                       maxlength="100" [(ngModel)]="name" [disabled]="loading">
              </label>

              <label class="dl-field">
                <span>Email</span>
                <input type="email" name="email" autocomplete="email"
                       maxlength="150" [(ngModel)]="email" [disabled]="loading">
              </label>

              <label class="dl-field">
                <span>Password</span>
                <input type="password" name="password"
                       minlength="8" maxlength="72"
                       [attr.autocomplete]="mode === 'login' ? 'current-password' : 'new-password'"
                       [(ngModel)]="password" [disabled]="loading">
              </label>

              <div class="dl-alert dl-alert-error" *ngIf="error" role="alert">{{ error }}</div>

              <button type="submit" class="dl-btn" [disabled]="loading || !canSubmit">
                <span class="dl-spinner" *ngIf="loading" aria-hidden="true"></span>
                {{ loading
                    ? 'Working…'
                    : (mode === 'login' ? 'Sign in' : 'Create account') }}
              </button>
            </form>
          </div>
        </div>
      </section>
    </div>
  `
})
export class AuthPageComponent {
  mode: 'login' | 'register' = 'login';
  name = '';
  email = '';
  password = '';
  loading = false;
  error: string | null = null;

  constructor(
    private readonly api: DefectLabApiService,
    private readonly session: SessionService,
    private readonly router: Router
  ) {}

  get canSubmit(): boolean {
    const base = this.email.trim().length > 0 && this.password.length > 0;
    return this.mode === 'login' ? base : base && this.name.trim().length > 0;
  }

  setMode(mode: 'login' | 'register'): void {
    this.mode = mode;
    this.error = null;
  }

  submit(): void {
    if (!this.canSubmit || this.loading) {
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
        const payload = failure.error as { error?: string } | null;
        this.error = payload?.error ?? 'The request could not be completed.';
      }
    });
  }
}
