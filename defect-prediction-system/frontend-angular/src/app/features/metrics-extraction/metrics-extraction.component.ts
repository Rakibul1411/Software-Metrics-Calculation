import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy } from '@angular/core';
import { finalize } from 'rxjs/operators';

import { MetricsPreview } from '../../core/models/metrics-preview.model';
import {
  ClassifierType,
  EvaluationMetrics,
  EvaluationResult,
  ModelConfiguration,
  PredictionRequestOptions,
  PredictionResult,
  PredictionResultItem,
  SourceRankingItem
} from '../../core/models/prediction-result.model';
import { MetricsApiService } from '../../core/services/metrics-api.service';
import { PredictionApiService } from '../../core/services/prediction-api.service';

type DatasetFormat = 'promise' | 'aeeem';
type SourceMode = 'zip' | 'folder' | 'github';
type ModelMode = 'predict' | 'evaluate';
type AeeemProfileId = 'current' | 'jdt' | 'pde' | 'eq' | 'ml' | 'lc';

interface AeeemProfileOption {
  id: AeeemProfileId;
  name: string;
  period: string;
  repositoryUrl?: string;
}

const MAX_ZIP_BYTES = 50 * 1024 * 1024;

/** Archive formats ZipExtractionService can unpack. */
const SUPPORTED_ARCHIVE_EXTENSIONS = ['.zip', '.tar.gz', '.tgz'];

/** Matches the folder-upload ceiling enforced by FileStorageService. */
const MAX_FOLDER_BYTES = 250 * 1024 * 1024;
const DEFAULT_TOP_K = 3;

@Component({
  selector: 'app-metrics-extraction',
  standalone: false,
  templateUrl: './metrics-extraction.component.html',
  styleUrls: ['./metrics-extraction.component.css']
})
export class MetricsExtractionComponent implements OnDestroy {
  readonly aeeemProfiles: AeeemProfileOption[] = [
    { id: 'current', name: 'Current repository', period: 'Latest 26 bi-weekly snapshots' },
    {
      id: 'jdt',
      name: 'JDT 3.4',
      period: '2005-01-01 to 2008-06-17',
      repositoryUrl: 'https://github.com/eclipse-jdt/eclipse.jdt.core'
    },
    {
      id: 'pde',
      name: 'PDE UI 3.4.1',
      period: '2005-01-01 to 2008-09-11',
      repositoryUrl: 'https://github.com/eclipse-pde/eclipse.pde'
    },
    {
      id: 'eq',
      name: 'Equinox 3.4',
      period: '2005-01-01 to 2008-06-25',
      repositoryUrl: 'https://github.com/eclipse-equinox/equinox.framework'
    },
    {
      id: 'ml',
      name: 'Mylyn 3.1',
      period: '2005-01-17 to 2009-03-17',
      repositoryUrl: 'https://github.com/eclipse-mylyn/org.eclipse.mylyn'
    },
    {
      id: 'lc',
      name: 'Lucene 2.4',
      period: '2005-01-01 to 2008-10-08',
      repositoryUrl: 'https://github.com/apache/lucene'
    }
  ];

  datasetFormat: DatasetFormat = 'promise';
  sourceMode: SourceMode = 'zip';
  aeeemProfile: AeeemProfileId = 'current';
  selectedZip: File | null = null;
  githubUrl = '';
  dragActive = false;
  labelFilterCsv: File | null = null;
  folderFiles: File[] = [];
  folderName = '';

  extractionLoading = false;
  extractionElapsedSeconds = 0;
  extractionError: string | null = null;
  result: MetricsPreview | null = null;

  modelMode: ModelMode = 'predict';
  sourceFiles: File[] = [];
  evaluationTargetFile: File | null = null;
  labelColumn = 'bug';
  classifierType: ClassifierType = 'knn';
  topK = DEFAULT_TOP_K;
  shallowCoralEnabled = true;
  modelLoading = false;
  modelError: string | null = null;
  labelNotice: string | null = null;
  labelValidationError: string | null = null;
  labelInspectionPending = false;
  predictionResult: PredictionResult | null = null;
  evaluationResult: EvaluationResult | null = null;

  private extractionTimer?: ReturnType<typeof setInterval>;
  private sourceHeaders = new Map<File, string[]>();
  private evaluationTargetHeaders: string[] = [];

  constructor(
    private readonly metricsApi: MetricsApiService,
    private readonly predictionApi: PredictionApiService
  ) {}

  ngOnDestroy(): void {
    this.stopExtractionTimer();
  }

