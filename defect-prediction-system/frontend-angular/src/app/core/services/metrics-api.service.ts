import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MetricsPreview } from '../models/metrics-preview.model';
import { environment } from '../../../environments/environment';

export interface MetricsExtractionRequest {
  datasetFormat: 'promise' | 'aeeem';
  projectZip?: File;
  githubUrl?: string;
  /** Java sources picked with the folder picker, sent with their relative paths. */
  projectFiles?: File[];
  aeeemProfile?: 'current' | 'jdt' | 'pde' | 'eq' | 'ml' | 'lc';
  /** Labelled release CSV; scopes extraction to its class list. */
  labelFilterCsv?: File;
}

@Injectable({ providedIn: 'root' })
export class MetricsApiService {
  private readonly baseUrl = `${environment.apiUrl}/api/metrics`;

  constructor(private readonly http: HttpClient) {}

  extractMetrics(request: MetricsExtractionRequest): Observable<MetricsPreview> {
    const formData = new FormData();
    formData.append('datasetFormat', request.datasetFormat);
    if (request.datasetFormat === 'aeeem') {
      formData.append('aeeemProfile', request.aeeemProfile ?? 'current');
    }
    if (request.projectZip) {
      formData.append('projectZip', request.projectZip, request.projectZip.name);
    }
    if (request.githubUrl) {
      formData.append('githubUrl', request.githubUrl.trim());
    }
    for (const file of request.projectFiles ?? []) {
      formData.append('projectFiles', file, file.name);
      formData.append('projectFilePaths', file.webkitRelativePath || file.name);
    }
    if (request.labelFilterCsv) {
      formData.append('labelFilterCsv', request.labelFilterCsv, request.labelFilterCsv.name);
    }
    return this.http.post<MetricsPreview>(`${this.baseUrl}/extract`, formData);
  }

  downloadDataset(datasetId: string, fileFormat: 'csv' | 'arff' = 'csv'): string {
    return `${this.baseUrl}/download/${encodeURIComponent(datasetId)}/${fileFormat}`;
  }
}
