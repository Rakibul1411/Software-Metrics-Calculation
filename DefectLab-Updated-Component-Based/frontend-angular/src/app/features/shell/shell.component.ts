import { Component, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SessionService } from '../../core/services/session.service';
import { ThemeService } from '../../core/services/theme.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  standalone: false,
  templateUrl: './shell.component.html'
})
export class ShellComponent implements OnInit {
  mobileNavOpen = false;
  title = 'Dashboard';
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

  constructor(
    readonly session: SessionService,
    readonly theme: ThemeService,
    private readonly router: Router
  ) {}

  get isDark(): boolean {
    return this.theme.isDark;
  }

  ngOnInit(): void {
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
    const pages: Record<string, { title: string; description: string }> = {
      overview: {
        title: 'Dashboard',
        description: 'Monitor extracted metrics, predictions, and research reports.'
      },
      analyze: {
        title: 'Analyze source',
        description: 'Calculate PROMISE or AEEEM metrics from source code or GitHub.'
      },
      datasets: {
        title: 'Metric storage',
        description: 'Manage predefined and manually extracted datasets.'
      },
      predictions: {
        title: 'Predictions',
        description: 'Run KNN with K=1–5, choose dataset alignment, and inspect ranked classes.'
      },
      'metric-comparisons': {
        title: 'Compare Metrics',
        description: 'Compare paired MANUAL and PREDEFINED metrics from saved storage.'
      },
      reports: {
        title: 'Reports',
        description: 'Review prediction evaluation and downloadable reports.'
      },
      account: {
        title: 'Account',
        description: 'Manage your profile and workspace security.'
      }
    };
    const page = pages[segment] ?? pages['overview'];
    this.title = page.title;
    this.description = page.description;
  }

  toggleTheme(): void {
    this.theme.toggle();
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

}
