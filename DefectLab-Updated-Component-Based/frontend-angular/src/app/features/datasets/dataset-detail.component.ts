import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { DatasetPreview, DatasetSummary } from '../../core/models/defectlab.model';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { ToastService } from '../../shared/ui-toast/toast.service';
import { DatasetsFacade } from './datasets.facade';

@Component({
  selector: 'app-dataset-detail',
  standalone: false,
  templateUrl: './dataset-detail.component.html'
})
export class DatasetDetailComponent implements OnInit {
  dataset: DatasetSummary | null = null;
  preview: DatasetPreview | null = null;
  loading = true;
  previewLoading = true;

  constructor(
    readonly facade: DatasetsFacade,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.toast.error('The dataset was not specified.');
      this.loading = false;
      this.previewLoading = false;
      return;
    }
    this.load(id);
  }

  load(id: number): void {
    this.loading = true;
    this.previewLoading = true;
    this.facade.get(id).subscribe({
      next: dataset => {
        this.dataset = dataset;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.previewLoading = false;
      }
    });
    this.facade.preview(id).subscribe({
      next: preview => {
        this.preview = preview;
        this.previewLoading = false;
      },
      error: () => {
        this.previewLoading = false;
      }
    });
  }

  get detailFields(): DetailField[] {
    return this.dataset ? this.facade.detailFields(this.dataset) : [];
  }

  get previewColumns(): TableColumn[] {
    return this.facade.previewColumns(this.preview);
  }

  get previewRows(): Array<Record<string, string>> {
    return this.facade.previewRows(this.preview);
  }

  deleteDataset = (): Observable<unknown> => this.facade.delete(this.dataset!.id);

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
