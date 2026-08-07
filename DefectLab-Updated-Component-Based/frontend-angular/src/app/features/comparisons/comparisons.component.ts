import { Component, OnInit } from '@angular/core';
import {
  AggregateMetricComparisonRow,
  DatasetFamily,
  InstanceMetricComparisonRow,
  MetricComparisonDetail,
  MetricComparisonPair
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-comparisons',
  standalone: false,
  template: `
    <section class="dl-card">
      <div class="dl-card-head"><div>
        <span class="dl-card-kicker">Stored comparison workflow</span>
        <h2>Compare calculated metrics</h2>
        <p>
          Only projects with both a MANUAL and PREDEFINED dataset are available.
          Selecting one calculates it once and reuses the stored result afterwards.
        </p>
      </div></div>

      <div class="dl-form-row">
        <label class="dl-field">
          <span>Dataset family</span>
          <select [(ngModel)]="selectedFamily"
                  (ngModelChange)="familyChanged()"
                  [disabled]="busy">
            <option [ngValue]="null">Choose PROMISE or AEEEM first</option>
            <option [ngValue]="'PROMISE'">PROMISE · file-wise comparison</option>
            <option [ngValue]="'AEEEM'">AEEEM · metric-wise comparison</option>
          </select>
        </label>

        <label class="dl-field">
          <span>Dataset with MANUAL and PREDEFINED metrics</span>
          <select [(ngModel)]="selectedPairKey"
                  (ngModelChange)="compareSelectedPair()"
                  [disabled]="busy || !selectedFamily || !filteredPairs.length">
            <option [ngValue]="null">Choose a dataset</option>
            <option *ngFor="let pair of filteredPairs" [ngValue]="pair.key">
              {{ pair.displayName }}
              {{ pair.cached ? '· Saved comparison' : '· Not calculated yet' }}
            </option>
          </select>
        </label>
      </div>

      <div *ngIf="!busy && selectedFamily && !filteredPairs.length" class="dl-empty">
        No {{ selectedFamily }} dataset currently has both MANUAL and PREDEFINED records.
      </div>
      <div *ngIf="busy" class="dl-alert">
        Loading or calculating the metric comparison…
      </div>
      <div *ngIf="error" class="dl-alert dl-alert-error">{{ error }}</div>
    </section>

    <section class="dl-card" *ngIf="selected as comparison">
      <div class="dl-card-head"><div>
        <span class="dl-card-kicker">Metric comparison #{{ comparison.id }}</span>
        <h2>{{ comparison.manualDataset.displayName }} ↔
          {{ comparison.predefinedDataset.displayName }}</h2>
        <p>
          MANUAL ID #{{ comparison.manualDatasetId }} ·
          PREDEFINED ID #{{ comparison.predefinedDatasetId }} ·
          {{ comparison.comparisonConfig.comparisonMode }}
        </p>
      </div>
        <a class="dl-btn" [href]="api.metricComparisonReportUrl(comparison.id)">
          Download PDF report
        </a>
      </div>

      <div class="dl-alert dl-alert-success">
        {{ comparison.cacheHit
          ? 'Loaded from saved comparison storage. Metrics were not recalculated.'
          : 'Calculated for the first time and saved in metric_comparisons and storage.' }}
      </div>

      <ng-container *ngIf="comparison.result.comparisonMode === 'AGGREGATE'; else instanceResult">
        <div class="dl-grid dl-grid-3">
          <article class="dl-data-card"><span>Common numeric metrics</span>
            <strong>{{ comparison.result.commonNumericMetricCount }}</strong></article>
          <article class="dl-data-card"><span>Dataset A</span>
            <strong>MANUAL</strong></article>
          <article class="dl-data-card"><span>Dataset B</span>
            <strong>PREDEFINED</strong></article>
        </div>

        <div class="dl-table-wrap dl-comparison-result-scroll">
          <table class="dl-table">
            <thead><tr>
              <th>Metric</th>
              <th>Mean Manual</th>
              <th>Mean Predefined</th>
              <th>Std Manual</th>
              <th>Std Predefined</th>
              <th>Percentage Difference</th>
            </tr></thead>
            <tbody><tr *ngFor="let row of aggregateRows">
              <td class="dl-mono">{{ row.metric }}</td>
              <td>{{ number(row.manual.mean) }}</td>
              <td>{{ number(row.predefined.mean) }}</td>
              <td>{{ number(row.manual.standardDeviation) }}</td>
              <td>{{ number(row.predefined.standardDeviation) }}</td>
              <td>{{ percentage(row.percentageDifference) }}</td>
            </tr></tbody>
          </table>
        </div>
      </ng-container>

      <ng-template #instanceResult>
        <div class="dl-grid dl-grid-4">
          <article class="dl-data-card"><span>Matched identifiers</span>
            <strong>{{ matchedIdentifiers }}</strong></article>
          <article class="dl-data-card"><span>Common numeric metrics</span>
            <strong>{{ commonMetricCount }}</strong></article>
          <article class="dl-data-card"><span>Manual-only files</span>
            <strong>{{ manualOnlyCount }}</strong></article>
          <article class="dl-data-card"><span>Predefined-only files</span>
            <strong>{{ predefinedOnlyCount }}</strong></article>
        </div>

        <h3>Metric-wise mean &amp; std (manual vs predefined)</h3>
        <div class="dl-table-wrap dl-comparison-result-scroll">
          <table class="dl-table">
            <thead><tr>
              <th>Metric</th>
              <th>Mean Manual</th>
              <th>Mean Predefined</th>
              <th>Std Manual</th>
              <th>Std Predefined</th>
              <th>Percentage Difference</th>
            </tr></thead>
            <tbody><tr *ngFor="let row of instanceMetricStats">
              <td class="dl-mono">{{ row.metric }}</td>
              <td>{{ number(row.manual.mean) }}</td>
              <td>{{ number(row.predefined.mean) }}</td>
              <td>{{ number(row.manual.standardDeviation) }}</td>
              <td>{{ number(row.predefined.standardDeviation) }}</td>
              <td>{{ percentage(row.percentageDifference) }}</td>
            </tr></tbody>
          </table>
        </div>

        <h3>File-wise comparison</h3>
        <div class="dl-table-wrap dl-comparison-result-scroll">
          <table class="dl-table">
            <thead><tr>
              <th>File / Identifier</th><th>Metric</th><th>Manual Value</th>
              <th>Predefined Value</th><th>Status</th>
            </tr></thead>
            <tbody><tr *ngFor="let row of instanceRows">
              <td class="dl-mono">{{ row.identifier }}</td>
              <td class="dl-mono">{{ row.metric }}</td>
              <td>{{ number(row.manualValue) }}</td>
              <td>{{ number(row.predefinedValue) }}</td>
              <td>{{ row.status }}</td>
            </tr></tbody>
          </table>
        </div>
      </ng-template>
    </section>
  `
})
export class ComparisonsComponent implements OnInit {
  pairs: MetricComparisonPair[] = [];
  selectedFamily: DatasetFamily | null = null;
  selectedPairKey: string | null = null;
  selected: MetricComparisonDetail | null = null;
  busy = false;
  error = '';

