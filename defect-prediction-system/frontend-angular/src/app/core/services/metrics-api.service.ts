import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MetricsPreview } from '../models/metrics-preview.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MetricsApiService {
  private baseUrl = `${environment.apiUrl}/api/metrics`;

  constructor(private http: HttpClient) {}

  extractMetrics(sourceDirectory: string, datasetFormat: string = 'promise', filterFile?: string): Observable<MetricsPreview> {
    let params = new HttpParams()
      .set('sourceDirectory', sourceDirectory)
      .set('datasetFormat', datasetFormat);
    if (filterFile) {
      params = params.set('filterFile', filterFile);
    }
    return this.http.post<MetricsPreview>(`${this.baseUrl}/extract`, null, { params });
  }

  downloadDataset(datasetId: string): string {
    return `${this.baseUrl}/download/${datasetId}`;
  }
}
