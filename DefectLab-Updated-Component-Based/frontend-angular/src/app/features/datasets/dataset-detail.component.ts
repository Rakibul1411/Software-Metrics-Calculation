import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { DatasetPreview, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

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

  pendingDelete: DatasetSummary | null = null;
  deleting = false;

  constructor(
    readonly api: DefectLabApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly datePipe: DatePipe
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

  remove(item: DatasetSummary): void {
    this.pendingDelete = item;
  }

  confirmDelete(): void {
    if (!this.pendingDelete || this.deleting) return;
    this.deleting = true;
    this.api.deleteDataset(this.pendingDelete.id).subscribe({
      next: () => this.router.navigate(['/datasets']),
      error: error => {
        this.deleting = false;
        this.pendingDelete = null;
        this.error = error?.error?.error ?? 'Dataset could not be deleted.';
      }
    });
  }

  cancelDelete(): void {
    if (this.deleting) return;
    this.pendingDelete = null;
  }

  back(): void {
    this.router.navigate(['/datasets']);
  }
}
