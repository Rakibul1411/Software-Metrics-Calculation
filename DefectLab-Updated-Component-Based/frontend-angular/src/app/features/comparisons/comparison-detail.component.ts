import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import {
  AggregateMetricComparisonRow,
  InstanceMetricComparisonRow,
  MetricComparisonDetail
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-comparison-detail',
  standalone: false,
  templateUrl: './comparison-detail.component.html'
})
export class ComparisonDetailComponent implements OnInit {
  comparison: MetricComparisonDetail | null = null;
  loading = true;
  error = '';

  readonly metricStatsColumns: TableColumn[] = [
    { key: 'metric', label: 'Metric', sticky: 'start', className: 'dl-mono', width: '14%' },
    { key: 'meanManual', label: 'Mean Manual', width: '18%' },
    { key: 'meanPredefined', label: 'Mean Predefined', width: '18%' },
    { key: 'stdManual', label: 'Std Manual', width: '16%' },
    { key: 'stdPredefined', label: 'Std Predefined', width: '16%' },
    { key: 'percentageDifference', label: 'Percentage Difference', sticky: 'end', width: '18%' }
  ];

  readonly instanceRowColumns: TableColumn[] = [
    { key: 'identifier', label: 'File / Identifier', sticky: 'start', className: 'dl-mono', width: '34%' },
    { key: 'metric', label: 'Metric', className: 'dl-mono', width: '14%' },
    { key: 'manualValue', label: 'Manual Value', width: '18%' },
    { key: 'predefinedValue', label: 'Predefined Value', width: '18%' },
    { key: 'status', label: 'Status', sticky: 'end', width: '16%' }
  ];

  constructor(
    readonly api: DefectLabApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'The comparison was not specified.';
      this.loading = false;
      return;
    }
    this.load(id);
  }

  load(id: number): void {
    this.loading = true;
    this.error = '';
    this.api.metricComparison(id).subscribe({
      next: detail => {
        this.comparison = detail;
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the comparison.';
        this.loading = false;
      }
    });
  }

  get aggregateRows(): AggregateMetricComparisonRow[] {
    const result = this.comparison?.result;
    return result?.comparisonMode === 'AGGREGATE' ? result.metrics : [];
  }

  get instanceRows(): InstanceMetricComparisonRow[] {
    const result = this.comparison?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.comparisons : [];
  }

  get instanceMetricStats(): AggregateMetricComparisonRow[] {
    const result = this.comparison?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.metrics : [];
  }

  get matchedIdentifiers(): number {
    const result = this.comparison?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.matchedIdentifiers : 0;
  }

  get commonMetricCount(): number {
    return this.comparison?.result.commonNumericMetricCount ?? 0;
  }

  get manualOnlyCount(): number {
    const result = this.comparison?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.manualOnly.length : 0;
  }

  get predefinedOnlyCount(): number {
    const result = this.comparison?.result;
    return result?.comparisonMode === 'INSTANCE_WISE' ? result.predefinedOnly.length : 0;
  }

  number(value: number | null | undefined): string {
    return value === null || value === undefined || !Number.isFinite(value)
      ? '—' : value.toFixed(4);
  }

  percentage(value: number | null | undefined): string {
    return value === null || value === undefined || !Number.isFinite(value)
      ? '—' : `${value.toFixed(2)}%`;
  }

  back(): void {
    this.router.navigate(['/metric-comparisons']);
  }

  deleteComparison = (): Observable<unknown> => this.api.deleteMetricComparison(this.comparison!.id);

  onDeleted(): void {
    this.router.navigate(['/metric-comparisons']);
  }

  downloadStarted(): void {
    this.toast.info('PDF report download started.');
  }
}
