import { Component, OnInit } from '@angular/core';
import {
  DatasetFamily,
  DatasetType,
  DatasetPreview,
  DatasetSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-datasets',
  standalone: false,
  templateUrl: './datasets.component.html'
})
export class DatasetsComponent implements OnInit {
  datasets: DatasetSummary[] = [];
  loading = true;
  loadError = '';
  search = '';
  familyFilter = '';
  originFilter = '';

  projectName = '';
  projectVersion = '';
  family: DatasetFamily = 'PROMISE';
  origin: DatasetType = 'PREDEFINED';
  file: File | null = null;
  uploading = false;
  uploadError = '';
  uploadSuccess = '';

  previewData: DatasetPreview | null = null;
  previewName = '';

  constructor(readonly api: DefectLabApiService) {}

  ngOnInit(): void { this.load(); }

  get filtered(): DatasetSummary[] {
    const query = this.search.trim().toLowerCase();
    return this.datasets.filter(item =>
      (!query || `${item.projectName} ${item.projectVersion || ''}`.toLowerCase().includes(query)) &&
      (!this.familyFilter || item.datasetFamily === this.familyFilter) &&
      (!this.originFilter || item.datasetType === this.originFilter));
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.listDatasets().subscribe({
      next: rows => {
        this.datasets = rows;
        this.loading = false;
      },
      error: error => {
        this.loadError = error?.error?.error ?? 'Could not load metric storage.';
        this.loading = false;
      }
    });
  }

  selectFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload(): void {
    if (!this.file) return;
    this.uploading = true;
    this.uploadError = '';
    this.uploadSuccess = '';
    this.api.uploadDataset({
      file: this.file,
      projectName: this.projectName,
      projectVersion: this.projectVersion,
      family: this.family,
      type: this.origin
    }).subscribe({
      next: dataset => {
        this.uploadSuccess = `${dataset.displayName} stored successfully.`;
        this.uploading = false;
        this.file = null;
        this.load();
      },
      error: error => {
        this.uploadError = error?.error?.error ?? 'Dataset upload failed.';
        this.uploading = false;
      }
    });
  }

  preview(item: DatasetSummary): void {
    this.previewName = item.displayName;
    this.api.previewDataset(item.id).subscribe({
      next: data => this.previewData = data,
      error: error => this.loadError = error?.error?.error ?? 'Preview failed.'
    });
  }

  remove(item: DatasetSummary): void {
    if (!window.confirm(`Delete ${item.displayName}?`)) return;
    this.api.deleteDataset(item.id).subscribe({
      next: () => this.load(),
      error: error => this.loadError = error?.error?.error ?? 'Dataset could not be deleted.'
    });
  }

  countFamily(family: DatasetFamily): number {
    return this.datasets.filter(item => item.datasetFamily === family).length;
  }
}
