import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MetricComparisonPair } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-comparisons',
  standalone: false,
  templateUrl: './comparisons.component.html'
})
export class ComparisonsComponent implements OnInit {
  pairs: MetricComparisonPair[] = [];
  error = '';
  runningKey: string | null = null;
  search = '';
  familyFilter = '';

  readonly familyFilterOptions: SelectOption[] = [
    { value: '', label: 'All families' },
    { value: 'PROMISE', label: 'PROMISE' },
    { value: 'AEEEM', label: 'AEEEM' }
  ];

  readonly columns: TableColumn[] = [
    { key: 'pair', label: 'Dataset pair', sticky: 'start' },
    { key: 'family', label: 'Metric family' },
    { key: 'status', label: 'Status' },
    { key: 'actions', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(
    readonly api: DefectLabApiService,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

  ngOnInit(): void { this.load(); }

  get filtered(): MetricComparisonPair[] {
    const query = this.search.trim().toLowerCase();
    return this.pairs.filter(pair =>
      (!query || pair.projectName.toLowerCase().includes(query)) &&
      (!this.familyFilter || pair.datasetFamily === this.familyFilter));
  }

  load(): void {
    this.error = '';
    this.api.metricComparisonPairs().subscribe({
      next: rows => this.pairs = rows,
      error: error => this.error = error?.error?.error ?? 'Could not load comparable datasets.'
    });
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
  }

  view(pair: MetricComparisonPair): void {
    if (pair.comparisonId) this.router.navigate(['/metric-comparisons', pair.comparisonId]);
  }

  run(pair: MetricComparisonPair): void {
    if (this.runningKey) return;
    this.runningKey = pair.key;
    this.error = '';
    this.api.runMetricComparison({
      manualDatasetId: pair.manualDatasetId,
      predefinedDatasetId: pair.predefinedDatasetId
    }).subscribe({
      next: () => {
        this.runningKey = null;
        this.toast.success('Comparison completed successfully.');
        this.load();
      },
      error: error => {
        this.runningKey = null;
        const message = error?.error?.error ?? 'Metric comparison failed.';
        this.error = message;
        this.toast.error(message);
      }
    });
  }
}
