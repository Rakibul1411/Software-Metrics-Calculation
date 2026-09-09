import { DatePipe } from '@angular/common';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
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
 * Owns every non-presentational decision the Datasets feature makes:
 * filtering rules, counts, and the detail/preview view models. It holds no
 * list state of its own -- the screen keeps that through its base class --
 * so the same rules serve any screen that loads datasets.
 */
@Injectable({ providedIn: 'root' })
export class DatasetsFacade {
  readonly columns: TableColumn[] = [
    { key: 'projectName', label: 'Project name', sticky: 'start', width: '28%' },
    { key: 'datasetFamily', label: 'Metric family', width: '10%' },
    { key: 'datasetType', label: 'Data source', width: '14%' },
    { key: 'hasActualLabel', label: 'Labeled', width: '10%' },
    { key: 'totalFiles', label: 'Instances', align: 'right', width: '8%' },
    { key: 'totalMetrics', label: 'Features', align: 'right', width: '9%' },
    { key: 'createdAt', label: 'Created at', width: '13%' },
    { key: 'actions', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(
    private readonly api: DefectLabApiService,
    private readonly datePipe: DatePipe
  ) {}

  list(): Observable<DatasetSummary[]> {
    return this.api.listDatasets();
  }

  matches(item: DatasetSummary, criteria: DatasetListFilter): boolean {
    const haystack = `${item.projectName} ${item.projectVersion || ''}`.toLowerCase();
    return (!criteria.search || haystack.includes(criteria.search))
      && (!criteria.family || item.datasetFamily === criteria.family)
      && (!criteria.origin || item.datasetType === criteria.origin);
  }

  countByFamily(items: DatasetSummary[], family: DatasetFamily): number {
    return items.filter(item => item.datasetFamily === family).length;
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

  /**
   * Only the identifier column pins. Pinning the last one too parked an
   * arbitrary metric over the right edge, where it hid whichever column
   * happened to be underneath — the neighbouring header read as truncated.
   */
  previewColumns(preview: DatasetPreview | null): TableColumn[] {
    return (preview?.headers ?? []).map((header, index) => ({
      key: header,
      label: header,
      className: 'dl-mono',
      sticky: index === 0 ? 'start' : undefined
    }));
  }

  previewRows(preview: DatasetPreview | null): Array<Record<string, string>> {
    const headers = preview?.headers ?? [];
    return (preview?.rows ?? []).map(row =>
      Object.fromEntries(headers.map((header, index) => [header, row[index]])));
  }
}
