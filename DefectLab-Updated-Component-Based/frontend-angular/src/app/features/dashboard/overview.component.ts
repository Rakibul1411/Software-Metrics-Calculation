import { Component, OnInit } from '@angular/core';
import { DashboardData } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-overview',
  standalone: false,
  template: `
    <div class="dl-wallet-dashboard">
      <header class="dl-wallet-heading">
        <div>
          <span>Defect intelligence</span>
          <h2>Research workspace</h2>
          <p>Track metric assets, source coverage, and prediction activity.</p>
        </div>
        <div class="dl-wallet-actions">
          <a class="dl-btn dl-wallet-primary" routerLink="/analyze">+ Analyze source</a>
          <a class="dl-btn dl-btn-ghost" routerLink="/predictions">New prediction</a>
        </div>
      </header>

      <div *ngIf="loading" class="dl-loading">
        <span class="dl-spinner"></span>Loading dashboard…
      </div>
      <div *ngIf="error" class="dl-alert dl-alert-error">{{ error }}</div>

      <ng-container *ngIf="data as dashboard">
        <section class="dl-wallet-summary-grid">
          <article class="dl-wallet-card dl-wallet-balance">
            <div class="dl-wallet-card-head">
              <div>
                <span class="dl-wallet-eyebrow">Dataset portfolio</span>
                <strong>{{ dashboard.totalDatasets | number }}</strong>
                <small>Total metric assets</small>
              </div>
              <span class="dl-wallet-live"><i></i> Live</span>
            </div>

            <div class="dl-wallet-mix" aria-label="Dataset origin distribution">
              <span class="manual"
                    [style.width.%]="percent(dashboard.manualDatasets, dashboard.totalDatasets)"></span>
              <span class="predefined"
                    [style.width.%]="percent(dashboard.predefinedDatasets, dashboard.totalDatasets)"></span>
            </div>
            <div class="dl-wallet-legend">
              <span><i class="manual"></i>Manual {{ dashboard.manualDatasets }}</span>
              <span><i class="predefined"></i>Predefined {{ dashboard.predefinedDatasets }}</span>
            </div>

            <footer>
              <span>Prediction ready</span>
              <strong>{{ percent(dashboard.labeledDatasets, dashboard.totalDatasets) | number:'1.0-0' }}%</strong>
            </footer>
          </article>

          <article class="dl-wallet-card dl-wallet-metric">
            <div class="dl-wallet-metric-icon">M</div>
            <span>Manual extractions</span>
            <strong>{{ dashboard.manualDatasets | number }}</strong>
            <small>Source archives and GitHub</small>
            <a routerLink="/analyze">Add source <b>→</b></a>
          </article>

          <article class="dl-wallet-card dl-wallet-metric">
            <div class="dl-wallet-metric-icon alt">P</div>
            <span>Predefined datasets</span>
            <strong>{{ dashboard.predefinedDatasets | number }}</strong>
            <small>Labeled training sources</small>
            <a routerLink="/datasets">Open storage <b>→</b></a>
          </article>
        </section>

        <section class="dl-wallet-analytics-grid">
          <article class="dl-wallet-card dl-wallet-chart">
            <div class="dl-wallet-section-head">
              <div>
                <span class="dl-wallet-eyebrow">Metric volume</span>
                <h3>Recent dataset size</h3>
              </div>
              <span class="dl-wallet-period">Latest</span>
            </div>

            <div class="dl-wallet-chart-total">
              <strong>{{ totalRecentRows(dashboard.recentDatasets) | number }}</strong>
              <span>classes across recent datasets</span>
            </div>

            <div class="dl-wallet-chart-stage" *ngIf="dashboard.recentDatasets.length; else noChart">
              <svg viewBox="0 0 600 190" role="img" aria-label="Recent dataset row counts">
                <line x1="20" y1="35" x2="580" y2="35"/>
                <line x1="20" y1="95" x2="580" y2="95"/>
                <line x1="20" y1="155" x2="580" y2="155"/>
                <polygon class="dl-wallet-chart-area"
                         [attr.points]="chartAreaPoints(dashboard.recentDatasets)"></polygon>
                <polyline [attr.points]="chartPoints(dashboard.recentDatasets)"></polyline>
                <circle *ngFor="let point of chartDots(dashboard.recentDatasets)"
                        [attr.cx]="point.x" [attr.cy]="point.y" r="4"></circle>
              </svg>
              <div class="dl-wallet-chart-labels">
                <span *ngFor="let item of dashboard.recentDatasets">
                  {{ item.projectName }}
                </span>
              </div>
            </div>
            <ng-template #noChart>
              <div class="dl-wallet-chart-empty">Analyze a project to begin the volume chart.</div>
            </ng-template>
          </article>

          <article class="dl-wallet-card dl-wallet-prediction">
            <div class="dl-wallet-section-head">
              <div>
                <span class="dl-wallet-eyebrow">Prediction workspace</span>
                <h3>Model activity</h3>
              </div>
              <span class="dl-wallet-status-dot"></span>
            </div>
            <strong class="dl-wallet-run-count">{{ dashboard.comparisonRuns | number }}</strong>
            <span class="dl-wallet-run-label">comparison reports</span>

            <div class="dl-wallet-readiness">
              <div>
                <span>Labeled coverage</span>
                <strong>{{ dashboard.labeledDatasets }} / {{ dashboard.totalDatasets }}</strong>
              </div>
              <div class="track">
                <span [style.width.%]="percent(dashboard.labeledDatasets, dashboard.totalDatasets)"></span>
              </div>
            </div>

            <div class="dl-wallet-prediction-actions">
              <a class="dl-btn dl-wallet-primary" routerLink="/predictions">Run model</a>
              <a class="dl-btn dl-btn-ghost" routerLink="/reports">Reports</a>
            </div>
          </article>
        </section>

        <section class="dl-wallet-lists">
          <article class="dl-wallet-card dl-wallet-list-card">
            <div class="dl-wallet-section-head">
              <div>
                <span class="dl-wallet-eyebrow">Metric storage</span>
                <h3>Recent datasets</h3>
              </div>
              <a routerLink="/datasets">View all</a>
            </div>

            <div class="dl-wallet-assets" *ngIf="dashboard.recentDatasets.length; else emptyData">
              <a *ngFor="let item of dashboard.recentDatasets" routerLink="/datasets"
                 class="dl-wallet-asset">
                <span class="dl-wallet-asset-mark">{{ item.datasetFamily === 'PROMISE' ? 'P' : 'A' }}</span>
                <span class="dl-wallet-asset-copy">
                  <strong>{{ item.displayName }}</strong>
                  <small>{{ originLabel(item.datasetType) }} · {{ item.totalMetrics }} features</small>
                </span>
                <span class="dl-wallet-asset-value">
                  <strong>{{ item.totalFiles | number }}</strong>
                  <small>{{ item.hasActualLabel ? 'Labeled' : 'Unlabeled' }}</small>
                </span>
              </a>
            </div>
            <ng-template #emptyData>
              <div class="dl-wallet-list-empty">No datasets yet.</div>
            </ng-template>
          </article>

          <article class="dl-wallet-card dl-wallet-list-card">
            <div class="dl-wallet-section-head">
              <div>
                <span class="dl-wallet-eyebrow">Latest results</span>
                <h3>Prediction runs</h3>
              </div>
              <a routerLink="/reports">View reports</a>
            </div>

            <div class="dl-wallet-assets" *ngIf="dashboard.recentRuns.length; else emptyRuns">
              <a *ngFor="let run of dashboard.recentRuns" routerLink="/reports"
                 class="dl-wallet-asset">
                <span class="dl-wallet-asset-mark run">{{ run.modelConfig.modelName.charAt(0) }}</span>
                <span class="dl-wallet-asset-copy">
                  <strong>{{ run.targetDataset.displayName }}</strong>
                  <small>{{ run.modelConfig.modelName }} · {{ modelSetting(run.modelConfig) }}</small>
                </span>
                <span class="dl-wallet-asset-value">
                  <strong>{{ run.summary.predictedBuggy }}</strong>
                  <small>buggy classes</small>
                </span>
              </a>
            </div>
            <ng-template #emptyRuns>
              <div class="dl-wallet-list-empty">
                <strong>No prediction runs yet</strong>
                <span>Select compatible datasets and run a model.</span>
                <a routerLink="/predictions">Create prediction</a>
              </div>
            </ng-template>
          </article>
        </section>

        <section class="dl-wallet-workflow">
          <div><span>01</span><strong>Analyze</strong><small>Source or GitHub</small></div>
          <i></i>
          <div><span>02</span><strong>Store</strong><small>Metric datasets</small></div>
          <i></i>
          <div><span>03</span><strong>Predict</strong><small>KNN · K=1–5</small></div>
          <i></i>
          <div><span>04</span><strong>Report</strong><small>Compare results</small></div>
        </section>
      </ng-container>
    </div>
  `
})
export class OverviewComponent implements OnInit {
  data: DashboardData | null = null;
  loading = true;
  error = '';

