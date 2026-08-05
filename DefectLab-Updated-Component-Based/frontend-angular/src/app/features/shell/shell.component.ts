import { Component, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SessionService } from '../../core/services/session.service';

const THEME_STORAGE_KEY = 'dl-theme';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  standalone: false,
  template: `
    <div class="dl-shell">
      <button type="button" class="dl-sidebar-scrim"
              *ngIf="mobileNavOpen"
              aria-label="Close navigation"
              (click)="closeMobileNav()"></button>

      <aside class="dl-sidebar" [class.mobile-open]="mobileNavOpen">
        <button type="button" class="dl-sidebar-close"
                aria-label="Close navigation"
                (click)="closeMobileNav()">×</button>
        <a class="dl-brand" routerLink="/overview" (click)="closeMobileNav()">
          <span class="dl-brand-mark" aria-hidden="true">DL</span>
          <span>
            <strong>DefectLab</strong>
            <small>Defect intelligence</small>
          </span>
        </a>

        <nav class="dl-nav" aria-label="Main navigation">
          <span class="dl-nav-label">Workspace</span>
          <a *ngFor="let item of primaryNav"
             [routerLink]="item.path"
             routerLinkActive="active"
             (click)="closeMobileNav()">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path [attr.d]="item.icon"/>
            </svg>
            {{ item.label }}
          </a>

          <span class="dl-nav-label" style="margin-top:14px">Results</span>
          <a *ngFor="let item of analysisNav"
             [routerLink]="item.path"
             routerLinkActive="active"
             (click)="closeMobileNav()">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path [attr.d]="item.icon"/>
            </svg>
            {{ item.label }}
          </a>
        </nav>

        <div class="dl-sidebar-footer">
          <div class="dl-system-status">
            <span aria-hidden="true"></span>
            Analysis services ready
          </div>
          <p>PROMISE 20 predictors · AEEEM 56 features</p>
          <small>KNN · K=1–5 · Optional CORAL</small>
        </div>
      </aside>

      <div class="dl-main">
        <header class="dl-topbar">
          <button type="button" class="dl-menu-button"
                  aria-label="Open navigation"
                  [attr.aria-expanded]="mobileNavOpen"
                  (click)="toggleMobileNav()">
            <span></span><span></span><span></span>
          </button>
          <div class="dl-page-context">
            <span>{{ section }}</span>
            <h1>{{ title }}</h1>
            <p>{{ description }}</p>
          </div>
          <div class="dl-spacer"></div>
          <button type="button" class="dl-btn dl-btn-ghost dl-btn-sm dl-theme-control"
                  (click)="toggleTheme()"
                  [attr.aria-pressed]="isDark"
                  aria-label="Toggle dark mode">
            <span class="dl-theme-dot" aria-hidden="true"></span>
            {{ isDark ? 'Light mode' : 'Dark mode' }}
          </button>
          <div class="dl-user" *ngIf="session.user as user">
            <span class="dl-avatar" aria-hidden="true">{{ initials(user.name) }}</span>
            <span class="dl-user-copy">
              <strong>{{ user.name }}</strong>
              <small>Signed in</small>
            </span>
          </div>
          <button type="button" class="dl-btn dl-btn-ghost dl-btn-sm dl-signout"
                  (click)="session.signOut()">
            Sign out
          </button>
        </header>

        <main class="dl-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `
})
export class ShellComponent implements OnInit {
  isDark = false;
  mobileNavOpen = false;
  title = 'Dashboard';
  section = 'Workspace';
  description = 'Monitor datasets, runs, and evaluation readiness.';

  readonly primaryNav: NavItem[] = [
    { path: '/overview', label: 'Dashboard', icon: 'M4 13h6V4H4v9Zm10 7h6v-9h-6v9ZM4 20h6v-4H4v4Zm10-11h6V4h-6v5Z' },
    { path: '/analyze', label: 'Analyze', icon: 'M9 3h6v5l4 8a3 3 0 0 1-2.7 4.3H7.7A3 3 0 0 1 5 16l4-8V3Z' },
    { path: '/datasets', label: 'Metric storage', icon: 'M12 4c4 0 7 1.1 7 2.5S16 9 12 9 5 7.9 5 6.5 8 4 12 4Zm7 6.5C19 12 16 13 12 13s-7-1-7-2.5M19 14.5C19 16 16 17 12 17s-7-1-7-2.5M5 6.5v11C5 19 8 20 12 20s7-1 7-2.5v-11' }
  ];

  readonly analysisNav: NavItem[] = [
    { path: '/predictions', label: 'Predictions', icon: 'M12 20a8 8 0 1 1 8-8M12 12l5-3' },
    { path: '/metric-comparisons', label: 'Compare Metrics', icon: 'M4 7h7m2 0h7M4 17h7m2 0h7M8 4v6m8 4v6' },
    { path: '/reports', label: 'Reports', icon: 'M6 3h9l3 3v15H6V3Zm3 6h6m-6 4h6m-6 4h4' },
    { path: '/account', label: 'Account', icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 8a8 8 0 0 1 16 0' }
  ];

  constructor(readonly session: SessionService, private readonly router: Router) {}

  ngOnInit(): void {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    this.isDark = stored ? stored === 'dark' : prefersDark;
    this.applyTheme();

    this.setTitle(this.router.url);
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(event => {
        this.setTitle(event.urlAfterRedirects);
        this.closeMobileNav();
      });
  }

  private setTitle(url: string): void {
    const segment = url.split('?')[0].split('/').filter(part => part.length > 0)[0] ?? 'overview';
    const pages: Record<string, { title: string; section: string; description: string }> = {
      overview: {
        title: 'Dashboard',
        section: 'Workspace',
        description: 'Monitor extracted metrics, predictions, and research reports.'
      },
      analyze: {
        title: 'Analyze source',
        section: 'Workspace',
        description: 'Calculate PROMISE or AEEEM metrics from source code or GitHub.'
      },
      datasets: {
        title: 'Metric storage',
        section: 'Workspace',
        description: 'Manage predefined and manually extracted datasets.'
      },
      predictions: {
        title: 'Predictions',
        section: 'Results',
        description: 'Run KNN with K=1–5, choose dataset alignment, and inspect ranked classes.'
      },
      'metric-comparisons': {
        title: 'Compare Metrics',
        section: 'Results',
        description: 'Compare paired MANUAL and PREDEFINED metrics from saved storage.'
      },
      reports: {
        title: 'Reports',
        section: 'Results',
        description: 'Review prediction evaluation and downloadable reports.'
      },
      account: {
        title: 'Account',
        section: 'Settings',
        description: 'Manage your profile and workspace security.'
      }
    };
    const page = pages[segment] ?? pages['overview'];
    this.title = page.title;
    this.section = page.section;
    this.description = page.description;
  }

  toggleTheme(): void {
    this.isDark = !this.isDark;
    localStorage.setItem(THEME_STORAGE_KEY, this.isDark ? 'dark' : 'light');
    this.applyTheme();
  }

  toggleMobileNav(): void {
    this.mobileNavOpen = !this.mobileNavOpen;
  }

  closeMobileNav(): void {
    this.mobileNavOpen = false;
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .filter(part => part.length > 0)
      .slice(0, 2)
      .map(part => part[0].toUpperCase())
      .join('');
  }

  private applyTheme(): void {
    document.documentElement.setAttribute('data-theme', this.isDark ? 'dark' : 'light');
  }
}