  constructor(readonly api: DefectLabApiService) {}

  ngOnInit(): void {
    this.loadPairs();
  }

  get aggregateRows(): AggregateMetricComparisonRow[] {
    const result = this.selected?.result;
    return result?.comparisonMode === 'AGGREGATE' ? result.metrics : [];
  }

  get filteredPairs(): MetricComparisonPair[] {
    return this.selectedFamily
      ? this.pairs.filter(pair => pair.datasetFamily === this.selectedFamily)
      : [];
  }

  get instanceRows(): InstanceMetricComparisonRow[] {
    const result = this.selected?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.comparisons : [];
  }

  get instanceMetricStats(): AggregateMetricComparisonRow[] {
    const result = this.selected?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.metrics : [];
  }

  get matchedIdentifiers(): number {
    const result = this.selected?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.matchedIdentifiers : 0;
  }

  get commonMetricCount(): number {
    return this.selected?.result.commonNumericMetricCount ?? 0;
  }

  get manualOnlyCount(): number {
    const result = this.selected?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.manualOnly.length : 0;
  }

  get predefinedOnlyCount(): number {
    const result = this.selected?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.predefinedOnly.length : 0;
  }

  loadPairs(): void {
    this.error = '';
    this.api.metricComparisonPairs().subscribe({
      next: rows => this.pairs = rows,
      error: error => {
        this.error = error?.error?.error ?? 'Could not load comparable datasets.';
      }
    });
  }

  familyChanged(): void {
    this.selectedPairKey = null;
    this.selected = null;
    this.error = '';
  }

  compareSelectedPair(): void {
    this.selected = null;
    this.error = '';
    const pair = this.pairs.find(item => item.key === this.selectedPairKey);
    if (!pair) return;

    this.busy = true;
    this.api.runMetricComparison({
      manualDatasetId: pair.manualDatasetId,
      predefinedDatasetId: pair.predefinedDatasetId
    }).subscribe({
      next: detail => {
        this.selected = detail;
        pair.cached = true;
        pair.comparisonId = detail.id;
        this.busy = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Metric comparison failed.';
        this.busy = false;
      }
    });
  }

  number(value: number | null | undefined): string {
    return value === null || value === undefined || !Number.isFinite(value)
      ? '—' : value.toFixed(4);
  }

  percentage(value: number | null | undefined): string {
    return value === null || value === undefined || !Number.isFinite(value)
      ? '—' : `${value.toFixed(2)}%`;
  }
}
