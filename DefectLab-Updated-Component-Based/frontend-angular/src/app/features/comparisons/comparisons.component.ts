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
  templateUrl: './comparisons.component.html'
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
