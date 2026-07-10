import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EvaluationResult, PredictionResult } from '../models/prediction-result.model';
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
    labelColumn: string = 'bug',
    knnValue: number = 5,
    coralOption: boolean = true,
    topK: number = 3
  ): Observable<PredictionResult> {
    const formData = new FormData();
    formData.append('targetDatasetId', targetDatasetId);
    formData.append('labelColumn', labelColumn);
    formData.append('knnValue', String(knnValue));
    formData.append('coralOption', String(coralOption));
    formData.append('topK', String(topK));
    sourceFiles.forEach(file => formData.append('sourceFiles', file, this.uploadName(file)));
    return this.http.post<PredictionResult>(`${this.baseUrl}/run`, formData);
  }

  evaluatePrediction(
    targetFile: File,
    sourceFiles: File[],
    labelColumn: string = 'bug',
    knnValue: number = 5,
    coralOption: boolean = true,
    topK: number = 3
  ): Observable<EvaluationResult> {
    const formData = new FormData();
    formData.append('targetFile', targetFile, targetFile.name);
    formData.append('labelColumn', labelColumn);
    formData.append('knnValue', String(knnValue));
    formData.append('coralOption', String(coralOption));
    formData.append('topK', String(topK));
    sourceFiles.forEach(file => formData.append('sourceFiles', file, this.uploadName(file)));
    return this.http.post<EvaluationResult>(`${this.baseUrl}/evaluate`, formData);
  }

  private uploadName(file: File): string {
    const relativePath = file.webkitRelativePath || file.name;
    return relativePath.replace(/[\\/]+/g, '__');
  }
}
