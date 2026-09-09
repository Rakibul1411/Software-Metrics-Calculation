import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

/** Mirrors the backend password policy so the button disables before submit. */
const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 12;

/** Owns the account screen's password rules and change request. */
@Injectable({ providedIn: 'root' })
export class AccountFacade {
  constructor(private readonly api: DefectLabApiService) {}

  isValidChange(currentPassword: string, newPassword: string): boolean {
    return currentPassword.length > 0
      && newPassword.length >= MIN_PASSWORD_LENGTH
      && newPassword.length <= MAX_PASSWORD_LENGTH;
  }

  changePassword(currentPassword: string, newPassword: string): Observable<unknown> {
    return this.api.changePassword(currentPassword, newPassword);
  }
}
