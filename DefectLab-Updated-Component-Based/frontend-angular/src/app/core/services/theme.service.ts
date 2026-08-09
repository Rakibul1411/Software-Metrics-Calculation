import { Injectable } from '@angular/core';

const STORAGE_KEY = 'dl-theme';

/**
 * Owns the light/dark preference so every screen toggles the same state.
 * The login page renders outside the shell, so the shell cannot hold this.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  isDark = false;

  constructor() {
    const stored = localStorage.getItem(STORAGE_KEY);
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    this.isDark = stored ? stored === 'dark' : prefersDark;
    this.apply();
  }

  toggle(): void {
    this.isDark = !this.isDark;
    localStorage.setItem(STORAGE_KEY, this.isDark ? 'dark' : 'light');
    this.apply();
  }

  private apply(): void {
    document.documentElement.setAttribute('data-theme', this.isDark ? 'dark' : 'light');
  }
}
