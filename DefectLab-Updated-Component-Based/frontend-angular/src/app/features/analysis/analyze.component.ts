import { Component, inject } from '@angular/core';
import { BaseFormComponent } from '../../core/base';
import { FAMILY_RADIO_OPTIONS } from '../../core/constants/dataset-filter.options';
import { DatasetFamily } from '../../core/models/defectlab.model';
import { PendingExtractionService } from '../../core/services/pending-extraction.service';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';
import { AnalysisFacade, AnalysisMode, AnalysisRequest } from './analysis.facade';

@Component({
  selector: 'app-analyze',
  standalone: false,
  templateUrl: './analyze.component.html'
})
export class AnalyzeComponent extends BaseFormComponent {
  mode: AnalysisMode = 'archive';
  family: DatasetFamily = 'PROMISE';
  projectName = '';
  projectVersion = '';
  archive: File | null = null;
  githubUrl = '';
  aeeemProfile = 'current';

  // Live timer tracking for backend processing
  startTime: number | null = null;
  elapsedSeconds = 0;
  private timerInterval: ReturnType<typeof setInterval> | null = null;
  completedDuration: string | null = null;
  lastExecutionDuration: string | null = null;
  lastExecutionStatus: 'idle' | 'running' | 'success' | 'error' = 'idle';

  readonly familyOptions = FAMILY_RADIO_OPTIONS;

  private readonly pendingExtraction = inject(PendingExtractionService);

  constructor(readonly facade: AnalysisFacade) {
    super();
    this.destroyRef.onDestroy(() => this.stopTimer());
  }

  get formattedTime(): string {
    const m = Math.floor(this.elapsedSeconds / 60);
    const s = this.elapsedSeconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  get formattedDuration(): string {
    const m = Math.floor(this.elapsedSeconds / 60);
    const s = this.elapsedSeconds % 60;
    if (m > 0) {
      return `${m}m ${s}s`;
    }
    return `${s}s`;
  }

  get estimatedTaskDescription(): string {
    if (this.family === 'AEEEM') {
      return 'Analyzing Git repository: parsing ASTs, calculating 56 OO metrics & mining change history across bi-weekly snapshots…';
    }
    return 'Extracting Java source classes: calculating CK metrics, McCabe complexity & OO predictor features…';
  }

  startTimer(): void {
    this.stopTimer();
    this.startTime = Date.now();
    this.elapsedSeconds = 0;
    this.completedDuration = null;
    this.lastExecutionStatus = 'running';
    this.timerInterval = setInterval(() => {
      if (this.startTime) {
        this.elapsedSeconds = Math.floor((Date.now() - this.startTime) / 1000);
      }
    }, 1000);
  }

  stopTimer(status: 'idle' | 'success' | 'error' = 'idle'): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
    if (this.startTime && this.elapsedSeconds > 0) {
      this.completedDuration = this.formattedDuration;
      this.lastExecutionDuration = this.completedDuration;
    }
    this.lastExecutionStatus = status;
  }

  get aeeemProfileOptions() {
    return this.facade.aeeemProfileOptions;
  }

  get modeOptions(): RadioOption[] {
    return [
      { value: 'archive', label: 'Local archive', disabled: !this.facade.allowsArchive(this.family) },
      { value: 'github', label: 'GitHub repository' }
    ];
  }

  onFamilyChange(value: string): void {
    this.selectFamily(value as DatasetFamily);
  }

  onModeChange(value: string): void {
    this.selectMode(value as AnalysisMode);
  }

  /** AEEEM needs Git history, so choosing it forces the GitHub source. */
  selectFamily(family: DatasetFamily): void {
    this.family = family;
    if (!this.facade.allowsArchive(family)) {
      this.mode = 'github';
    }
  }

  selectMode(mode: AnalysisMode): void {
    this.mode = this.facade.allowsArchive(this.family) ? mode : 'github';
  }

  selectArchive(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selected = input.files?.[0] ?? null;
    if (!selected) {
      this.archive = null;
      return;
    }
    if (!this.facade.isSupportedArchive(selected)) {
      this.archive = null;
      input.value = '';
      this.toast.error('Choose a ZIP, TAR, TGZ, TAR.GZ or GZ source archive.');
      return;
    }
    this.archive = selected;
  }

  onAeeemProfileChange(value: string | number | null): void {
    this.aeeemProfile = value as string;
    this.applyAeeemProfile(this.aeeemProfile);
  }

  applyAeeemProfile(profile: string): void {
    const preset = this.facade.presetFor(profile);
    this.projectName = preset.name;
    this.projectVersion = preset.version;
    this.githubUrl = preset.url;
  }

  cancel(): void {
    this.stopTimer('idle');
    this.projectName = '';
    this.projectVersion = '';
    this.archive = null;
    this.githubUrl = '';
    this.aeeemProfile = 'current';
    this.completedDuration = null;
    this.lastExecutionDuration = null;
    this.lastExecutionStatus = 'idle';
  }

  canSubmit(): boolean {
    return this.facade.canSubmit(this.request);
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.startTimer();
    this.pendingExtraction.start(this.family, this.projectName, this.projectVersion);
    this.submitWith(this.facade.analyze(this.request), {
      success: () => `Dataset analyzed and saved successfully in ${this.formattedDuration}.`,
      redirect: ['/datasets'],
      onSuccess: () => {
        this.stopTimer('success');
      },
      onSettled: () => {
        if (this.lastExecutionStatus !== 'success') {
          this.stopTimer('error');
        }
        this.pendingExtraction.clear();
      }
    });
  }

  private get request(): AnalysisRequest {
    return {
      mode: this.mode,
      family: this.family,
      projectName: this.projectName,
      projectVersion: this.projectVersion,
      archive: this.archive,
      githubUrl: this.githubUrl,
      aeeemProfile: this.aeeemProfile
    };
  }
}
