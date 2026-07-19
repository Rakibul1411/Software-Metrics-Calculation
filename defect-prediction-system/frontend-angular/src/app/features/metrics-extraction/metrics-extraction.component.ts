import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { MetricsPreview } from '../../core/models/metrics-preview.model';
import {
  ClassifierType,
  EvaluationResult,
  ModelConfiguration,
  PredictionRequestOptions,
  PredictionResult,
  PredictionResultItem,
  PredictionSummary
} from '../../core/models/prediction-result.model';
import { MetricsApiService } from '../../core/services/metrics-api.service';
import { PredictionApiService } from '../../core/services/prediction-api.service';

type DatasetFormat = 'promise' | 'aeeem';
type SourceMode = 'zip' | 'github';
type DatasetFileExtension = 'csv' | 'arff';

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
  balancedAccuracy: number;
  precision: number;
  recall: number;
  specificity: number;
  f1: number;
  f2: number;
  mcc: number;
  truePositive: number;
  trueNegative: number;
  falsePositive: number;
  falseNegative: number;
  rows: ModelTestRow[];
}

const DEFAULT_LABEL_COLUMN = 'bug';
const DEFAULT_KNN_VALUE = 5;
const DEFAULT_SVM_C = 1;
const DEFAULT_TOP_K_VALUE = 3;
const MAX_ZIP_BYTES = 50 * 1024 * 1024;
const BUGGY_VALUES = new Set(['buggy', 'defective', 'true', 'yes']);

@Component({
  selector: 'app-metrics-extraction',
  standalone: false,
  templateUrl: './metrics-extraction.component.html',
  styleUrls: ['./metrics-extraction.component.css']
})
export class MetricsExtractionComponent implements OnDestroy {
  @ViewChild('evaluationTableShell') private evaluationTableShell?: ElementRef<HTMLElement>;

  datasetFormat: DatasetFormat = 'promise';
  sourceMode: SourceMode = 'zip';
  selectedZip: File | null = null;
  githubUrl = '';
  dragActive = false;
  loading = false;
  analysisElapsedSeconds = 0;
  error: string | null = null;
  result: MetricsPreview | null = null;
  sourceFiles: File[] = [];
  labelColumn = DEFAULT_LABEL_COLUMN;
  classifierType: ClassifierType = 'knn';
  knnValue = DEFAULT_KNN_VALUE;
  autoTuneK = false;
  svmC = DEFAULT_SVM_C;
  autoTuneSvmC = false;
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
  evaluationClassifierType: ClassifierType = 'knn';
  evaluationKnnValue = DEFAULT_KNN_VALUE;
  evaluationAutoTuneK = false;
  evaluationSvmC = DEFAULT_SVM_C;
  evaluationAutoTuneSvmC = false;
  evaluationTopKValue = DEFAULT_TOP_K_VALUE;
  evaluationCoralOption = true;
  evaluationLoading = false;
  evaluationError: string | null = null;
  evaluationResult: EvaluationResult | null = null;
  private analysisTimer?: ReturnType<typeof setInterval>;

  constructor(
    private readonly metricsApi: MetricsApiService,
    private readonly predictionApi: PredictionApiService
  ) {}

  ngOnDestroy(): void {
    this.stopAnalysisTimer();
  }

  setDatasetFormat(format: DatasetFormat): void {
    if (this.datasetFormat === format) return;

    this.datasetFormat = format;
    const labelColumn = format === 'aeeem' ? 'class' : DEFAULT_LABEL_COLUMN;
    this.labelColumn = labelColumn;
    this.modelTestLabelColumn = labelColumn;
    this.evaluationLabelColumn = labelColumn;
    this.sourceFiles = [];
    this.evaluationSourceFiles = [];
    this.evaluationTargetFile = null;
    this.clearResult();
    this.clearModelTest();
    this.clearEvaluation();
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
    this.startAnalysisTimer();
    this.error = null;
    this.result = null;

    this.metricsApi.extractMetrics({
      datasetFormat: this.datasetFormat,
      projectZip: this.sourceMode === 'zip' ? this.selectedZip ?? undefined : undefined,
      githubUrl: this.sourceMode === 'github' ? this.githubUrl : undefined
    }).pipe(finalize(() => {
      this.loading = false;
      this.stopAnalysisTimer();
    })).subscribe({
      next: data => this.result = data,
      error: (error: HttpErrorResponse) => this.error = this.describeError(error)
    });
  }

