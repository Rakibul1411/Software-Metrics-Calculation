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
          const message = error?.error?.error ?? 'Something went wrong. Please try again.';
          this.toast.error(message);
        }
        return throwError(() => error);
      })
    );
  }

  private isSilent(request: HttpRequest<unknown>): boolean {
    return SILENT_URL_PATTERNS.some(pattern => pattern.test(request.url));
  }
}