  setDatasetFormat(format: DatasetFormat): void {
    if (this.datasetFormat === format) {
      return;
    }

    this.datasetFormat = format;
    this.sourceMode = format === 'aeeem' ? 'github' : 'zip';
    this.labelColumn = format === 'aeeem' ? 'class' : 'bug';
    this.selectedZip = null;
    this.githubUrl = '';
    this.labelFilterCsv = null;
    this.folderFiles = [];
    this.folderName = '';
    this.sourceFiles = [];
    this.evaluationTargetFile = null;
    this.clearLabelValidation();
    this.clearExtraction();
    this.clearModelResults();
  }

  onAeeemProfileChange(profileId: AeeemProfileId): void {
    this.aeeemProfile = profileId;
    this.githubUrl = this.selectedAeeemProfile.repositoryUrl ?? '';
    this.extractionError = null;
    this.clearExtraction();
  }

  setSourceMode(mode: SourceMode): void {
    // AEEEM needs repository history, so only the GitHub source applies.
    if (this.datasetFormat === 'aeeem' && mode !== 'github') {
      return;
    }
    this.sourceMode = mode;
    this.extractionError = null;
    this.clearExtraction();
  }

  setModelMode(mode: ModelMode): void {
    if (this.modelMode === mode) {
      return;
    }
    this.modelMode = mode;
    this.modelError = null;
    this.refreshLabelValidation(false);
    this.clearModelResults();
  }

  onZipChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.acceptZip(input.files?.item(0) ?? null);
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
    this.clearExtraction();
  }

  onFolderChange(event: Event): void {
    const picked = Array.from((event.target as HTMLInputElement).files ?? []);
    this.clearExtraction();
    this.folderFiles = picked.filter(file => file.name.toLowerCase().endsWith('.java'));
    this.folderName = this.rootFolderName(picked);

    if (picked.length > 0 && this.folderFiles.length === 0) {
      this.extractionError = 'The selected folder does not contain any .java files.';
      return;
    }
    const totalBytes = this.folderFiles.reduce((sum, file) => sum + file.size, 0);
    if (totalBytes > MAX_FOLDER_BYTES) {
      this.folderFiles = [];
      this.extractionError = 'The selected folder exceeds the 250 MB Java source limit.';
    }
  }

  removeFolder(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.folderFiles = [];
    this.folderName = '';
    this.clearExtraction();
  }

  private rootFolderName(files: File[]): string {
    const path = files.find(file => file.webkitRelativePath)?.webkitRelativePath ?? '';
    return path.split('/')[0] ?? '';
  }

  onLabelFilterChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.item(0) ?? null;
    this.clearExtraction();
    if (file && !file.name.toLowerCase().endsWith('.csv')) {
      this.labelFilterCsv = null;
      this.extractionError = 'The predefined dataset must be a .csv file.';
      return;
    }
    this.labelFilterCsv = file;
  }

  removeLabelFilter(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.labelFilterCsv = null;
    this.clearExtraction();
  }

  extract(): void {
    if (!this.canExtract || this.extractionLoading) {
      return;
    }

    this.extractionLoading = true;
    this.extractionError = null;
    this.result = null;
    this.clearModelResults();
    this.startExtractionTimer();

    this.metricsApi.extractMetrics({
      datasetFormat: this.datasetFormat,
      aeeemProfile: this.datasetFormat === 'aeeem' ? this.aeeemProfile : undefined,
      projectZip: this.sourceMode === 'zip' ? this.selectedZip ?? undefined : undefined,
      githubUrl: this.sourceMode === 'github' ? this.githubUrl.trim() : undefined,
      projectFiles: this.sourceMode === 'folder' ? this.folderFiles : undefined,
      labelFilterCsv: this.labelFilterCsv ?? undefined
    }).pipe(finalize(() => {
      this.extractionLoading = false;
      this.stopExtractionTimer();
    })).subscribe({
      next: data => {
        this.result = data;
        this.modelMode = 'predict';
      },
      error: error => this.extractionError = this.describeError(error)
    });
  }

  async onSourceFilesChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.modelError = null;
    this.clearLabelValidation();
    this.clearModelResults();

    const invalid = files.filter(file => !this.isDatasetFile(file));
    if (invalid.length > 0) {
      this.sourceFiles = [];
      this.modelError = 'Source datasets must be CSV or ARFF files.';
      return;
    }
    if (!this.hasOneDatasetFormat(files)) {
      this.sourceFiles = [];
      this.modelError = 'Use one file format for every source: CSV or ARFF, not both.';
      return;
    }
    this.sourceFiles = files;
    this.topK = Math.min(DEFAULT_TOP_K, Math.max(1, files.length));
    await this.inspectSourceLabels(files);
  }

  clearSourceFiles(): void {
    this.sourceFiles = [];
    this.sourceHeaders.clear();
    this.topK = DEFAULT_TOP_K;
    this.modelError = null;
    this.clearLabelValidation();
    this.clearModelResults();
  }

  async onEvaluationTargetChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    this.modelError = null;
    this.evaluationTargetHeaders = [];
    this.clearModelResults();

    if (file && !this.isDatasetFile(file)) {
      this.evaluationTargetFile = null;
      this.modelError = 'The evaluation target must be a CSV or ARFF file.';
      return;
    }
    this.evaluationTargetFile = file;
    if (file) {
      this.labelInspectionPending = true;
      try {
        this.evaluationTargetHeaders = await this.readDatasetHeaders(file);
      } finally {
        this.labelInspectionPending = false;
      }
    }
    this.refreshLabelValidation(false);
  }

  onLabelColumnChange(): void {
    this.modelError = null;
    this.labelNotice = null;
    this.refreshLabelValidation(false);
    this.clearModelResults();
  }

  runModel(): void {
    if (!this.canRunModel || this.modelLoading) {
      return;
    }

    this.modelLoading = true;
    this.modelError = null;
    this.clearModelResults();
    const options: PredictionRequestOptions = {
      classifierType: this.classifierType,
      coralOption: this.shallowCoralEnabled,
      topK: this.topK,
      thresholdBeta: 2
    };

    if (this.modelMode === 'predict' && this.result) {
      this.predictionApi.runPrediction(
        this.result.targetDatasetId,
        this.sourceFiles,
        this.labelColumn.trim(),
        options
      ).pipe(finalize(() => this.modelLoading = false)).subscribe({
        next: data => {
          if (data.status !== 'success') {
            this.modelError = data.message ?? 'The prediction service rejected the request.';
            return;
          }
          this.predictionResult = data;
        },
        error: error => this.modelError = this.describeError(error)
      });
      return;
    }

    if (this.modelMode === 'evaluate' && this.evaluationTargetFile) {
      this.predictionApi.evaluatePrediction(
        this.evaluationTargetFile,
        this.sourceFiles,
        this.labelColumn.trim(),
        options
      ).pipe(finalize(() => this.modelLoading = false)).subscribe({
        next: data => {
          if (data.status !== 'success') {
            this.modelError = data.message ?? 'The evaluation service rejected the request.';
            return;
          }
          this.evaluationResult = data;
        },
        error: error => this.modelError = this.describeError(error)
      });
    }
  }

  downloadModelCsv(): void {
    const rows = this.predictionRows;
    if (rows.length === 0) {
      return;
    }

    const includeActual = this.modelMode === 'evaluate';
    const table = [
      ['class', 'prediction', 'risk_percent', ...(includeActual ? ['actual', 'correct'] : [])],
      ...rows.map(item => [
        item.class,
        this.isBuggy(item) ? 'buggy' : 'clean',
        this.riskPercent(item).toFixed(2),
        ...(includeActual
          ? [item.actualIsBuggy ? 'buggy' : 'clean', item.correct ? 'true' : 'false']
          : [])
      ])
    ];

    const csv = table
      .map(row => row.map(value => this.escapeCsv(String(value))).join(','))
      .join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = this.modelMode === 'evaluate'
      ? 'defect-evaluation.csv'
      : 'defect-predictions.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  get canExtract(): boolean {
    if (this.sourceMode === 'zip') {
      return this.datasetFormat === 'promise' && this.selectedZip !== null;
    }
    if (this.sourceMode === 'folder') {
      return this.datasetFormat === 'promise' && this.folderFiles.length > 0;
    }
    return /^https:\/\/github\.com\/[^/\s]+\/[^/\s]+/i.test(this.githubUrl.trim());
  }

  get canRunModel(): boolean {
    if (this.sourceFiles.length === 0 || this.topK < 1 || !this.labelColumn.trim()
        || this.labelInspectionPending || this.labelValidationError !== null) {
      return false;
    }
    if (this.modelMode === 'predict') {
      return this.result !== null;
    }
    if (!this.evaluationTargetFile) {
      return false;
    }
    return this.datasetFileFormat(this.evaluationTargetFile)
      === this.datasetFileFormat(this.sourceFiles[0]);
  }

  get extractionElapsedLabel(): string {
    const minutes = Math.floor(this.extractionElapsedSeconds / 60);
    const seconds = this.extractionElapsedSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  get selectedAeeemProfile(): AeeemProfileOption {
    return this.aeeemProfiles.find(profile => profile.id === this.aeeemProfile)
      ?? this.aeeemProfiles[0];
  }

  get previewHeaders(): string[] {
    return this.result?.csvPreview[0]
      ? this.parseCsvLine(this.result.csvPreview[0])
      : [];
  }

  get previewRows(): string[][] {
    return this.result?.csvPreview.slice(1).map(line => this.parseCsvLine(line)) ?? [];
  }

  get csvDownloadUrl(): string {
    return this.result
      ? this.metricsApi.downloadDataset(this.result.targetDatasetId, 'csv')
      : '';
  }

  get arffDownloadUrl(): string {
    return this.result
      ? this.metricsApi.downloadDataset(this.result.targetDatasetId, 'arff')
      : '';
  }

  get modelResult(): PredictionResult | EvaluationResult | null {
    return this.evaluationResult ?? this.predictionResult;
  }

  get predictionRows(): PredictionResultItem[] {
    return this.modelResult?.predictions ?? [];
  }

  get visiblePredictionRows(): PredictionResultItem[] {
    return this.predictionRows.slice(0, 100);
  }

  get evaluationMetrics(): EvaluationMetrics | null {
    return this.evaluationResult?.metrics ?? null;
  }

  get selectedSources(): SourceRankingItem[] {
    return this.modelResult?.selectedSources ?? [];
  }

  get modelConfiguration(): ModelConfiguration | undefined {
    return this.modelResult?.modelConfiguration;
  }

  get buggyCount(): number {
    if (this.predictionResult?.summary) {
      return this.predictionResult.summary.buggy;
    }
    return this.predictionRows.filter(item => this.isBuggy(item)).length;
  }

  get cleanCount(): number {
    return this.predictionRows.length - this.buggyCount;
  }

  get sourceFilesLabel(): string {
    if (this.sourceFiles.length === 0) {
      return 'Choose labelled CSV or ARFF datasets';
    }
    const format = this.datasetFileFormat(this.sourceFiles[0]).toUpperCase();
    return `${this.sourceFiles.length} ${format} source file${this.sourceFiles.length === 1 ? '' : 's'}`;
  }

  get modelParameter(): string {
    const configuration = this.modelConfiguration;
    if (configuration?.classifierType === 'svm'
        || configuration?.classifier?.includes('svm')) {
      const value = configuration.selectedC ?? configuration.selectedSvmC;
      return typeof value === 'number' ? `C = ${value}` : 'Linear SVM';
    }
    return typeof configuration?.selectedK === 'number'
      ? `K = ${configuration.selectedK}`
      : 'KNN';
  }

  get modelWarnings(): string[] {
    return this.modelResult?.modelSelection?.warnings ?? [];
  }

  isBuggy(item: PredictionResultItem): boolean {
    if (typeof item.isBuggy === 'boolean') {
      return item.isBuggy;
    }
    return item.label?.toLowerCase() === 'buggy' || Number(item.prediction) > 0;
  }

  riskPercent(item: PredictionResultItem): number {
    if (typeof item.riskPercent === 'number') {
      return item.riskPercent;
    }
    return typeof item.riskScore === 'number' ? item.riskScore * 100 : 0;
  }

  private acceptZip(file: File | null): void {
    this.extractionError = null;
    this.clearExtraction();
    if (!file) {
      this.selectedZip = null;
      return;
    }
    const name = file.name.toLowerCase();
    if (!SUPPORTED_ARCHIVE_EXTENSIONS.some(extension => name.endsWith(extension))) {
      this.selectedZip = null;
      this.extractionError =
        'Choose a .zip, .tar.gz, or .tgz archive containing one Java project release.';
      return;
    }
    if (file.size > MAX_ZIP_BYTES) {
      this.selectedZip = null;
      this.extractionError = 'The archive exceeds the 50 MB upload limit.';
      return;
    }
    this.selectedZip = file;
  }

  private clearExtraction(): void {
    this.result = null;
    this.extractionError = null;
  }

  private clearModelResults(): void {
    this.predictionResult = null;
    this.evaluationResult = null;
  }

  private async inspectSourceLabels(files: File[]): Promise<void> {
    if (files.length === 0) {
      return;
    }
    this.labelInspectionPending = true;
    try {
      const headers = await Promise.all(
        files.map(file => this.readDatasetHeaders(file))
      );
      if (this.sourceFiles !== files) {
        return;
      }
      this.sourceHeaders.clear();
      files.forEach((file, index) => this.sourceHeaders.set(file, headers[index]));
      this.refreshLabelValidation(true);
    } catch {
      if (this.sourceFiles === files) {
        this.labelValidationError =
          'The source dataset header could not be read. Check that each CSV or ARFF file is valid.';
      }
    } finally {
      if (this.sourceFiles === files) {
        this.labelInspectionPending = false;
      }
    }
  }

  private refreshLabelValidation(allowAutoDetect: boolean): void {
    if (this.sourceFiles.length === 0 || this.sourceHeaders.size === 0) {
      this.labelValidationError = null;
      return;
    }

    const requested = this.labelColumn.trim().toLowerCase();
    const datasets: Array<{ name: string; headers: string[] }> = this.sourceFiles.map(file => ({
      name: file.name,
      headers: this.sourceHeaders.get(file) ?? []
    }));
    if (this.modelMode === 'evaluate' && this.evaluationTargetFile
        && this.evaluationTargetHeaders.length > 0) {
      datasets.push({
        name: this.evaluationTargetFile.name,
        headers: this.evaluationTargetHeaders
      });
    }

    if (requested && datasets.every(dataset => dataset.headers.includes(requested))) {
      this.labelValidationError = null;
      return;
    }

    const candidates = ['class', 'bug', 'bugs', 'defect', 'defects', 'label', 'is_buggy'];
    const detected = candidates.find(candidate =>
      datasets.every(dataset => dataset.headers.includes(candidate))
    );
    if (allowAutoDetect && detected) {
      this.labelColumn = detected;
      this.labelValidationError = null;
      this.labelNotice =
        `Label column auto-detected as "${detected}" from all uploaded datasets.`;
      return;
    }

    if (detected) {
      this.labelValidationError =
        `Label column "${requested || '(empty)'}" is missing. The uploaded datasets use `
        + `"${detected}". Enter "${detected}" in Label column.`;
      return;
    }

    const missing = datasets
      .filter(dataset => !dataset.headers.includes(requested))
      .map(dataset => dataset.name);
    this.labelValidationError =
      `Label column "${requested || '(empty)'}" was not found in: ${missing.join(', ')}. `
      + 'Use the exact column name that contains defect counts or clean/buggy labels.';
  }

  private async readDatasetHeaders(file: File): Promise<string[]> {
    const text = await file.slice(0, 512 * 1024).text();
    if (file.name.toLowerCase().endsWith('.arff')
        || /^\s*@relation\b/im.test(text)) {
      const headers: string[] = [];
      for (const line of text.split(/\r?\n/)) {
        if (/^\s*@data\b/i.test(line)) {
          break;
        }
        const match = line.match(
          /^\s*@attribute\s+(?:'([^']+)'|"([^"]+)"|([^\s]+))/i
        );
        const value = match?.[1] ?? match?.[2] ?? match?.[3];
        if (value) {
          headers.push(value.trim().toLowerCase());
        }
      }
      return headers;
    }

    const headerLine = text.split(/\r?\n/).find(line => line.trim().length > 0);
    return headerLine
      ? this.parseCsvLine(headerLine).map(value => value.trim().toLowerCase())
      : [];
  }

  private clearLabelValidation(): void {
    this.sourceHeaders.clear();
    this.evaluationTargetHeaders = [];
    this.labelNotice = null;
    this.labelValidationError = null;
    this.labelInspectionPending = false;
  }

  private startExtractionTimer(): void {
    this.stopExtractionTimer();
    this.extractionElapsedSeconds = 0;
    this.extractionTimer = setInterval(() => this.extractionElapsedSeconds++, 1000);
  }

  private stopExtractionTimer(): void {
    if (this.extractionTimer) {
      clearInterval(this.extractionTimer);
      this.extractionTimer = undefined;
    }
  }

  private isDatasetFile(file: File): boolean {
    const name = file.name.toLowerCase();
    return name.endsWith('.csv') || name.endsWith('.arff');
  }

  private hasOneDatasetFormat(files: File[]): boolean {
    return new Set(files.map(file => this.datasetFileFormat(file))).size <= 1;
  }

  private datasetFileFormat(file: File): 'csv' | 'arff' {
    return file.name.toLowerCase().endsWith('.arff') ? 'arff' : 'csv';
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const payload = error.error as { error?: string; detail?: string; message?: string } | string | null;
      if (typeof payload === 'string' && payload.trim()) {
        return payload;
      }
      if (payload && typeof payload === 'object') {
        return payload.error ?? payload.detail ?? payload.message ?? error.message;
      }
      return error.message;
    }
    return error instanceof Error ? error.message : 'The request could not be completed.';
  }

  private parseCsvLine(line: string): string[] {
    const cells: string[] = [];
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
        cells.push(value);
        value = '';
      } else {
        value += character;
      }
    }
    cells.push(value);
    return cells;
  }

  private escapeCsv(value: string): string {
    return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
  }
}
