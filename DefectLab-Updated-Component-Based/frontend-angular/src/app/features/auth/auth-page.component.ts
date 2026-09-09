import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BaseComponent } from '../../core/base';
import { ThemeService } from '../../core/services/theme.service';
import { AuthCredentials, AuthFacade, AuthMode, ForgotStep } from './auth.facade';

@Component({
  selector: 'app-auth-page',
  standalone: false,
  templateUrl: './auth-page.component.html'
})
export class AuthPageComponent extends BaseComponent {
  mode: AuthMode = 'login';
  name = '';
  email = '';
  password = '';
  loading = false;

  /**
   * Auth failures render inline rather than as a toast: these endpoints are
   * on the interceptor's silent list so the message appears exactly once,
   * next to the field that caused it.
   */
  error: string | null = null;
  notice: string | null = null;

  forgotStep: ForgotStep = 'email';
  showPassword = false;

  readonly theme = inject(ThemeService);

  constructor(private readonly facade: AuthFacade) {
    super();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  get canSubmit(): boolean {
    return this.facade.canSubmit(this.mode, this.forgotStep, this.credentials);
  }

  setMode(mode: AuthMode): void {
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
    this.send(
      this.mode === 'login'
        ? this.facade.login(this.credentials)
        : this.facade.register(this.credentials),
      () => this.navigateTo(['/overview']));
  }

  private submitForgot(): void {
    if (this.forgotStep === 'email') {
      this.send(this.facade.forgotPassword(this.email), () => {
        this.forgotStep = 'password';
        this.notice = 'Account found. Enter a new password.';
      });
      return;
    }
    this.send(this.facade.resetPassword(this.email, this.password), () => {
      this.mode = 'login';
      this.forgotStep = 'email';
      this.password = '';
      this.notice = 'Password updated. Sign in with your new password.';
    });
  }

  /** One busy/inline-error shape for all four auth requests. */
  private send<T>(request$: Observable<T>, onSuccess: () => void): void {
    this.loading = true;
    this.error = null;
    this.watch(request$.pipe(finalize(() => (this.loading = false)))).subscribe({
      next: () => onSuccess(),
      error: (failure: HttpErrorResponse) => (this.error = this.facade.messageOf(failure))
    });
  }

  private get credentials(): AuthCredentials {
    return { name: this.name, email: this.email, password: this.password };
  }
}
