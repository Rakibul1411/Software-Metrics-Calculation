import { Component, OnInit } from '@angular/core';
import { forkJoin, of, timer, Observable } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { BaseFormComponent } from '../../core/base';
import { FAMILY_FILTER_OPTIONS } from '../../core/constants/dataset-filter.options';
import {
  DatasetFamily,
  DatasetSummary,
  DatasetType,
  MetricComparisonPair
} from '../../core/models/defectlab.model';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { DatasetsFacade } from '../datasets/datasets.facade';
import { ComparisonsFacade } from './comparisons.facade';

@Component({
  selector: 'app-comparison-create',
  standalone: false,
  templateUrl: './comparison-create.component.html'
})
export class ComparisonCreateComponent extends BaseFormComponent implements OnInit {
  creationMode: 'upload' | 'pair' = 'upload';

  readonly modeOptions: RadioOption[] = [
    { value: 'upload', label: 'Direct Upload' },
    { value: 'pair', label: 'Stored Pair' }
  ];

  // Direct Upload fields
  family: DatasetFamily = 'AEEEM';
  readonly familyOptions: RadioOption[] = [
    { value: 'AEEEM', label: 'AEEEM' },
    { value: 'PROMISE', label: 'PROMISE' }
  ];

  projectName = '';
  projectVersion = '';
  manualFile: File | null = null;

  predefinedSourceType: 'existing' | 'upload' = 'existing';
  readonly predefinedSourceOptions: RadioOption[] = [
    { value: 'existing', label: 'Stored Benchmark' },
    { value: 'upload', label: 'Upload Benchmark' }
  ];
  selectedPredefinedId: number | null = null;
  predefinedFile: File | null = null;

  comparisonMode: 'INSTANCE_WISE' | 'AGGREGATE' = 'INSTANCE_WISE';
  readonly comparisonModeOptions: RadioOption[] = [
    { value: 'INSTANCE_WISE', label: 'Instance-wise' },
    { value: 'AGGREGATE', label: 'Aggregate' }
  ];

  absoluteTolerance = 0.0001;
  relativeTolerance = 0.01;

  // Stored Pairs fields
  pairs: MetricComparisonPair[] = [];
  familyFilter = '';
  selectedKey = '';
  readonly familyFilterOptions = FAMILY_FILTER_OPTIONS;

  availableDatasets: DatasetSummary[] = [];

  protected override readonly listRoute = ['/metric-comparisons'];

  constructor(
    private readonly facade: ComparisonsFacade,
    private readonly datasetsFacade: DatasetsFacade
  ) {
    super();
  }

  ngOnInit(): void {
    this.load();
    this.loadDatasets();
    this.initAutoRefresh();
  }

  // --- Stored Pairs Getters ---
  get uncomparedPairs(): MetricComparisonPair[] {
    return this.pairs.filter(pair => !pair.cached);
  }

  get filteredPairs(): MetricComparisonPair[] {
    return this.uncomparedPairs.filter(
      pair => !this.familyFilter || pair.datasetFamily === this.familyFilter);
  }

  get pairOptions(): SelectOption[] {
    return this.filteredPairs.map(pair => ({ value: pair.key, label: pair.displayName }));
  }

  get selectedPair(): MetricComparisonPair | undefined {
    return this.uncomparedPairs.find(pair => pair.key === this.selectedKey);
  }

  // --- Direct Upload Predefined Options ---
  get predefinedSelectOptions(): SelectOption[] {
    return this.availableDatasets
      .filter(d => d.datasetType === 'PREDEFINED' && d.datasetFamily === this.family)
      .map(d => ({
        value: d.id,
        label: `${d.displayName || d.projectName} (${d.totalFiles} classes)`
      }));
  }

  get canSubmit(): boolean {
    if (this.creationMode === 'pair') {
      return !!this.selectedPair;
    }
    const hasManual = !!this.manualFile;
    const hasPredefined = this.predefinedSourceType === 'existing'
      ? !!this.selectedPredefinedId
      : !!this.predefinedFile;
    return hasManual && hasPredefined;
  }

  onModeChange(value: string): void {
    this.creationMode = value as 'upload' | 'pair';
  }

  onFamilyChange(value: string): void {
    this.family = value as DatasetFamily;
    this.selectedPredefinedId = null;
    const opts = this.predefinedSelectOptions;
    if (opts.length > 0) {
      this.selectedPredefinedId = opts[0].value as number;
    } else {
      this.predefinedSourceType = 'upload';
    }
  }

  onPredefinedSourceTypeChange(value: string): void {
    this.predefinedSourceType = value as 'existing' | 'upload';
  }

  onSelectedPredefinedChange(value: string | number | null): void {
    this.selectedPredefinedId = value as number | null;
  }

  onComparisonModeChange(value: string): void {
    this.comparisonMode = value as 'INSTANCE_WISE' | 'AGGREGATE';
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
    if (!this.filteredPairs.some(pair => pair.key === this.selectedKey)) {
      this.selectedKey = '';
    }
    if (!this.filteredPairs.length) {
      this.toast.info('Every eligible dataset pair has already been compared.');
    }
  }

  onSelectedKeyChange(value: string | number | null): void {
    this.selectedKey = (value as string) ?? '';
  }

  selectManualFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.manualFile = file;
    if (file) {
      if (!this.projectName) {
        this.projectName = this.inferProjectName(file.name);
      }
      if (!this.projectVersion) {
        this.projectVersion = this.inferProjectVersion(file.name);
      }
    }
  }

  selectPredefinedFile(event: Event): void {
    this.predefinedFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  load(): void {
    this.watch(this.facade.pairs()).subscribe({
      next: rows => (this.pairs = rows),
      error: () => {}
    });
  }

  loadDatasets(): void {
    this.watch(this.datasetsFacade.list()).subscribe({
      next: datasets => {
        this.availableDatasets = datasets;
        if (!this.selectedPredefinedId) {
          const opts = this.predefinedSelectOptions;
          if (opts.length > 0) {
            this.selectedPredefinedId = opts[0].value as number;
          }
        }
      },
      error: () => {}
    });
  }

  protected initAutoRefresh(intervalMs = 5000): void {
    this.watch(timer(intervalMs, intervalMs)).subscribe(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      if (this.busy) return;
      this.watch(this.facade.pairs()).subscribe({
        next: rows => {
          if (JSON.stringify(this.pairs) !== JSON.stringify(rows)) {
            this.pairs = rows;
          }
        },
        error: () => {}
      });
      this.watch(this.datasetsFacade.list()).subscribe({
        next: datasets => (this.availableDatasets = datasets),
        error: () => {}
      });
    });
  }

  run(): void {
    if (this.creationMode === 'pair') {
      const pair = this.selectedPair;
      if (!pair) return;
      this.submitWith(this.facade.run(pair), {
        success: 'Comparison completed successfully.',
        redirect: this.listRoute
      });
      return;
    }

    // Direct Upload Mode
    if (!this.manualFile) {
      this.toast.error('Please select a manual metrics file to compare.');
      return;
    }
    if (this.predefinedSourceType === 'existing' && !this.selectedPredefinedId) {
      this.toast.error('Please select a predefined benchmark dataset or choose file upload.');
      return;
    }
    if (this.predefinedSourceType === 'upload' && !this.predefinedFile) {
      this.toast.error('Please select a predefined benchmark file.');
      return;
    }

    this.busy = true;
    const pName = this.projectName.trim() || this.inferProjectName(this.manualFile.name);
    const pVersion = this.projectVersion.trim() || this.inferProjectVersion(this.manualFile.name);

    // 1. Upload manual dataset
    const manualUpload$ = this.uploadDatasetWithAutoVersion(
      this.manualFile, pName, pVersion, 'MANUAL');

    // 2. Obtain predefined dataset (upload or use existing ID)
    const predefinedUpload$ = this.predefinedSourceType === 'upload' && this.predefinedFile
      ? this.uploadDatasetWithAutoVersion(this.predefinedFile, pName, pVersion, 'PREDEFINED')
      : of({ id: this.selectedPredefinedId! } as DatasetSummary);

    forkJoin({
      manual: manualUpload$,
      predefined: predefinedUpload$
    }).pipe(
      switchMap(({ manual, predefined }) => {
        return this.facade.run({
          manualDatasetId: manual.id,
          predefinedDatasetId: predefined.id,
          comparisonMode: this.comparisonMode,
          absoluteTolerance: Number(this.absoluteTolerance) || 0.0001,
          relativeTolerance: Number(this.relativeTolerance) || 0.01
        });
      })
    ).subscribe({
      next: result => {
        this.busy = false;
        this.toast.success('Comparison completed successfully.');
        this.navigateTo(['/metric-comparisons', result.id]);
      },
      error: err => {
        this.busy = false;
        const msg = err?.error?.message || err?.message || 'Comparison failed. Please verify the files and try again.';
        this.toast.error(msg);
      }
    });
  }

  private uploadDatasetWithAutoVersion(
    file: File,
    projectName: string,
    projectVersion: string,
    type: DatasetType
  ): Observable<DatasetSummary> {
    const existing = this.availableDatasets.find(d =>
      d.datasetFamily === this.family &&
      d.datasetType === type &&
      d.projectName.toLowerCase() === projectName.toLowerCase() &&
      d.projectVersion.toLowerCase() === projectVersion.toLowerCase()
    );
    const versionToUse = existing
      ? `${projectVersion}-${Date.now().toString().slice(-4)}`
      : projectVersion;

    return this.datasetsFacade.upload({
      file,
      projectName,
      projectVersion: versionToUse,
      family: this.family,
      type
    }).pipe(
      catchError(err => {
        if (err?.status === 409) {
          return this.datasetsFacade.upload({
            file,
            projectName,
            projectVersion: `${projectVersion}-${Date.now().toString().slice(-4)}`,
            family: this.family,
            type
          });
        }
        throw err;
      })
    );
  }

  private inferProjectName(filename: string): string {
    const clean = filename.replace(/\.(csv|arff)$/i, '').toLowerCase();
    if (clean.includes('mylyn') || clean.startsWith('ml')) return 'Mylyn';
    if (clean.includes('equinox') || clean.startsWith('eq')) return 'Equinox';
    if (clean.includes('eclipse') || clean.startsWith('jdt')) return 'Eclipse JDT';
    if (clean.includes('lucene') || clean.startsWith('lc')) return 'Lucene';
    if (clean.includes('pde')) return 'PDE';
    if (clean.includes('ant')) return 'Ant';
    if (clean.includes('camel')) return 'Camel';
    if (clean.includes('ivy')) return 'Ivy';
    if (clean.includes('jedit')) return 'jEdit';
    if (clean.includes('log4j')) return 'Log4j';
    if (clean.includes('poi')) return 'POI';
    if (clean.includes('synapse')) return 'Synapse';
    if (clean.includes('velocity')) return 'Velocity';
    if (clean.includes('xalan')) return 'Xalan';
    if (clean.includes('xerces')) return 'Xerces';
    const firstWord = filename.split(/[-_.]/)[0];
    return firstWord.charAt(0).toUpperCase() + firstWord.slice(1);
  }

  private inferProjectVersion(filename: string): string {
    const match = filename.match(/(\d+\.\d+(\.\d+)?)/);
    return match ? match[1] : '1.0';
  }
}
