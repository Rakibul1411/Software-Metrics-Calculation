import { Injectable } from '@angular/core';
import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ToastService } from '../../shared/ui-toast/toast.service';

/**
 * Requests excluded from the automatic error toast: the silent session
 * check (a 401 here just means "not signed in", not a real error) and the
 * auth forms, which already render their own inline error message and
 * would otherwise show the same failure twice.
 */
const SILENT_URL_PATTERNS: RegExp[] = [
  /\/auth\/me$/,
  /\/auth\/login$/,
  /\/auth\/register$/,
  /\/auth\/password\/forgot$/,
  /\/auth\/password\/reset$/
];

/**
 * Safety net so no failed API call goes un-noticed: any request not in the
 * skip list above shows a top-right toast automatically on error, so
 * individual components no longer need to remember to call
 * `toast.error(...)` themselves. The error is re-thrown unchanged so
 * components can still react locally (inline messages, resetting a busy
 * flag, and so on).
 */
@Injectable()
export class ErrorToastInterceptor implements HttpInterceptor {
  constructor(private readonly toast: ToastService) {}

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (!this.isSilent(request)) {
          const message = this.extractErrorMessage(error);
          this.toast.error(message);
        }
        return throwError(() => error);
      })
    );
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Unable to reach the DefectLab server. Please check your connection or verify the backend is running.';
    }
    if (error.status === 503) {
      if (typeof error.error === 'object' && error.error?.error) {
        return error.error.error;
      }
      return 'The service is temporarily unavailable. Please try again in a moment.';
    }
    if (error.status === 504) {
      return 'The request timed out. Please try again.';
    }
    if (typeof error.error === 'string' && error.error.trim().length > 0) {
      return error.error;
    }
    if (typeof error.error === 'object' && error.error !== null) {
      if (error.error.error && typeof error.error.error === 'string') {
        return error.error.error;
      }
      if (error.error.message && typeof error.error.message === 'string') {
        return error.error.message;
      }
      if (error.error.detail && typeof error.error.detail === 'string') {
        return error.error.detail;
      }
    }
    if (error.message && !error.message.includes('Http failure response')) {
      return error.message;
    }
    return 'Something went wrong. Please try again.';
  }

  private isSilent(request: HttpRequest<unknown>): boolean {
    return SILENT_URL_PATTERNS.some(pattern => pattern.test(request.url));
  }
}