  get analysisElapsedLabel(): string {
    const minutes = Math.floor(this.analysisElapsedSeconds / 60);
    const seconds = this.analysisElapsedSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  get canExtract(): boolean {
    if (this.sourceMode === 'zip') return this.selectedZip !== null;
    return this.isSupportedGitHubUrl(this.githubUrl)
      && !(this.datasetFormat === 'aeeem' && this.isGitHubZipUrl(this.githubUrl));
  }

  get githubUrlError(): string | null {
    if (this.sourceMode !== 'github' || !this.githubUrl.trim() || this.canExtract) return null;
    return this.datasetFormat === 'aeeem'
      ? 'Enter a public GitHub repository or folder URL so full Git history can be analyzed.'
      : 'Enter a public GitHub repository, folder, or ZIP file URL.';
  }

  get previewHeaders(): string[] {
    return this.result?.csvPreview[0] ? this.parseCsvLine(this.result.csvPreview[0]) : [];
  }

  get previewRows(): string[][] {
    return this.result?.csvPreview.slice(1).map(line => this.parseCsvLine(line)) ?? [];
  }

  get csvDownloadUrl(): string {
    return this.result ? this.metricsApi.downloadDataset(this.result.targetDatasetId, 'csv') : '';
  }

  get arffDownloadUrl(): string {
    return this.result ? this.metricsApi.downloadDataset(this.result.targetDatasetId, 'arff') : '';
  }

  get sourceFilesLabel(): string {
    const label = this.formatFileCount(this.sourceFiles.length, 'file selected', 'files selected', 'Choose CSV or ARFF files');
    return this.sourceFiles.length ? `${label} · ${this.datasetFileFormat(this.sourceFiles[0]).toUpperCase()}` : label;
  }

  get evaluationSourceFilesLabel(): string {
    const label = this.formatFileCount(
      this.evaluationSourceFiles.length,
      'source file selected',
      'source files selected',
      'Choose source CSV / ARFF files'
    );
    return this.evaluationSourceFiles.length
      ? `${label} · ${this.datasetFileFormat(this.evaluationSourceFiles[0]).toUpperCase()}`
      : label;
  }

  get evaluationTargetFileLabel(): string {
    return this.evaluationTargetFile?.name || 'Choose target CSV / ARFF file';
  }

  get canRunPrediction(): boolean {
    return !!this.result && this.sourceFiles.length > 0 && !this.predictionLoading
      && this.isValidModelOptions(this.classifierType, this.knnValue, this.svmC)
      && this.topKValue >= 1 && this.hasOneDatasetFormat(this.sourceFiles);
  }

  get canRunEvaluation(): boolean {
    return !!this.evaluationTargetFile && this.evaluationSourceFiles.length > 0 && !this.evaluationLoading
      && this.isValidModelOptions(this.evaluationClassifierType, this.evaluationKnnValue, this.evaluationSvmC)
      && this.evaluationTopKValue >= 1
      && this.hasOneDatasetFormat(this.evaluationSourceFiles)
      && this.datasetFileFormat(this.evaluationTargetFile) === this.datasetFileFormat(this.evaluationSourceFiles[0]);
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

  classifierName(configuration: ModelConfiguration | undefined): string {
    if (configuration?.classifierType === 'svm') return 'Linear SVM';
    if (configuration?.classifierType === 'knn') return 'KNN';
    return configuration?.classifier?.toLowerCase().includes('svm') ? 'Linear SVM' : 'KNN';
  }

  classifierSetting(configuration: ModelConfiguration | undefined): string {
    if (this.isSvmConfiguration(configuration)) {
      const cValue = configuration?.selectedSvmC;
      return typeof cValue === 'number' ? `C = ${cValue}` : 'Balanced classes';
    }
    const kValue = configuration?.selectedK;
    return typeof kValue === 'number' ? `K = ${kValue}` : 'Distance weighted';
  }

  usesNearestNeighbors(result: PredictionResult | null): boolean {
    return !this.isSvmConfiguration(result?.modelConfiguration);
  }

  metricNumber(value: number | null | undefined): number {
    return typeof value === 'number' ? value : 0;
  }

  metricScore(value: number | null | undefined): string {
    return typeof value === 'number' ? value.toFixed(3) : 'N/A';
  }

  metricPercent(value: number | null | undefined): string {
    return typeof value === 'number' ? `${(value * 100).toFixed(1)}%` : 'N/A';
  }

  get evaluationRows(): PredictionResultItem[] {
    return this.evaluationResult?.predictions ?? [];
  }

  downloadPredictionCsv(): void {
    if (!this.predictionResult) return;

    const includeNearestClasses = this.usesNearestNeighbors(this.predictionResult);
    const rows = [
      [
        'class', 'prediction', 'status', 'risk_percent', 'confidence', 'top_risky_metrics',
        ...(includeNearestClasses ? ['nearest_buggy_classes'] : [])
      ],
      ...this.predictionResult.predictions.map(item => [
        item.class,
        this.isBuggyPrediction(item) ? '1' : '0',
        this.predictionLabel(item),
        this.riskPercent(item).toFixed(2),
        item.confidence ?? '',
        this.topRiskyMetricsText(item),
        ...(includeNearestClasses ? [this.nearestBuggyText(item)] : [])
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

    if (this.datasetFormat === 'aeeem') {
      this.modelTestError = 'AEEEM datasets have no class identifier column. Use labelled-dataset evaluation instead.';
      return;
    }

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

  downloadEvaluationCsv(): void {
    if (!this.evaluationResult) return;

    const rows = [
      ['class', 'actual', 'predicted', 'correct', 'risk_percent'],
      ...this.evaluationRows.map(item => [
        item.class,
        item.actualLabel ?? '',
        item.label ?? this.predictionLabel(item),
        item.correct ? 'yes' : 'no',
        this.riskPercent(item).toFixed(2)
      ])
    ];

    this.downloadCsv('labelled-dataset-evaluation.csv', rows);
  }

  onSourceFilesChange(event: Event): void {
    this.sourceFiles = this.acceptSameFormatFiles([], this.getDatasetFiles(event), 'prediction');
    this.predictionResult = null;
    this.clearModelTest();
  }

  onSourceFolderChange(event: Event): void {
    this.sourceFiles = this.acceptSameFormatFiles(this.sourceFiles, this.getDatasetFiles(event), 'prediction');
    this.predictionResult = null;
    this.clearModelTest();
  }

  clearSourceFiles(): void {
    this.sourceFiles = [];
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();
  }

  onPredictionClassifierChange(): void {
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();
  }

  runPrediction(): void {
    if (!this.canRunPrediction || !this.result) return;

    const requestedClassifier = this.classifierType;
    this.predictionLoading = true;
    this.predictionError = null;
    this.predictionResult = null;
    this.clearModelTest();

    this.predictionApi.runPrediction(
      this.result.targetDatasetId,
      this.sourceFiles,
      this.predictionLabelColumn(this.labelColumn),
      this.predictionOptions()
    ).pipe(finalize(() => this.predictionLoading = false)).subscribe({
      next: data => {
        if (data.status === 'error') {
          this.predictionError = data.message || 'Prediction failed.';
          return;
        }
        if (!this.hasExpectedClassifier(data, requestedClassifier)) {
          this.predictionError = this.classifierMismatchMessage(requestedClassifier);
          return;
        }
        this.predictionResult = data;
      },
      error: (error: HttpErrorResponse) => this.predictionError = this.describePredictionError(error)
    });
  }

  onEvaluationSourceFilesChange(event: Event): void {
    const files = this.getDatasetFiles(event);
    this.evaluationSourceFiles = this.acceptSameFormatFiles([], files, 'evaluation');
    this.validateEvaluationFormat();
  }

  onEvaluationSourceFolderChange(event: Event): void {
    const files = this.getDatasetFiles(event);
    this.evaluationSourceFiles = this.acceptSameFormatFiles(this.evaluationSourceFiles, files, 'evaluation');
    this.validateEvaluationFormat();
  }

  clearEvaluationSourceFiles(): void {
    this.evaluationSourceFiles = [];
    this.clearEvaluation();
  }

  onEvaluationClassifierChange(): void {
    this.clearEvaluation();
  }

  onEvaluationTargetFileChange(event: Event): void {
    this.evaluationTargetFile = this.getFirstFile(event);
    this.clearEvaluation();
    if (this.datasetFormat === 'aeeem' && this.evaluationLabelColumn === DEFAULT_LABEL_COLUMN) {
      this.evaluationLabelColumn = 'class';
    }
    this.validateEvaluationFormat();
  }

  runEvaluation(): void {
    if (!this.canRunEvaluation || !this.evaluationTargetFile) return;

    const requestedClassifier = this.evaluationClassifierType;
    this.evaluationLoading = true;
    this.evaluationError = null;
    this.evaluationResult = null;
    this.predictionApi.evaluatePrediction(
      this.evaluationTargetFile,
      this.evaluationSourceFiles,
      this.predictionLabelColumn(this.evaluationLabelColumn),
      this.evaluationOptions()
    ).pipe(finalize(() => this.evaluationLoading = false)).subscribe({
      next: data => {
        if (data.status === 'error') {
          this.evaluationError = data.message || 'Evaluation failed.';
          return;
        }
        if (!this.hasExpectedClassifier(data, requestedClassifier)) {
          this.evaluationError = this.classifierMismatchMessage(requestedClassifier);
          return;
        }
        this.evaluationResult = data;
        this.resetEvaluationTableScroll();
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

  private isGitHubZipUrl(value: string): boolean {
    try {
      const parts = new URL(value.trim()).pathname.split('/').filter(Boolean);
      return parts.length >= 6 && parts[2]?.toLowerCase() === 'blob'
        && parts[parts.length - 1].toLowerCase().endsWith('.zip');
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

  private resetEvaluationTableScroll(): void {
    setTimeout(() => {
      if (this.evaluationTableShell?.nativeElement) {
        this.evaluationTableShell.nativeElement.scrollTop = 0;
        this.evaluationTableShell.nativeElement.scrollLeft = 0;
      }
    });
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

  private startAnalysisTimer(): void {
    this.stopAnalysisTimer();
    this.analysisElapsedSeconds = 0;
    this.analysisTimer = setInterval(() => this.analysisElapsedSeconds++, 1000);
  }

  private stopAnalysisTimer(): void {
    if (this.analysisTimer) {
      clearInterval(this.analysisTimer);
      this.analysisTimer = undefined;
    }
  }

  private getDatasetFiles(event: Event): File[] {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? [])
      .filter(file => /\.(csv|arff)$/i.test(file.name));
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

  private acceptSameFormatFiles(existing: File[], incoming: File[], context: 'prediction' | 'evaluation'): File[] {
    const merged = this.mergeCsvFiles(existing, incoming);
    if (!this.hasOneDatasetFormat(merged)) {
      const message = 'Choose only one dataset format at a time: all source files must be CSV or all must be ARFF.';
      if (context === 'prediction') {
        this.predictionError = message;
      } else {
        this.evaluationError = message;
      }
      return existing;
    }
    if (context === 'prediction') {
      this.predictionError = null;
    } else {
      this.evaluationError = null;
    }
    return merged;
  }

  private validateEvaluationFormat(): void {
    this.evaluationResult = null;
    if (!this.evaluationTargetFile || !this.evaluationSourceFiles.length) return;
    if (this.datasetFileFormat(this.evaluationTargetFile) !== this.datasetFileFormat(this.evaluationSourceFiles[0])) {
      this.evaluationError = 'Source and target formats must match. Use CSV with CSV or ARFF with ARFF.';
    }
  }

  private hasOneDatasetFormat(files: File[]): boolean {
    return new Set(files.map(file => this.datasetFileFormat(file))).size <= 1;
  }

  private datasetFileFormat(file: File): DatasetFileExtension {
    return file.name.toLowerCase().endsWith('.arff') ? 'arff' : 'csv';
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

  private predictionLabelColumn(value: string): string {
    return this.datasetFormat === 'aeeem' ? 'class' : this.normalizedLabelColumn(value);
  }

  private normalizedTopK(value: number, availableSources: number): number {
    if (!Number.isFinite(value)) return DEFAULT_TOP_K_VALUE;
    const requested = Math.max(1, Math.floor(value));
    return availableSources > 0 ? Math.min(requested, availableSources) : requested;
  }

  private predictionOptions(): PredictionRequestOptions {
    return {
      classifierType: this.classifierType,
      knnValue: this.knnValue,
      autoTuneK: this.autoTuneK,
      svmC: this.svmC,
      autoTuneSvmC: this.autoTuneSvmC,
      coralOption: this.coralOption,
      topK: this.normalizedTopK(this.topKValue, this.sourceFiles.length),
      thresholdBeta: 2
    };
  }

  private evaluationOptions(): PredictionRequestOptions {
    return {
      classifierType: this.evaluationClassifierType,
      knnValue: this.evaluationKnnValue,
      autoTuneK: this.evaluationAutoTuneK,
      svmC: this.evaluationSvmC,
      autoTuneSvmC: this.evaluationAutoTuneSvmC,
      coralOption: this.evaluationCoralOption,
      topK: this.normalizedTopK(this.evaluationTopKValue, this.evaluationSourceFiles.length),
      thresholdBeta: 2
    };
  }

  private isValidModelOptions(classifier: ClassifierType, knnValue: number, svmC: number): boolean {
    return classifier === 'knn'
      ? Number.isFinite(knnValue) && knnValue >= 1
      : Number.isFinite(svmC) && svmC > 0;
  }

  private isSvmConfiguration(configuration: ModelConfiguration | undefined): boolean {
    return configuration?.classifierType === 'svm'
      || configuration?.classifier?.toLowerCase().includes('svm') === true;
  }

  private hasExpectedClassifier(
    result: PredictionResult | EvaluationResult,
    expected: ClassifierType
  ): boolean {
    const configuration = result.modelConfiguration;
    if (configuration?.classifierType) {
      return configuration.classifierType === expected;
    }

    const description = `${configuration?.classifier ?? ''} ${result.method ?? ''}`.toLowerCase();
    if (description.includes('svm')) return expected === 'svm';
    if (description.includes('knn')) return expected === 'knn';
    return true;
  }

  private classifierMismatchMessage(expected: ClassifierType): string {
    const expectedName = expected === 'svm' ? 'Linear SVM' : 'KNN';
    return `The server returned a different classifier instead of ${expectedName}. Restart the Java backend so it forwards the selected model to the updated ML service, then run again.`;
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

    for (const line of lines.slice(1)) {
      const values = this.parseCsvLine(line);
      const className = this.canonicalClassName(values[classIndex] ?? '');
      const labelValue = (values[labelIndex] ?? '').trim();
      if (!className || !labelValue) continue;

      const actualBuggy = this.parseActualBugLabel(labelValue, labelColumn);
      const existingLabel = exactLabels.get(className);
      if (typeof existingLabel === 'boolean' && existingLabel !== actualBuggy) {
        throw new Error(`Class '${className}' has conflicting labels in the labelled target CSV.`);
      }
      exactLabels.set(className, actualBuggy);
    }

    if (!exactLabels.size) {
      throw new Error('No labelled classes were found in the target CSV.');
    }

    const rows: ModelTestRow[] = [];
    const predictionClassNames = new Set<string>();
    let truePositive = 0;
    let trueNegative = 0;
    let falsePositive = 0;
    let falseNegative = 0;

    for (const item of predictions) {
      const className = this.canonicalClassName(item.class);
      if (!className) continue;
      if (predictionClassNames.has(className)) {
        throw new Error(`Prediction result contains duplicate class '${className}'.`);
      }
      predictionClassNames.add(className);

      const actualBuggy = exactLabels.get(className);
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
    const precision = this.safeDivide(truePositive, truePositive + falsePositive);
    const recall = this.safeDivide(truePositive, truePositive + falseNegative);
    const specificity = this.safeDivide(trueNegative, trueNegative + falsePositive);
    const f1 = this.safeDivide(2 * precision * recall, precision + recall);
    const f2 = this.safeDivide(5 * precision * recall, (4 * precision) + recall);
    const mccDenominator = Math.sqrt(
      (truePositive + falsePositive)
      * (truePositive + falseNegative)
      * (trueNegative + falsePositive)
      * (trueNegative + falseNegative)
    );
    return {
      totalPredictions: predictions.length,
      labelledRows: exactLabels.size,
      matchedRows: rows.length,
      unmatchedPredictions: predictions.length - rows.length,
      correct,
      accuracy: correct / rows.length,
      balancedAccuracy: (recall + specificity) / 2,
      precision,
      recall,
      specificity,
      f1,
      f2,
      mcc: mccDenominator
        ? ((truePositive * trueNegative) - (falsePositive * falseNegative)) / mccDenominator
        : 0,
      truePositive,
      trueNegative,
      falsePositive,
      falseNegative,
      rows
    };
  }

  private safeDivide(numerator: number, denominator: number): number {
    return denominator ? numerator / denominator : 0;
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

  private canonicalClassName(className: string): string {
    return className.trim().replace(/\$/g, '.');
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
