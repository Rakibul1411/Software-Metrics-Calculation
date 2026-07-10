import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { MetricsPreview } from '../../core/models/metrics-preview.model';
import {
  EvaluationResult,
  PredictionResult,
  PredictionResultItem,
  PredictionSummary
} from '../../core/models/prediction-result.model';
import { MetricsApiService } from '../../core/services/metrics-api.service';
import { PredictionApiService } from '../../core/services/prediction-api.service';

type DatasetFormat = 'promise' | 'aeeem';
type SourceMode = 'zip' | 'github';

interface ModelTestRow {
  className: string;
  predictedLabel: 'Buggy' | 'Clean';
  actualLabel: 'Buggy' | 'Clean';
  riskPercent: number;
  correct: boolean;
}

interface ModelTestResult {
  totalPredictions: number;
  labelledRows: number;
  matchedRows: number;
  unmatchedPredictions: number;
  correct: number;
  accuracy: number;
  truePositive: number;
  trueNegative: number;
  falsePositive: number;
  falseNegative: number;
  rows: ModelTestRow[];
}

const DEFAULT_LABEL_COLUMN = 'bug';
const DEFAULT_KNN_VALUE = 5;
const DEFAULT_TOP_K_VALUE = 3;
const MAX_ZIP_BYTES = 50 * 1024 * 1024;
const BUGGY_VALUES = new Set(['buggy', 'defective', 'true', 'yes']);

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
  sourceFiles: File[] = [];
  labelColumn = DEFAULT_LABEL_COLUMN;
  knnValue = DEFAULT_KNN_VALUE;
  topKValue = DEFAULT_TOP_K_VALUE;
  coralOption = true;
  predictionLoading = false;
  predictionError: string | null = null;
  predictionResult: PredictionResult | null = null;
  modelTestFile: File | null = null;
  modelTestLabelColumn = DEFAULT_LABEL_COLUMN;
  modelTestError: string | null = null;
  modelTestResult: ModelTestResult | null = null;
  evaluationSourceFiles: File[] = [];
  evaluationTargetFile: File | null = null;
  evaluationLabelColumn = DEFAULT_LABEL_COLUMN;
  evaluationKnnValue = DEFAULT_KNN_VALUE;
  evaluationTopKValue = DEFAULT_TOP_K_VALUE;
  evaluationCoralOption = true;
  evaluationLoading = false;
  evaluationError: string | null = null;
  evaluationResult: EvaluationResult | null = null;

  constructor(
    private readonly metricsApi: MetricsApiService,
    private readonly predictionApi: PredictionApiService
  ) {}

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
    this.acceptZip(this.getFirstFile(event));
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
    return this.isSupportedGitHubUrl(this.githubUrl);
  }

  get githubUrlError(): string | null {
    if (this.sourceMode !== 'github' || !this.githubUrl.trim() || this.canExtract) return null;
    return 'Enter a public GitHub repository, folder, or ZIP file URL.';
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

  get sourceFilesLabel(): string {
    return this.formatFileCount(this.sourceFiles.length, 'file selected', 'files selected', 'Choose CSV files');
  }

  get evaluationSourceFilesLabel(): string {
    return this.formatFileCount(
      this.evaluationSourceFiles.length,
      'source file selected',
      'source files selected',
      'Choose source CSV files'
    );
  }

  get evaluationTargetFileLabel(): string {
    return this.evaluationTargetFile?.name || 'Choose target CSV file';
  }

  get modelTestFileLabel(): string {
    return this.modelTestFile?.name || 'Choose labelled target CSV';
  }

  get predictionSummary(): PredictionSummary {
    if (this.predictionResult?.summary) return this.predictionResult.summary;

    const predictions = this.predictionResult?.predictions ?? [];
    const buggy = predictions.filter(item => this.isBuggyPrediction(item)).length;
    return { total: predictions.length, buggy, clean: predictions.length - buggy };
  }

  get buggyRate(): number {
    const summary = this.predictionSummary;
    return summary.total ? (summary.buggy / summary.total) * 100 : 0;
  }

  isBuggyPrediction(item: PredictionResultItem): boolean {
    if (typeof item.isBuggy === 'boolean') return item.isBuggy;
    if (item.label) return item.label.toLowerCase() === 'buggy';

    const numericPrediction = Number(item.prediction);
    return Number.isFinite(numericPrediction)
      ? numericPrediction > 0
      : BUGGY_VALUES.has(item.prediction.trim().toLowerCase());
  }

  predictionLabel(item: PredictionResultItem): string {
    return this.isBuggyPrediction(item) ? 'Buggy' : 'Clean';
  }

  riskPercent(item: PredictionResultItem): number {
    if (typeof item.riskPercent === 'number') return item.riskPercent;
    if (typeof item.riskScore === 'number') return item.riskScore * 100;
    return this.isBuggyPrediction(item) ? 100 : 0;
  }

  topRiskyMetricsText(item: PredictionResultItem): string {
    const metrics = item.topRiskyMetrics ?? [];
    if (!metrics.length) return 'No unusually high metric detected';
    return metrics.map(metric => `${metric.metric}=${metric.value}`).join(', ');
  }

  nearestBuggyText(item: PredictionResultItem): string {
    const classes = item.nearestBuggyClasses ?? [];
    if (!classes.length) return 'No buggy neighbor in top KNN';
    return classes.map(example => `${example.class} (${example.dataset})`).join(', ');
  }

  metricNumber(value: number | null | undefined): number {
    return typeof value === 'number' ? value : 0;
  }

  metricScore(value: number | null | undefined): string {
    return typeof value === 'number' ? value.toFixed(3) : 'N/A';
  }

  downloadPredictionCsv(): void {
    if (!this.predictionResult) return;

    const rows = [
      ['class', 'prediction', 'status', 'risk_percent', 'confidence', 'top_risky_metrics', 'nearest_buggy_classes'],
      ...this.predictionResult.predictions.map(item => [
        item.class,
        this.isBuggyPrediction(item) ? '1' : '0',
        this.predictionLabel(item),
        this.riskPercent(item).toFixed(2),
        item.confidence ?? '',
        this.topRiskyMetricsText(item),
        this.nearestBuggyText(item)
      ])
    ];

    this.downloadCsv('model-predictions.csv', rows);
  }

  onModelTestFileChange(event: Event): void {
    this.modelTestFile = this.getFirstFile(event);
    this.modelTestError = null;
    this.modelTestResult = null;
  }

  async runModelTest(): Promise<void> {
    if (!this.predictionResult || !this.modelTestFile) return;

    this.modelTestError = null;
    this.modelTestResult = null;

    try {
      this.modelTestResult = this.comparePredictionsWithLabels(
        this.predictionResult.predictions,
        await this.modelTestFile.text(),
        this.normalizedLabelColumn(this.modelTestLabelColumn)
      );
    } catch (error) {
      this.modelTestError = error instanceof Error
        ? error.message
        : 'Could not compare predictions with the labelled target CSV.';
    }
  }

  downloadModelTestCsv(): void {
    if (!this.modelTestResult) return;

    const rows = [
      ['class', 'predicted', 'actual', 'correct', 'risk_percent'],
      ...this.modelTestResult.rows.map(row => [
        row.className,
        row.predictedLabel,
        row.actualLabel,
        row.correct ? 'yes' : 'no',
        row.riskPercent.toFixed(2)
      ])
    ];

    this.downloadCsv('model-test-comparison.csv', rows);
  }

  onSourceFilesChange(event: Event): void {
    this.sourceFiles = this.mergeCsvFiles([], this.getCsvFiles(event));
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();
  }

  onSourceFolderChange(event: Event): void {
    this.sourceFiles = this.mergeCsvFiles(this.sourceFiles, this.getCsvFiles(event));
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();
  }

  clearSourceFiles(): void {
    this.sourceFiles = [];
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();
  }

  runPrediction(): void {
    if (!this.result || !this.sourceFiles.length || this.predictionLoading) return;

    this.predictionLoading = true;
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();

    this.predictionApi.runPrediction(
      this.result.targetDatasetId,
      this.sourceFiles,
      this.normalizedLabelColumn(this.labelColumn),
      this.knnValue,
      this.coralOption,
      this.normalizedTopK(this.topKValue, this.sourceFiles.length)
    ).pipe(finalize(() => this.predictionLoading = false)).subscribe({
      next: data => {
        if (data.status === 'error') {
          this.predictionError = data.message || 'Prediction failed.';
          return;
        }
        this.predictionResult = data;
      },
      error: (error: HttpErrorResponse) => this.predictionError = this.describePredictionError(error)
    });
  }

  onEvaluationSourceFilesChange(event: Event): void {
    this.evaluationSourceFiles = this.mergeCsvFiles([], this.getCsvFiles(event));
    this.clearEvaluation();
  }

  onEvaluationSourceFolderChange(event: Event): void {
    this.evaluationSourceFiles = this.mergeCsvFiles(this.evaluationSourceFiles, this.getCsvFiles(event));
    this.clearEvaluation();
  }

  clearEvaluationSourceFiles(): void {
    this.evaluationSourceFiles = [];
    this.clearEvaluation();
  }

  onEvaluationTargetFileChange(event: Event): void {
    this.evaluationTargetFile = this.getFirstFile(event);
    this.clearEvaluation();
  }

  runEvaluation(): void {
    if (!this.evaluationTargetFile || !this.evaluationSourceFiles.length
        || this.evaluationLoading || this.evaluationKnnValue < 1) return;

    this.evaluationLoading = true;
    this.evaluationError = null;
    this.evaluationResult = null;
    this.predictionApi.evaluatePrediction(
      this.evaluationTargetFile,
      this.evaluationSourceFiles,
      this.normalizedLabelColumn(this.evaluationLabelColumn),
      this.evaluationKnnValue,
      this.evaluationCoralOption,
      this.normalizedTopK(this.evaluationTopKValue, this.evaluationSourceFiles.length)
    ).pipe(finalize(() => this.evaluationLoading = false)).subscribe({
      next: data => {
        if (data.status === 'error') {
          this.evaluationError = data.message || 'Evaluation failed.';
          return;
        }
        this.evaluationResult = data;
      },
      error: (error: HttpErrorResponse) => this.evaluationError = this.describePredictionError(error)
    });
  }

  trackByIndex(index: number): number { return index; }

  private acceptZip(file: File | null): void {
    this.error = null;
    this.clearResult();

    if (!file) return;

    if (!this.isSupportedProjectArchive(file.name)) {
      this.selectedZip = null;
      this.error = 'Choose a Java project packaged as a .zip, .tar.gz, or .tgz file.';
      return;
    }

    if (file.size > MAX_ZIP_BYTES) {
      this.selectedZip = null;
      this.error = 'The project ZIP must be 50 MB or smaller.';
      return;
    }
    this.selectedZip = file;
  }

  private isSupportedProjectArchive(fileName: string): boolean {
    const lower = fileName.toLowerCase();
    return lower.endsWith('.zip') || lower.endsWith('.tar.gz') || lower.endsWith('.tgz');
  }

  private isSupportedGitHubUrl(value: string): boolean {
    try {
      const url = new URL(value.trim());
      const hostname = url.hostname.toLowerCase();
      const githubHost = hostname === 'github.com' || hostname === 'www.github.com';
      const parts = url.pathname.split('/').filter(Boolean);
      const repositoryRoot = parts.length === 2;
      const repositoryFolder = parts.length >= 4 && parts[2].toLowerCase() === 'tree';
      const repositoryZip = parts.length >= 6 && parts[2].toLowerCase() === 'blob'
        && parts[parts.length - 1].toLowerCase().endsWith('.zip');

      return url.protocol === 'https:' && githubHost && !url.username && !url.password
        && !url.search && !url.hash && (repositoryRoot || repositoryFolder || repositoryZip);
    } catch {
      return false;
    }
  }

  clearResult(): void {
    this.result = null;
    this.predictionResult = null;
    this.predictionError = null;
  }

  private clearEvaluation(): void {
    this.evaluationError = null;
    this.evaluationResult = null;
  }

  private clearModelTest(): void {
    this.modelTestFile = null;
    this.modelTestError = null;
    this.modelTestResult = null;
  }

  private downloadCsv(fileName: string, rows: Array<Array<string | number | boolean | null | undefined>>): void {
    const csv = rows.map(row => row.map(value => this.escapeCsv(String(value ?? ''))).join(',')).join('\n');
    const objectUrl = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');

    link.href = objectUrl;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(objectUrl);
  }

  private escapeCsv(value: string): string {
    return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
  }

  private formatFileCount(count: number, singular: string, plural: string, empty: string): string {
    if (!count) return empty;
    return `${count} ${count === 1 ? singular : plural}`;
  }

  private getFiles(event: Event): File[] {
    const input = event.target as HTMLInputElement;
    return Array.from(input.files ?? []);
  }

  private getCsvFiles(event: Event): File[] {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? [])
      .filter(file => file.name.toLowerCase().endsWith('.csv'));
    input.value = '';
    return files;
  }

  private mergeCsvFiles(existing: File[], incoming: File[]): File[] {
    const byKey = new Map<string, File>();
    for (const file of existing) {
      byKey.set(this.fileKey(file), file);
    }
    for (const file of incoming) {
      byKey.set(this.fileKey(file), file);
    }
    return Array.from(byKey.values()).sort((left, right) =>
      this.fileDisplayName(left).localeCompare(this.fileDisplayName(right)));
  }

  private fileKey(file: File): string {
    return `${file.webkitRelativePath || file.name}:${file.size}:${file.lastModified}`;
  }

  private fileDisplayName(file: File): string {
    return file.webkitRelativePath || file.name;
  }

  private getFirstFile(event: Event): File | null {
    const input = event.target as HTMLInputElement;
    return input.files?.item(0) ?? null;
  }

  private normalizedLabelColumn(value: string): string {
    return value.trim() || DEFAULT_LABEL_COLUMN;
  }

  private normalizedTopK(value: number, availableSources: number): number {
    if (!Number.isFinite(value)) return DEFAULT_TOP_K_VALUE;
    const requested = Math.max(1, Math.floor(value));
    return availableSources > 0 ? Math.min(requested, availableSources) : requested;
  }

  private comparePredictionsWithLabels(
    predictions: PredictionResultItem[],
    csvText: string,
    labelColumn: string
  ): ModelTestResult {
    const lines = csvText.split(/\r?\n/).filter(line => line.trim());
    if (lines.length < 2) {
      throw new Error('The labelled target CSV must contain a header and at least one data row.');
    }

    const headers = this.parseCsvLine(lines[0]).map(header => header.trim().toLowerCase());
    const classIndex = this.findColumnIndex(headers, ['name', 'class', 'class_name', 'classname']);
    const labelIndex = headers.indexOf(labelColumn.toLowerCase());
    if (classIndex < 0) {
      throw new Error('The labelled target CSV must contain a class identifier column such as name or class.');
    }
    if (labelIndex < 0) {
      throw new Error(`Label column '${labelColumn}' was not found in the labelled target CSV.`);
    }

    const exactLabels = new Map<string, boolean>();
    const simpleLabelBuckets = new Map<string, boolean[]>();

    for (const line of lines.slice(1)) {
      const values = this.parseCsvLine(line);
      const className = (values[classIndex] ?? '').trim();
      const labelValue = (values[labelIndex] ?? '').trim();
      if (!className || !labelValue) continue;

      const actualBuggy = this.parseActualBugLabel(labelValue, labelColumn);
      exactLabels.set(className, actualBuggy);

      const simpleName = this.simpleClassName(className);
      const bucket = simpleLabelBuckets.get(simpleName) ?? [];
      bucket.push(actualBuggy);
      simpleLabelBuckets.set(simpleName, bucket);
    }

    if (!exactLabels.size) {
      throw new Error('No labelled classes were found in the target CSV.');
    }

    const uniqueSimpleLabels = new Map<string, boolean>();
    simpleLabelBuckets.forEach((bucket, className) => {
      if (bucket.length === 1) uniqueSimpleLabels.set(className, bucket[0]);
    });

    const rows: ModelTestRow[] = [];
    let truePositive = 0;
    let trueNegative = 0;
    let falsePositive = 0;
    let falseNegative = 0;

    for (const item of predictions) {
      const actualBuggy = exactLabels.get(item.class)
        ?? uniqueSimpleLabels.get(this.simpleClassName(item.class));
      if (typeof actualBuggy !== 'boolean') continue;

      const predictedBuggy = this.isBuggyPrediction(item);
      if (predictedBuggy && actualBuggy) truePositive++;
      if (!predictedBuggy && !actualBuggy) trueNegative++;
      if (predictedBuggy && !actualBuggy) falsePositive++;
      if (!predictedBuggy && actualBuggy) falseNegative++;

      rows.push({
        className: item.class,
        predictedLabel: predictedBuggy ? 'Buggy' : 'Clean',
        actualLabel: actualBuggy ? 'Buggy' : 'Clean',
        riskPercent: this.riskPercent(item),
        correct: predictedBuggy === actualBuggy
      });
    }

    if (!rows.length) {
      throw new Error('No class names matched between the prediction result and labelled target CSV.');
    }

    const correct = rows.filter(row => row.correct).length;
    return {
      totalPredictions: predictions.length,
      labelledRows: exactLabels.size,
      matchedRows: rows.length,
      unmatchedPredictions: predictions.length - rows.length,
      correct,
      accuracy: correct / rows.length,
      truePositive,
      trueNegative,
      falsePositive,
      falseNegative,
      rows
    };
  }

  private findColumnIndex(headers: string[], candidates: string[]): number {
    return candidates.map(candidate => headers.indexOf(candidate)).find(index => index >= 0) ?? -1;
  }

  private parseActualBugLabel(value: string, labelColumn: string): boolean {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) {
      if (numeric < 0) throw new Error(`Label column '${labelColumn}' cannot contain negative defect counts.`);
      return numeric > 0;
    }

    const normalized = value.trim().toLowerCase();
    if (['clean', 'false', 'no', 'n'].includes(normalized)) return false;
    if (['buggy', 'defective', 'true', 'yes', 'y'].includes(normalized)) return true;
    throw new Error(`Unsupported label value '${value}'. Use defect counts, Clean/Buggy, or true/false.`);
  }

  private simpleClassName(className: string): string {
    const normalized = className.replace(/\$/g, '.');
    const index = normalized.lastIndexOf('.');
    return index >= 0 ? normalized.slice(index + 1) : normalized;
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 0) return 'Cannot reach the metrics service. Make sure the Java backend is running on port 8080.';
    if (typeof error.error === 'object' && error.error?.error) return this.normalizeArchiveError(String(error.error.error));
    if (typeof error.error === 'string' && error.error.trim()) return error.error;
    return 'Metrics extraction failed. Please check the project and try again.';
  }

  private normalizeArchiveError(message: string): string {
    if (message.includes('The project upload must be a .zip file.')) {
      return 'Your browser accepted the archive, but the Java backend is still running the old ZIP-only code. Restart the Spring Boot backend, then upload the .tar.gz again.';
    }
    return message;
  }

  private describePredictionError(error: HttpErrorResponse): string {
    if (error.status === 0) return 'Cannot reach the prediction service. Make sure the Java and Python services are running.';
    if (typeof error.error === 'object' && error.error?.error) return String(error.error.error);
    if (typeof error.error === 'object' && error.error?.message) return String(error.error.message);
    return 'Prediction failed. Check the source CSV schema and try again.';
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
