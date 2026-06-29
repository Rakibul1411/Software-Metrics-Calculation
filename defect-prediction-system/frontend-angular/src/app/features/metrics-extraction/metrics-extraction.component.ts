import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { MetricsPreview } from '../../core/models/metrics-preview.model';
import { MetricsApiService } from '../../core/services/metrics-api.service';

type DatasetFormat = 'promise' | 'aeeem';
type SourceMode = 'zip' | 'github';

@Component({
  selector: 'app-metrics-extraction',
  standalone: false,
  templateUrl: './metrics-extraction.component.html',
  styleUrls: ['./metrics-extraction.component.css']
})
export class MetricsExtractionComponent {
  datasetFormat: DatasetFormat = 'promise';
  sourceMode: SourceMode = 'zip';
  selectedZip: File | null = null;
  githubUrl = '';
  dragActive = false;
  loading = false;
  error: string | null = null;
  result: MetricsPreview | null = null;

  constructor(private readonly metricsApi: MetricsApiService) {}

  setDatasetFormat(format: DatasetFormat): void {
    this.datasetFormat = format;
    this.clearResult();
  }

  setSourceMode(mode: SourceMode): void {
    this.sourceMode = mode;
    this.error = null;
    this.clearResult();
  }

  onZipChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.acceptZip(input.files?.item(0) ?? null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    this.acceptZip(event.dataTransfer?.files.item(0) ?? null);
  }

  removeZip(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.selectedZip = null;
    this.clearResult();
  }

  extract(): void {
    if (!this.canExtract || this.loading) return;
    this.loading = true;
    this.error = null;
    this.result = null;

    this.metricsApi.extractMetrics({
      datasetFormat: this.datasetFormat,
      projectZip: this.sourceMode === 'zip' ? this.selectedZip ?? undefined : undefined,
      githubUrl: this.sourceMode === 'github' ? this.githubUrl : undefined
    }).pipe(finalize(() => this.loading = false)).subscribe({
      next: data => this.result = data,
      error: (error: HttpErrorResponse) => this.error = this.describeError(error)
    });
  }

  get canExtract(): boolean {
    if (this.sourceMode === 'zip') return this.selectedZip !== null;
    return /^https:\/\/(www\.)?github\.com\/[^/]+\/[^/]+\/?$/i.test(this.githubUrl.trim());
  }

  get previewHeaders(): string[] {
    return this.result?.csvPreview[0] ? this.parseCsvLine(this.result.csvPreview[0]) : [];
  }

  get previewRows(): string[][] {
    return this.result?.csvPreview.slice(1).map(line => this.parseCsvLine(line)) ?? [];
  }

  get downloadUrl(): string {
    return this.result ? this.metricsApi.downloadDataset(this.result.targetDatasetId) : '';
  }

  trackByIndex(index: number): number { return index; }

  private acceptZip(file: File | null): void {
    this.error = null;
    this.clearResult();
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.zip')) {
      this.selectedZip = null;
      this.error = 'Choose a Java project packaged as a .zip file.';
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      this.selectedZip = null;
      this.error = 'The project ZIP must be 50 MB or smaller.';
      return;
    }
    this.selectedZip = file;
  }

  clearResult(): void { this.result = null; }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 0) return 'Cannot reach the metrics service. Make sure the Java backend is running on port 8080.';
    if (typeof error.error === 'object' && error.error?.error) return String(error.error.error);
    if (typeof error.error === 'string' && error.error.trim()) return error.error;
    return 'Metrics extraction failed. Please check the project and try again.';
  }

  private parseCsvLine(line: string): string[] {
    const values: string[] = [];
    let value = '';
    let quoted = false;
    for (let index = 0; index < line.length; index++) {
      const character = line[index];
      if (character === '"') {
        if (quoted && line[index + 1] === '"') {
          value += '"';
          index++;
        } else {
          quoted = !quoted;
        }
      } else if (character === ',' && !quoted) {
        values.push(value);
        value = '';
      } else {
        value += character;
      }
    }
    values.push(value);
    return values;
  }
}
