import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SessionService } from '../services/session.service';

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
          this.router.navigate(['/overview']);
        }
        return !signedIn;
      })
    );
  }
}
