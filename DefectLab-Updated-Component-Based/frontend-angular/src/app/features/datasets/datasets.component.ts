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
  template: `
    <section class="dl-grid dl-grid-3">
      <article class="dl-card dl-stat">
        <span class="dl-stat-label">Visible datasets</span>
        <strong class="dl-stat-value">{{ datasets.length }}</strong>
        <span class="dl-stat-hint">Your data plus bundled predefined data</span>
      </article>
      <article class="dl-card dl-stat">
        <span class="dl-stat-label">PROMISE</span>
        <strong class="dl-stat-value">{{ countFamily('PROMISE') }}</strong>
        <span class="dl-stat-hint">20 predictors</span>
      </article>
      <article class="dl-card dl-stat">
        <span class="dl-stat-label">AEEEM</span>
        <strong class="dl-stat-value">{{ countFamily('AEEEM') }}</strong>
        <span class="dl-stat-hint">56 prediction features</span>
      </article>
    </section>

    <section class="dl-grid dl-grid-2 dl-dataset-import-layout">
      <article class="dl-card dl-dataset-upload-card">
        <div class="dl-card-head">
          <div>
            <span class="dl-card-kicker">Import</span>
            <h2>Add a metrics dataset</h2>
            <p>Upload CSV/ARFF ground truth or an already extracted metrics file.</p>
          </div>
        </div>
        <form class="dl-form-stack dl-dataset-upload-form" (ngSubmit)="upload()">
          <div class="dl-form-row">
            <label class="dl-field">
              <span>Project name *</span>
              <input [(ngModel)]="projectName" name="projectName"
                     required placeholder="Example: Ant">
            </label>
            <label class="dl-field">
              <span>Project version *</span>
              <input [(ngModel)]="projectVersion" name="projectVersion"
                     required placeholder="Example: 1.7">
            </label>
          </div>
          <div class="dl-form-row">
            <label class="dl-field">
              <span>Family</span>
              <select [(ngModel)]="family" name="family">
                <option value="PROMISE">PROMISE</option>
                <option value="AEEEM">AEEEM</option>
              </select>
            </label>
            <label class="dl-field">
              <span>Origin</span>
              <select [(ngModel)]="origin" name="origin">
                <option value="PREDEFINED">Predefined / published</option>
                <option value="MANUAL">Manual extraction</option>
              </select>
            </label>
          </div>
          <label class="dl-file-panel">
            <input class="dl-sr-only" type="file" accept=".csv,.arff"
                   (change)="selectFile($event)">
            <strong>{{ file?.name || 'Choose CSV or ARFF' }}</strong>
            <span>The backend detects labels and validates the complete feature profile.</span>
            <span class="dl-btn dl-btn-ghost dl-btn-sm">Browse file</span>
          </label>
          <div *ngIf="uploadError" class="dl-alert dl-alert-error">{{ uploadError }}</div>
          <div *ngIf="uploadSuccess" class="dl-alert dl-alert-success">{{ uploadSuccess }}</div>
          <button class="dl-btn" type="submit"
                  [disabled]="uploading || !file || !projectName.trim() || !projectVersion.trim()">
            <span *ngIf="uploading" class="dl-spinner"></span>
            {{ uploading ? 'Validating and storing…' : 'Store dataset' }}
          </button>
        </form>
      </article>

      <article class="dl-card dl-dataset-rules-card">
        <div class="dl-card-head">
          <div>
            <span class="dl-card-kicker">Selection rules</span>
            <h2>How datasets are used</h2>
            <p>The three roles are checked before every prediction.</p>
          </div>
        </div>
        <div class="dl-step-overview">
          <div class="dl-step"><span class="dl-step-order">S</span><div>
            <strong>Labeled source</strong><p>Trains KNN or SVM. Any project in the same family.</p>
          </div></div>
          <div class="dl-step"><span class="dl-step-order">M</span><div>
            <strong>Manual target</strong><p>MANUAL metrics from source/GitHub.</p>
          </div></div>
          <div class="dl-step"><span class="dl-step-order">P</span><div>
            <strong>Predefined target</strong><p>Labeled data for the same target project/version.</p>
          </div></div>
        </div>
      </article>
    </section>

    <section class="dl-card">
      <div class="dl-card-head">
        <div>
          <h2>Stored metric datasets</h2>
          <p>Files are stored outside Neon; only their metadata path is saved in metric_datasets.</p>
        </div>
        <button class="dl-btn dl-btn-ghost dl-btn-sm" type="button" (click)="load()">Refresh</button>
      </div>
      <div class="dl-form-row dl-dataset-filter-row">
        <label class="dl-field">
          <span>Search</span>
          <input [(ngModel)]="search" placeholder="Project or version">
        </label>
        <label class="dl-field">
          <span>Family</span>
          <select [(ngModel)]="familyFilter">
            <option value="">All families</option>
            <option value="PROMISE">PROMISE</option>
            <option value="AEEEM">AEEEM</option>
          </select>
        </label>
        <label class="dl-field">
          <span>Origin</span>
          <select [(ngModel)]="originFilter">
            <option value="">All origins</option>
            <option value="PREDEFINED">Predefined</option>
            <option value="MANUAL">Manual extracted</option>
          </select>
        </label>
      </div>
      <div *ngIf="loading" class="dl-loading"><span class="dl-spinner"></span>Loading datasets…</div>
      <div *ngIf="loadError" class="dl-alert dl-alert-error">{{ loadError }}</div>
      <div class="dl-table-wrap" *ngIf="filtered.length; else empty">
        <table class="dl-table">
          <thead>
            <tr><th>Project</th><th>Family</th><th>Origin</th><th>Label</th>
              <th>Rows</th><th>Features</th><th>Created</th><th>Actions</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of filtered">
              <td><strong>{{ item.projectName }}</strong><br><small>{{ item.projectVersion || '—' }}</small></td>
              <td><span class="dl-badge dl-badge-blue">{{ item.datasetFamily }}</span></td>
              <td>{{ item.datasetType === 'PREDEFINED' ? 'Predefined' : 'Manual extracted' }}</td>
              <td>
                <span class="dl-badge"
                      [class.dl-badge-green]="item.hasActualLabel"
                      [class.dl-badge-amber]="!item.hasActualLabel">
                  {{ item.hasActualLabel ? 'Labeled' : 'Unlabeled' }}
                </span>
              </td>
              <td class="dl-num">{{ item.totalFiles | number }}</td>
              <td class="dl-num">{{ item.totalMetrics }}</td>
              <td>{{ item.createdAt | date:'mediumDate' }}</td>
              <td>
                <div class="dl-actions">
                  <button class="dl-btn dl-btn-ghost dl-btn-sm" type="button"
                          (click)="preview(item)">Preview</button>
                  <a class="dl-btn dl-btn-ghost dl-btn-sm"
                     [href]="api.datasetDownloadUrl(item.id)">CSV/ARFF</a>
                  <button *ngIf="!item.systemDataset"
                          class="dl-btn dl-btn-ghost dl-btn-sm" type="button"
                          (click)="remove(item)">Delete</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ng-template #empty><div class="dl-empty">No matching datasets.</div></ng-template>
    </section>

    <section class="dl-card" *ngIf="previewData as view">
      <div class="dl-card-head">
        <div><h2>{{ previewName }} preview</h2><p>First {{ view.rows.length }} of {{ view.totalRows }} rows.</p></div>
        <button class="dl-btn dl-btn-ghost dl-btn-sm" type="button" (click)="previewData = null">Close</button>
      </div>
      <div class="dl-table-wrap dl-scroll-y">
        <table class="dl-table">
          <thead><tr><th *ngFor="let header of view.headers">{{ header }}</th></tr></thead>
          <tbody><tr *ngFor="let row of view.rows"><td class="dl-mono" *ngFor="let cell of row">{{ cell }}</td></tr></tbody>
        </table>
      </div>
    </section>
  `
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
