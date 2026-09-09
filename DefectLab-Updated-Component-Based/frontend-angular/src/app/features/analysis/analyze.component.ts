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

  readonly familyOptions = FAMILY_RADIO_OPTIONS;

  private readonly pendingExtraction = inject(PendingExtractionService);

  constructor(readonly facade: AnalysisFacade) {
    super();
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
    this.projectName = '';
    this.projectVersion = '';
    this.archive = null;
    this.githubUrl = '';
    this.aeeemProfile = 'current';
  }

  canSubmit(): boolean {
    return this.facade.canSubmit(this.request);
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    // Extraction runs synchronously on the backend and can take a while
    // (AEEEM especially). If the user refreshes or navigates away before
    // this request returns, the backend keeps working -- this marker lets
    // us tell them once it's done even though this page is gone by then.
    this.pendingExtraction.start(this.family, this.projectName, this.projectVersion);
    this.submitWith(this.facade.analyze(this.request), {
      success: 'Dataset analyzed and saved successfully.',
      redirect: ['/datasets'],
      onSettled: () => this.pendingExtraction.clear()
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
