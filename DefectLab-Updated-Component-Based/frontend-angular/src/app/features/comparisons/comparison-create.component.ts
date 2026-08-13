import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MetricComparisonPair } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-comparison-create',
  standalone: false,
  templateUrl: './comparison-create.component.html'
})
export class ComparisonCreateComponent implements OnInit {
  pairs: MetricComparisonPair[] = [];
  error = '';
  busy = false;
  familyFilter = '';
  selectedKey = '';

  readonly familyFilterOptions: SelectOption[] = [
    { value: '', label: 'All families' },
    { value: 'PROMISE', label: 'PROMISE' },
    { value: 'AEEEM', label: 'AEEEM' }
  ];

  constructor(
    private readonly api: DefectLabApiService,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

  ngOnInit(): void { this.load(); }

  get uncomparedPairs(): MetricComparisonPair[] {
    return this.pairs.filter(pair => !pair.cached);
  }

  get filteredPairs(): MetricComparisonPair[] {
    return this.uncomparedPairs.filter(pair => !this.familyFilter || pair.datasetFamily === this.familyFilter);
  }

  get pairOptions(): SelectOption[] {
    return this.filteredPairs.map(pair => ({ value: pair.key, label: pair.displayName }));
  }

  get selectedPair(): MetricComparisonPair | undefined {
    return this.uncomparedPairs.find(pair => pair.key === this.selectedKey);
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
    if (!this.filteredPairs.some(pair => pair.key === this.selectedKey)) {
      this.selectedKey = '';
    }
    if (!this.filteredPairs.length) {
      this.toast.info('Every eligible dataset pair has already been compared.');
    }
  }

  onSelectedKeyChange(value: string | number | null): void {
    this.selectedKey = (value as string) ?? '';
  }

  load(): void {
    this.error = '';
    this.api.metricComparisonPairs().subscribe({
      next: rows => this.pairs = rows,
      error: error => this.error = error?.error?.error ?? 'Could not load comparable datasets.'
    });
  }

  run(): void {
    const pair = this.selectedPair;
    if (!pair || this.busy) return;
    this.busy = true;
    this.error = '';
    this.api.runMetricComparison({
      manualDatasetId: pair.manualDatasetId,
      predefinedDatasetId: pair.predefinedDatasetId
    }).subscribe({
      next: detail => {
        this.busy = false;
        this.toast.success('Comparison completed successfully.');
        this.router.navigate(['/metric-comparisons', detail.id]);
      },
      error: error => {
        this.busy = false;
        const message = error?.error?.error ?? 'Metric comparison failed.';
        this.error = message;
        this.toast.error(message);
      }
    });
  }

  back(): void {
    this.router.navigate(['/metric-comparisons']);
  }
}
