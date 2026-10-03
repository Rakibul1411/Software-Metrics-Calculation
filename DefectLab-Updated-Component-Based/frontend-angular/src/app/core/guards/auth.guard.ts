import { inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateFn, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SessionService } from '../services/session.service';

/** Modern functional guard for authenticated routes. */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  return session.restore().pipe(
    map(signedIn => {
      if (!signedIn) {
        router.navigate(['/login']);
      }
      return signedIn;
    })
  );
};

/** Modern functional guard preventing signed-in users from accessing the login screen. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  return session.restore().pipe(
    map(signedIn => {
      if (signedIn) {
        router.navigate(['/dashboard']);
      }
      return !signedIn;
    })
  );
};

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private readonly session: SessionService, private readonly router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): Observable<boolean> {
    return this.session.restore().pipe(
      map(signedIn => {
        if (!signedIn) {
          this.router.navigate(['/login']);
        }
        return signedIn;
      })
    );
  }
}

/** Keeps a signed-in user away from the login screen. */
@Injectable({ providedIn: 'root' })
export class GuestGuard implements CanActivate {
  constructor(private readonly session: SessionService, private readonly router: Router) {}

  canActivate(): Observable<boolean> {
    return this.session.restore().pipe(
      map(signedIn => {
        if (signedIn) {
          this.router.navigate(['/dashboard']);
        }
        return !signedIn;
      })
    );
  }
}
