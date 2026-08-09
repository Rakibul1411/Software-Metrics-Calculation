import { Component } from '@angular/core';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-analyze',
  standalone: false,
  templateUrl: './analyze.component.html'
})
export class AnalyzeComponent {
  mode: 'archive' | 'github' = 'archive';
  family: DatasetFamily = 'PROMISE';
  projectName = '';
  projectVersion = '';
  archive: File | null = null;
  githubUrl = '';
  aeeemProfile = 'current';
  busy = false;
  error = '';
  created: DatasetSummary | null = null;

  constructor(private readonly api: DefectLabApiService) {}

  selectFamily(family: DatasetFamily): void {
    this.family = family;
    this.error = '';
    if (family === 'AEEEM') {
      this.mode = 'github';
    }
  }

  selectMode(mode: 'archive' | 'github'): void {
    this.error = '';
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
      this.error = 'Choose a ZIP, TAR, TGZ, TAR.GZ or GZ source archive.';
      return;
    }
    this.error = '';
    this.archive = selected;
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
    if (!selected) return;
    this.projectName = selected.name;
    this.projectVersion = selected.version;
    this.githubUrl = selected.url;
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
    this.error = '';
    this.created = null;
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
      },
      error: error => {
        this.error = error?.error?.error ?? 'Metric extraction failed.';
        this.busy = false;
      }
    });
  }
}
