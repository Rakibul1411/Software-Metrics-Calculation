import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PredictionRunDetail,
  PredictionRunGroup,
  PredictionRunSummary,
  DashboardData,
  DatasetFamily,
  DatasetType,
  DatasetPreview,
  DatasetSummary,
  PredictionRow,
  PredictionExecution,
  MetricComparisonDetail,
  MetricComparisonPair,
  MetricComparisonSummary,
  UserProfile
} from '../models/defectlab.model';

@Injectable({ providedIn: 'root' })
export class DefectLabApiService {
  private readonly baseUrl = environment.apiUrl;
  private readonly api = `${this.baseUrl}/api`;
  private readonly options = { withCredentials: true };

  constructor(private readonly http: HttpClient) {}

  register(name: string, email: string, password: string): Observable<UserProfile> {
    return this.http.post<UserProfile>(
      `${this.api}/auth/register`, { name, email, password }, this.options);
  }

  login(email: string, password: string): Observable<UserProfile> {
    return this.http.post<UserProfile>(
      `${this.api}/auth/login`, { email, password }, this.options);
  }

  logout(): Observable<unknown> {
    return this.http.post(`${this.api}/auth/logout`, {}, this.options);
  }

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.api}/auth/me`, this.options);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<unknown> {
    return this.http.post(
      `${this.api}/auth/password`, { currentPassword, newPassword }, this.options);
  }

  forgotPassword(email: string) {
    return this.http.post<{ registered: boolean }>(
      `${this.api}/auth/password/forgot`, { email }, this.options);
  }

  resetPassword(email: string, newPassword: string) {
    return this.http.post<{ updated: boolean }>(
      `${this.api}/auth/password/reset`, { email, newPassword }, this.options);
  }

  dashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>(`${this.api}/dashboard`, this.options);
  }

  listDatasets(): Observable<DatasetSummary[]> {
    return this.http.get<DatasetSummary[]>(`${this.api}/datasets`, this.options);
  }

  getDataset(id: number): Observable<DatasetSummary> {
    return this.http.get<DatasetSummary>(`${this.api}/datasets/${id}`, this.options);
  }

  previewDataset(id: number): Observable<DatasetPreview> {
    return this.http.get<DatasetPreview>(
      `${this.api}/datasets/${id}/preview`, this.options);
  }

  uploadDataset(input: {
    file: File;
    projectName: string;
    projectVersion: string;
    family: DatasetFamily;
    type: DatasetType;
  }): Observable<DatasetSummary> {
    const form = this.metadataForm(input.projectName, input.projectVersion, input.family);
    form.append('datasetFile', input.file, input.file.name);
    form.append('datasetType', input.type);
    return this.http.post<DatasetSummary>(`${this.api}/datasets`, form, this.options);
  }

  analyzeArchive(input: {
    file: File;
    projectName: string;
    projectVersion: string;
    family: DatasetFamily;
  }): Observable<DatasetSummary> {
    const form = this.metadataForm(input.projectName, input.projectVersion, input.family);
    form.append('projectArchive', input.file, input.file.name);
    return this.http.post<DatasetSummary>(`${this.api}/analysis`, form, this.options);
  }

  analyzeGitHub(input: {
    githubUrl: string;
    projectName: string;
    projectVersion: string;
    family: DatasetFamily;
    aeeemProfile: string;
  }): Observable<DatasetSummary> {
    const form = this.metadataForm(input.projectName, input.projectVersion, input.family);
    form.append('githubUrl', input.githubUrl);
    form.append('aeeemProfile', input.aeeemProfile);
    return this.http.post<DatasetSummary>(`${this.api}/analysis`, form, this.options);
  }

  deleteDataset(id: number): Observable<unknown> {
    return this.http.delete(`${this.api}/datasets/${id}`, this.options);
  }

  datasetDownloadUrl(id: number, format?: 'csv' | 'arff'): string {
    return `${this.api}/datasets/${id}/download` + (format ? `?format=${format}` : '');
  }

  listPredictionRuns(): Observable<PredictionRunSummary[]> {
    return this.http.get<PredictionRunSummary[]>(`${this.api}/predictions`, this.options);
  }

  listPredictionGroups(): Observable<PredictionRunGroup[]> {
    return this.http.get<PredictionRunGroup[]>(
      `${this.api}/predictions/groups`, this.options);
  }

  predictionRun(id: number): Observable<PredictionRunDetail> {
    return this.http.get<PredictionRunDetail>(
      `${this.api}/predictions/${id}`, this.options);
  }

  runPrediction(payload: Record<string, unknown>): Observable<PredictionExecution> {
    return this.http.post<PredictionExecution>(
      `${this.api}/predictions`, payload, this.options);
  }

  predictions(
    id: number,
    buggyOnly = false,
    limit = 500
  ): Observable<PredictionRow[]> {
    const params = new HttpParams()
      .set('buggyOnly', String(buggyOnly))
      .set('limit', String(limit));
    return this.http.get<PredictionRow[]>(
      `${this.api}/predictions/${id}/predictions`,
      { ...this.options, params });
  }

  predictionDownloadUrl(id: number): string {
    return `${this.api}/predictions/${id}/prediction.csv`;
  }

  reportDownloadUrl(id: number): string {
    return `${this.api}/reports/${id}.pdf`;
  }

  listMetricComparisons(): Observable<MetricComparisonSummary[]> {
    return this.http.get<MetricComparisonSummary[]>(
      `${this.api}/metric-comparisons`, this.options);
  }

  metricComparisonPairs(): Observable<MetricComparisonPair[]> {
    return this.http.get<MetricComparisonPair[]>(
      `${this.api}/metric-comparisons/eligible-pairs`, this.options);
  }

  metricComparison(id: number): Observable<MetricComparisonDetail> {
    return this.http.get<MetricComparisonDetail>(
      `${this.api}/metric-comparisons/${id}`, this.options);
  }

  runMetricComparison(payload: Record<string, unknown>): Observable<MetricComparisonDetail> {
    return this.http.post<MetricComparisonDetail>(
      `${this.api}/metric-comparisons`, payload, this.options);
  }

  metricComparisonReportUrl(id: number): string {
    return `${this.api}/metric-comparisons/${id}/report.pdf`;
  }

  private metadataForm(
    projectName: string,
    projectVersion: string,
    family: DatasetFamily
  ): FormData {
    const form = new FormData();
    if (projectName.trim()) {
      form.append('projectName', projectName.trim());
    }
    form.append('projectVersion', projectVersion.trim());
    form.append('datasetFamily', family);
    return form;
  }
}
