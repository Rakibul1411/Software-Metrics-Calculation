import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MetricComparisonPair } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

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
  searchOpen = false;
  familyFilter = '';

  readonly familyFilterOptions: SelectOption[] = [
    { value: '', label: 'All families' },
    { value: 'PROMISE', label: 'PROMISE' },
    { value: 'AEEEM', label: 'AEEEM' }
  ];

  @ViewChild('searchInput') private readonly searchInputRef?: ElementRef<HTMLInputElement>;

  readonly columns: TableColumn[] = [
    { key: 'pair', label: 'Dataset pair', sticky: 'start' },
    { key: 'family', label: 'Metric family' },
    { key: 'status', label: 'Status' },
    { key: 'actions', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(
    readonly api: DefectLabApiService,
    private readonly router: Router
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

  openSearch(): void {
    this.searchOpen = true;
    setTimeout(() => this.searchInputRef?.nativeElement.focus());
  }

  closeSearch(): void {
    this.searchOpen = false;
    this.search = '';
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
      next: detail => {
        this.runningKey = null;
        this.router.navigate(['/metric-comparisons', detail.id]);
      },
      error: error => {
        this.runningKey = null;
        this.error = error?.error?.error ?? 'Metric comparison failed.';
      }
    });
  }
}