  constructor(private readonly api: DefectLabApiService) {}

  ngOnInit(): void {
    this.api.dashboard().subscribe({
      next: data => {
        this.data = data;
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the dashboard.';
        this.loading = false;
      }
    });
  }

  originLabel(value: string): string {
    return value === 'MANUAL' ? 'Manual extraction' : 'Predefined';
  }

  modelSetting(config: { k: number }): string {
    return `K=${config.k}`;
  }

  percent(value: number, total: number): number {
    return total > 0 ? Math.max(0, Math.min(100, (value / total) * 100)) : 0;
  }

  totalRecentRows(items: DashboardData['recentDatasets']): number {
    return items.reduce((sum, item) => sum + item.totalFiles, 0);
  }

  chartPoints(items: DashboardData['recentDatasets']): string {
    return this.chartDots(items).map(point => `${point.x},${point.y}`).join(' ');
  }

  chartAreaPoints(items: DashboardData['recentDatasets']): string {
    const points = this.chartPoints(items);
    return points ? `20,170 ${points} 580,170` : '';
  }

  chartDots(items: DashboardData['recentDatasets']): Array<{ x: number; y: number }> {
    if (!items.length) return [];
    const values = items.map(item => item.totalFiles);
    const max = Math.max(...values, 1);
    if (items.length === 1) {
      const y = 160 - (values[0] / max) * 120;
      return [{ x: 20, y }, { x: 580, y }];
    }
    return values.map((value, index) => ({
      x: 20 + (index / (values.length - 1)) * 560,
      y: 160 - (value / max) * 120
    }));
  }
}
