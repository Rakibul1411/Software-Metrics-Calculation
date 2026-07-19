import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MetricsPreview } from '../models/metrics-preview.model';
import { environment } from '../../../environments/environment';

export interface MetricsExtractionRequest {
  datasetFormat: 'promise' | 'aeeem';
  projectZip?: File;
  githubUrl?: string;
}

@Injectable({ providedIn: 'root' })
export class MetricsApiService {
  private readonly baseUrl = `${environment.apiUrl}/api/metrics`;

  constructor(private readonly http: HttpClient) {}

  extractMetrics(request: MetricsExtractionRequest): Observable<MetricsPreview> {
    const formData = new FormData();
    formData.append('datasetFormat', request.datasetFormat);
    if (request.projectZip) {
      formData.append('projectZip', request.projectZip, request.projectZip.name);
    }
    if (request.githubUrl) {
      formData.append('githubUrl', request.githubUrl.trim());
    }
    return this.http.post<MetricsPreview>(`${this.baseUrl}/extract`, formData);
  }

  downloadDataset(datasetId: string, fileFormat: 'csv' | 'arff' = 'csv'): string {
    return `${this.baseUrl}/download/${encodeURIComponent(datasetId)}/${fileFormat}`;
  }
}
