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
    description: 'Calculate PROMISE or AEEEM metrics directly from Java source code or GitHub.'
  },
  datasets: {
    title: 'Metric Storage',
    description: 'Manage predefined and manually extracted datasets.'
  },
  'datasets/new': {
    title: 'Add Dataset',
    description: 'Upload and register a new labeled or unlabeled metric dataset.'
  },
  'datasets/detail': {
    title: 'Dataset Details',
    description: 'Inspect stored software metrics, distribution, and architectural treemap.'
  },
  predictions: {
    title: 'Predictions',
    description: 'Train models against labeled datasets and review saved prediction runs.'
  },
  'predictions/new': {
    title: 'Run Prediction',
    description: 'Train KNN classifiers on source datasets and evaluate target defect risks.'
  },
  'predictions/detail': {
    title: 'Prediction Run Details',
    description: 'Review prediction scores, confusion matrix, and evaluated classes.'
  },
  'metric-comparisons': {
    title: 'Compare Metrics',
    description: 'Compare paired manual and predefined metrics from saved storage.'
  },
  'metric-comparisons/new': {
    title: 'New Comparison',
    description: 'Evaluate metric consistency between manual and predefined extractions.'
  },
  'metric-comparisons/detail': {
    title: 'Metric Comparison Details',
    description: 'Detailed statistical comparison between manual and predefined metrics.'
  },
  reports: {
    title: 'Prediction Reports',
    description: 'Review cross-project defect predictions, model agreements, and treemaps.'
  },
  'reports/detail': {
    title: 'Prediction Report Details',
    description: 'Detailed cross-project evaluation, agreement analysis, and Hotspot Treemap.'
  },
  account: {
    title: 'Account Settings',
    description: 'Manage your profile and workspace security credentials.'
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

  /** Resolves the heading from the URL path, matching exact subroutes where applicable. */
  headingFor(url: string): PageHeading {
    const parts = url.split('?')[0].split('/').filter(part => part.length > 0);
    if (!parts.length) return PAGES['overview'];

    const first = parts[0];
    const second = parts[1];

    if (second === 'new') {
      const key = `${first}/new`;
      if (PAGES[key]) return PAGES[key];
    } else if (second) {
      const key = `${first}/detail`;
      if (PAGES[key]) return PAGES[key];
    }

    return PAGES[first] ?? PAGES['overview'];
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
