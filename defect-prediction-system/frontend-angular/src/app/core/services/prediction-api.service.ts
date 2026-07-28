import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  EvaluationResult,
  PredictionRequestOptions,
  PredictionResult
} from '../models/prediction-result.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PredictionApiService {
  private baseUrl = `${environment.apiUrl}/api/prediction`;

  constructor(private http: HttpClient) {}

  runPrediction(
    targetDatasetId: string,
    sourceFiles: File[],
    labelColumn: string,
    options: PredictionRequestOptions
  ): Observable<PredictionResult> {
    const formData = new FormData();
    formData.append('targetDatasetId', targetDatasetId);
    formData.append('targetFileFormat', this.datasetFileFormat(sourceFiles[0]));
    formData.append('labelColumn', labelColumn);
    this.appendModelOptions(formData, options);
    sourceFiles.forEach(file => formData.append('sourceFiles', file, this.uploadName(file)));
    return this.http.post<PredictionResult>(`${this.baseUrl}/run`, formData);
  }

  evaluatePrediction(
    targetFile: File,
    sourceFiles: File[],
    labelColumn: string,
    options: PredictionRequestOptions
  ): Observable<EvaluationResult> {
    const formData = new FormData();
    formData.append('targetFile', targetFile, targetFile.name);
    formData.append('labelColumn', labelColumn);
    this.appendModelOptions(formData, options);
    sourceFiles.forEach(file => formData.append('sourceFiles', file, this.uploadName(file)));
    return this.http.post<EvaluationResult>(`${this.baseUrl}/evaluate`, formData);
  }

  private uploadName(file: File): string {
    const relativePath = file.webkitRelativePath || file.name;
    return relativePath.replace(/[\\/]+/g, '__');
  }

  private datasetFileFormat(file: File): 'csv' | 'arff' {
    return file.name.toLowerCase().endsWith('.arff') ? 'arff' : 'csv';
  }

  private appendModelOptions(formData: FormData, options: PredictionRequestOptions): void {
    formData.append('classifierType', options.classifierType);
    formData.append('coralOption', String(options.coralOption));
    formData.append('topK', String(options.topK));
    formData.append('thresholdBeta', String(options.thresholdBeta ?? 2));
    if (typeof options.decisionThreshold === 'number') {
      formData.append('decisionThreshold', String(options.decisionThreshold));
    }
  }
}
