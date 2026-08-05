import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { UserProfile } from '../models/defectlab.model';
import { DefectLabApiService } from './defectlab-api.service';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly userSubject = new BehaviorSubject<UserProfile | null>(null);
  readonly user$ = this.userSubject.asObservable();

  /** Null until the first /auth/me call resolves, so the guard can wait. */
  private resolved = false;

  constructor(private readonly api: DefectLabApiService, private readonly router: Router) {}

  get user(): UserProfile | null {
    return this.userSubject.value;
  }

  get isSignedIn(): boolean {
    return this.userSubject.value !== null;
  }

  /** Restores the session from the cookie on a page reload. */
  restore(): Observable<boolean> {
    if (this.resolved) {
      return of(this.isSignedIn);
    }
    return this.api.me().pipe(
      tap(user => {
        this.userSubject.next(user);
        this.resolved = true;
      }),
      map(() => true),
      catchError(() => {
        this.resolved = true;
        this.userSubject.next(null);
        return of(false);
      })
    );
  }

  setUser(user: UserProfile): void {
    this.resolved = true;
    this.userSubject.next(user);
  }

  signOut(): void {
    this.api.logout().pipe(catchError(() => of(null))).subscribe(() => {
      this.userSubject.next(null);
      this.router.navigate(['/login']);
    });
  }
}
