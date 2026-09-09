import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { UserProfile } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SessionService } from '../../core/services/session.service';

export type AuthMode = 'login' | 'register' | 'forgot';
/** 'email' asks who they are, 'password' takes the replacement. */
export type ForgotStep = 'email' | 'password';

export interface AuthCredentials {
  name: string;
  email: string;
  password: string;
}

const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 12;

/**
 * Owns authentication requests, the per-mode form rules, and the session
 * hand-off. Sign-in endpoints are excluded from the global error toast
 * because the auth screen shows its own inline banner, so translating a
 * failure into a message is part of this feature's job too.
 */
@Injectable({ providedIn: 'root' })
export class AuthFacade {
  constructor(
    private readonly api: DefectLabApiService,
    private readonly session: SessionService
  ) {}

  login(credentials: AuthCredentials): Observable<UserProfile> {
    return this.api.login(credentials.email.trim(), credentials.password)
      .pipe(tap(user => this.session.setUser(user)));
  }

  register(credentials: AuthCredentials): Observable<UserProfile> {
    return this.api
      .register(credentials.name.trim(), credentials.email.trim(), credentials.password)
      .pipe(tap(user => this.session.setUser(user)));
  }

  forgotPassword(email: string): Observable<{ registered: boolean }> {
    return this.api.forgotPassword(email.trim());
  }

  resetPassword(email: string, password: string): Observable<{ updated: boolean }> {
    return this.api.resetPassword(email.trim(), password);
  }

  canSubmit(mode: AuthMode, step: ForgotStep, credentials: AuthCredentials): boolean {
    if (mode === 'forgot') {
      return step === 'email'
        ? credentials.email.trim().length > 0
        : this.isValidPassword(credentials.password);
    }
    const base = credentials.email.trim().length > 0 && credentials.password.length > 0;
    return mode === 'login' ? base : base && credentials.name.trim().length > 0;
  }

  isValidPassword(password: string): boolean {
    return password.length >= MIN_PASSWORD_LENGTH
      && password.length <= MAX_PASSWORD_LENGTH;
  }

  messageOf(failure: HttpErrorResponse): string {
    const payload = failure.error as { error?: string } | null;
    return payload?.error ?? 'The request could not be completed.';
  }
}
