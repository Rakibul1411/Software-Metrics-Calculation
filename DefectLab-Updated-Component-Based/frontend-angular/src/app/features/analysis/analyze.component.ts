import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { PendingExtractionService } from '../../core/services/pending-extraction.service';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ToastService } from '../../shared/ui-toast/toast.service';

@Component({
  selector: 'app-analyze',
  standalone: false,
  templateUrl: './analyze.component.html'
})
export class AnalyzeComponent {
  mode: 'archive' | 'github' = 'archive';
  family: DatasetFamily = 'PROMISE';

  readonly familyOptions: RadioOption[] = [
    { value: 'PROMISE', label: 'PROMISE · 20 metrics' },
    { value: 'AEEEM', label: 'AEEEM · 56 metrics' }
  ];
  projectName = '';
  projectVersion = '';
  archive: File | null = null;
  githubUrl = '';
  aeeemProfile = 'current';
  readonly aeeemProfileOptions: SelectOption[] = [
    { value: 'current', label: 'Custom (manual entry)' },
    { value: 'jdt', label: 'JDT 3.4' },
    { value: 'eq', label: 'Equinox 3.4' },
    { value: 'pde', label: 'PDE UI 3.4.1' },
    { value: 'lc', label: 'Lucene 2.4.0' },
    { value: 'ml', label: 'Mylyn 3.1' }
  ];
  busy = false;
  created: DatasetSummary | null = null;

  constructor(
    private readonly api: DefectLabApiService,
    private readonly toast: ToastService,
    private readonly router: Router,
    private readonly pendingExtraction: PendingExtractionService
  ) {}

  get modeOptions(): RadioOption[] {
    return [
      { value: 'archive', label: 'Local archive', disabled: this.family === 'AEEEM' },
      { value: 'github', label: 'GitHub repository' }
    ];
  }

  onFamilyChange(value: string): void {
    this.selectFamily(value as DatasetFamily);
  }

  onModeChange(value: string): void {
    this.selectMode(value as 'archive' | 'github');
  }

  selectFamily(family: DatasetFamily): void {
    this.family = family;
    if (family === 'AEEEM') {
      this.mode = 'github';
    }
  }

  selectMode(mode: 'archive' | 'github'): void {
    this.mode = this.family === 'AEEEM' ? 'github' : mode;
  }

  selectArchive(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selected = input.files?.[0] ?? null;
    if (!selected) {
      this.archive = null;
      return;
    }
    const filename = selected.name.toLowerCase();
    const supported = ['.zip', '.tar', '.tgz', '.tar.gz', '.gz']
      .some(extension => filename.endsWith(extension));
    if (!supported) {
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
    const benchmarks: Record<string, { name: string; version: string; url: string }> = {
      jdt: {
        name: 'JDT',
        version: '3.4',
        url: 'https://github.com/eclipse-jdt/eclipse.jdt.core'
      },
      eq: {
        name: 'EQ',
        version: '3.4',
        url: 'https://github.com/eclipse-equinox/equinox.framework'
      },
      pde: {
        name: 'PDE',
        version: '3.4.1',
        url: 'https://github.com/eclipse-pde/eclipse.pde'
      },
      lc: {
        name: 'LC',
        version: '2.4.0',
        url: 'https://github.com/apache/lucene'
      },
      ml: {
        name: 'ML',
        version: '3.1',
        url: 'https://github.com/eclipse-mylyn/org.eclipse.mylyn'
      }
    };
    const selected = benchmarks[profile];
    if (!selected) {
      this.projectName = '';
      this.projectVersion = '';
      this.githubUrl = '';
      return;
    }
    this.projectName = selected.name;
    this.projectVersion = selected.version;
    this.githubUrl = selected.url;
  }

  cancel(): void {
    this.projectName = '';
    this.projectVersion = '';
    this.archive = null;
    this.githubUrl = '';
    this.aeeemProfile = 'current';
  }

  canSubmit(): boolean {
    if (!this.projectVersion.trim()) return false;
    if (this.mode === 'archive') {
      return !!this.archive && this.family === 'PROMISE';
    }
    return this.githubUrl.trim().startsWith('https://github.com/');
  }

  submit(): void {
    if (!this.canSubmit()) return;
    this.busy = true;
    this.created = null;
    // Extraction runs synchronously on the backend and can take a while
    // (AEEEM especially). If the user refreshes or navigates away before
    // this request returns, the backend keeps working -- this marker lets
    // us tell them once it's done even though this page is gone by then.
    this.pendingExtraction.start(this.family, this.projectName, this.projectVersion);
    const request = this.mode === 'archive'
      ? this.api.analyzeArchive({
          file: this.archive!,
          projectName: this.projectName,
          projectVersion: this.projectVersion,
          family: this.family
        })
      : this.api.analyzeGitHub({
          githubUrl: this.githubUrl,
          projectName: this.projectName,
          projectVersion: this.projectVersion,
          family: this.family,
          aeeemProfile: this.aeeemProfile
        });
    request.subscribe({
      next: dataset => {
        this.created = dataset;
        this.busy = false;
        this.pendingExtraction.clear();
        this.toast.success('Dataset analyzed and saved successfully.');
        this.router.navigate(['/datasets']);
      },
      error: () => {
        this.busy = false;
        this.pendingExtraction.clear();
      }
    });
  }
}
