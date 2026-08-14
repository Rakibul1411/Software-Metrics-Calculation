import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { DatasetPreview, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-dataset-detail',
  standalone: false,
  templateUrl: './dataset-detail.component.html',
  providers: [DatePipe]
})
export class DatasetDetailComponent implements OnInit {
  dataset: DatasetSummary | null = null;
  preview: DatasetPreview | null = null;
  loading = true;
  previewLoading = true;
  error = '';

  constructor(
    readonly api: DefectLabApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly datePipe: DatePipe,
    private readonly toast: ToastService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'The dataset was not specified.';
      this.loading = false;
      this.previewLoading = false;
      return;
    }
    this.load(id);
  }

  load(id: number): void {
    this.loading = true;
    this.previewLoading = true;
    this.error = '';
    this.api.getDataset(id).subscribe({
      next: dataset => {
        this.dataset = dataset;
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the dataset.';
        this.loading = false;
        this.previewLoading = false;
      }
    });
    this.api.previewDataset(id).subscribe({
      next: preview => {
        this.preview = preview;
        this.previewLoading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the dataset preview.';
        this.previewLoading = false;
      }
    });
  }

  originLabel(value: string): string {
    return value === 'PREDEFINED' ? 'Predefined dataset' : 'Manually extracted';
  }

  get detailFields(): DetailField[] {
    const item = this.dataset;
    if (!item) return [];
    return [
      { label: 'Family', value: item.datasetFamily },
      { label: 'Origin', value: this.originLabel(item.datasetType) },
      { label: 'Label', value: item.hasActualLabel ? 'Labeled' : 'Unlabeled' },
      { label: 'Project version', value: item.projectVersion || '—' },
      { label: 'Rows', value: item.totalFiles },
      { label: 'Features', value: item.totalMetrics },
      { label: 'Created', value: this.datePipe.transform(item.createdAt, 'medium') },
      { label: 'System dataset', value: item.systemDataset ? 'Yes' : 'No' }
    ];
  }

  get previewColumns(): TableColumn[] {
    const headers = this.preview?.headers ?? [];
    return headers.map((header, index) => ({
      key: header,
      label: header,
      className: 'dl-mono',
      sticky: index === 0
        ? 'start'
        : (index === headers.length - 1 && headers.length > 1 ? 'end' : undefined)
    }));
  }

  get previewRows(): Array<Record<string, string>> {
    const headers = this.preview?.headers ?? [];
    return (this.preview?.rows ?? []).map(row =>
      Object.fromEntries(headers.map((header, index) => [header, row[index]])));
  }

  deleteDataset = (): Observable<unknown> => this.api.deleteDataset(this.dataset!.id);

  onDeleted(): void {
    this.router.navigate(['/datasets']);
  }

  downloadStarted(format: 'CSV' | 'ARFF'): void {
    this.toast.info(`${format} download started.`);
  }

  back(): void {
    this.router.navigate(['/datasets']);
  }
}
