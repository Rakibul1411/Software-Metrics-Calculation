import { Injectable } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import {
  DatasetFamily,
  DatasetPreview,
  DatasetSummary,
  DatasetType
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

export interface DatasetListFilter {
  search: string;
  family: string;
  origin: string;
}

/**
 * Owns dataset list state and every non-presentational computation the
 * Datasets feature needs (filtering, counts, detail/preview view-model
 * mapping), so the components stay limited to wiring user input to this
 * service and binding its results to their templates.
 */
@Injectable({ providedIn: 'root' })
export class DatasetsFacade {
  private datasets: DatasetSummary[] = [];

  constructor(
    private readonly api: DefectLabApiService,
    private readonly datePipe: DatePipe
  ) {}

  get list(): DatasetSummary[] {
    return this.datasets;
  }

  loadList(): Observable<DatasetSummary[]> {
    return this.api.listDatasets().pipe(tap(rows => (this.datasets = rows)));
  }

  filterList(criteria: DatasetListFilter): DatasetSummary[] {
    const query = criteria.search.trim().toLowerCase();
    return this.datasets.filter(item =>
      (!query || `${item.projectName} ${item.projectVersion || ''}`.toLowerCase().includes(query)) &&
      (!criteria.family || item.datasetFamily === criteria.family) &&
      (!criteria.origin || item.datasetType === criteria.origin));
  }

  countByFamily(family: DatasetFamily): number {
    return this.datasets.filter(item => item.datasetFamily === family).length;
  }

  get(id: number): Observable<DatasetSummary> {
    return this.api.getDataset(id);
  }

  preview(id: number): Observable<DatasetPreview> {
    return this.api.previewDataset(id);
  }

  upload(input: {
    file: File;
    projectName: string;
    projectVersion: string;
    family: DatasetFamily;
    type: DatasetType;
  }): Observable<DatasetSummary> {
    return this.api.uploadDataset(input);
  }

  delete(id: number): Observable<unknown> {
    return this.api.deleteDataset(id);
  }

  downloadUrl(id: number, format?: 'csv' | 'arff'): string {
    return this.api.datasetDownloadUrl(id, format);
  }

  originLabel(value: DatasetType): string {
    return value === 'PREDEFINED' ? 'Predefined dataset' : 'Manually extracted';
  }

  detailFields(item: DatasetSummary): DetailField[] {
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

  previewColumns(preview: DatasetPreview | null): TableColumn[] {
    const headers = preview?.headers ?? [];
    return headers.map((header, index) => ({
      key: header,
      label: header,
      className: 'dl-mono',
      sticky: index === 0
        ? 'start'
        : (index === headers.length - 1 && headers.length > 1 ? 'end' : undefined)
    }));
  }

  previewRows(preview: DatasetPreview | null): Array<Record<string, string>> {
    const headers = preview?.headers ?? [];
    return (preview?.rows ?? []).map(row =>
      Object.fromEntries(headers.map((header, index) => [header, row[index]])));
  }
}
