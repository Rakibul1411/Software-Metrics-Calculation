import { Injectable } from '@angular/core';

export interface NavItem {
  path: string;
  label: string;
  icon: string;
}

export interface PageHeading {
  title: string;
  description: string;
}

const PAGES: Record<string, PageHeading> = {
  overview: {
    title: 'Dashboard',
    description: 'Monitor extracted metrics, predictions, and research reports.'
  },
  analyze: {
    title: 'Analyze Source',
    description: 'Calculate PROMISE or AEEEM metrics from source code or GitHub.'
  },
  datasets: {
    title: 'Metric Storage',
    description: 'Manage predefined and manually extracted datasets.'
  },
  predictions: {
    title: 'Predictions',
    description: 'Train KNN models against labeled datasets and review saved prediction runs.'
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

/** Owns the application chrome's navigation map and per-route headings. */
@Injectable({ providedIn: 'root' })
export class ShellFacade {
  /** Build and manage the metric pipeline: extract, then store. */
  readonly primaryNav: NavItem[] = [
    { path: '/overview', label: 'Dashboard', icon: 'M4 13h6V4H4v9Zm10 7h6v-9h-6v9ZM4 20h6v-4H4v4Zm10-11h6V4h-6v5Z' },
    { path: '/analyze', label: 'Analyze Source', icon: 'M9 3h6v5l4 8a3 3 0 0 1-2.7 4.3H7.7A3 3 0 0 1 5 16l4-8V3Z' },
    { path: '/datasets', label: 'Metric Storage', icon: 'M12 4c4 0 7 1.1 7 2.5S16 9 12 9 5 7.9 5 6.5 8 4 12 4Zm7 6.5C19 12 16 13 12 13s-7-1-7-2.5M19 14.5C19 16 16 17 12 17s-7-1-7-2.5M5 6.5v11C5 19 8 20 12 20s7-1 7-2.5v-11' }
  ];

  /** Run models and review the resulting predictions, comparisons, and reports. */
  readonly analysisNav: NavItem[] = [
    { path: '/predictions', label: 'Predictions', icon: 'M12 20a8 8 0 1 1 8-8M12 12l5-3' },
    { path: '/metric-comparisons', label: 'Compare Metrics', icon: 'M4 7h7m2 0h7M4 17h7m2 0h7M8 4v6m8 4v6' },
    { path: '/reports', label: 'Reports', icon: 'M6 3h9l3 3v15H6V3Zm3 6h6m-6 4h6m-6 4h4' }
  ];

  /** Resolves the heading from the first path segment of a URL. */
  headingFor(url: string): PageHeading {
    const segment = url.split('?')[0].split('/')
      .filter(part => part.length > 0)[0] ?? 'overview';
    return PAGES[segment] ?? PAGES['overview'];
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
