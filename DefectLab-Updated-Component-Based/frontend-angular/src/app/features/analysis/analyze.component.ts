import { Component } from '@angular/core';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-analyze',
  standalone: false,
  template: `
    <section class="dl-grid dl-grid-3 dl-analyze-modes">
      <article class="dl-card dl-action-card"
               [class.dl-data-card]="mode === 'archive'"
               [class.dl-disabled]="family === 'AEEEM'"
               (click)="selectMode('archive')">
        <span class="dl-card-index">01</span>
        <h2>Source code archive</h2>
        <p>Upload one Java release as ZIP, TAR, TGZ, TAR.GZ or GZ. Available for PROMISE.</p>
      </article>
      <article class="dl-card dl-action-card"
               [class.dl-data-card]="mode === 'github'"
               (click)="selectMode('github')">
        <span class="dl-card-index">02</span>
        <h2>GitHub repository</h2>
        <p>Clone a public repository. Required for AEEEM history features.</p>
      </article>
      <article class="dl-card dl-action-card">
        <span class="dl-card-index">03</span>
        <h2>Saved automatically</h2>
        <p>The generated CSV is registered as MANUAL and stays unlabeled.</p>
      </article>
    </section>

    <section class="dl-grid dl-grid-2 dl-analyze-layout">
      <article class="dl-card dl-analyze-form-card">
        <div class="dl-card-head">
          <div>
            <span class="dl-card-kicker">Input</span>
            <h2>{{ mode === 'archive' ? 'Analyze Java archive' : 'Analyze GitHub repository' }}</h2>
            <p>Project name is derived from the file/repository if left blank. Version is supplied by you.</p>
          </div>
        </div>

        <form class="dl-form-stack" (ngSubmit)="submit()">
          <div class="dl-form-row">
            <label class="dl-field">
              <span>Metric family</span>
              <select [(ngModel)]="family" name="family"
                      (ngModelChange)="selectFamily($event)">
                <option value="PROMISE">PROMISE · 20 static predictors</option>
                <option value="AEEEM">AEEEM · 56 static/history predictors</option>
              </select>
            </label>
            <label class="dl-field">
              <span>Source input</span>
              <select [(ngModel)]="mode" name="sourceMode"
                      (ngModelChange)="selectMode($event)">
                <option value="archive" [disabled]="family === 'AEEEM'">
                  Local source archive
                </option>
                <option value="github">Public GitHub repository</option>
              </select>
            </label>
          </div>

          <div class="dl-form-row">
            <label class="dl-field">
              <span>Project version *</span>
              <input [(ngModel)]="projectVersion" name="projectVersion"
                     required placeholder="Example: 1.7 or 3.4">
            </label>
            <label class="dl-field">
              <span>Project name (optional)</span>
              <input [(ngModel)]="projectName" name="projectName"
                     placeholder="Automatically derived when empty">
            </label>
          </div>

          <ng-container *ngIf="mode === 'archive'">
            <label class="dl-file-panel">
              <input class="dl-sr-only" type="file"
                     accept=".zip,.tar,.tgz,.tar.gz,.gz,application/zip,application/x-tar,application/gzip"
                     (change)="selectArchive($event)">
              <strong>{{ archive?.name || 'Choose a Java source archive' }}</strong>
              <span>ZIP, TAR, TGZ, TAR.GZ or GZ · maximum 50 MB</span>
              <span class="dl-btn dl-btn-ghost dl-btn-sm">Browse file</span>
            </label>
          </ng-container>

          <ng-container *ngIf="mode === 'github'">
            <label class="dl-field">
              <span>Public GitHub URL *</span>
              <input [(ngModel)]="githubUrl" name="githubUrl"
                     placeholder="https://github.com/owner/repository">
            </label>
            <label class="dl-field" *ngIf="family === 'AEEEM'">
              <span>AEEEM benchmark profile</span>
              <select [(ngModel)]="aeeemProfile" name="aeeemProfile"
                      (ngModelChange)="applyAeeemProfile($event)">
                <option value="current">Current repository history</option>
                <option value="jdt">JDT 3.4</option>
                <option value="eq">Equinox 3.4</option>
                <option value="pde">PDE UI 3.4.1</option>
                <option value="lc">Lucene 2.4.0</option>
                <option value="ml">Mylyn 3.1</option>
              </select>
            </label>
          </ng-container>

          <div *ngIf="error" class="dl-alert dl-alert-error">{{ error }}</div>
          <button class="dl-btn" type="submit" [disabled]="busy || !canSubmit()">
            <span *ngIf="busy" class="dl-spinner"></span>
            {{ busy ? 'Calculating metrics…' : 'Calculate and store metrics' }}
          </button>
        </form>
      </article>

      <article class="dl-card dl-analyze-pipeline-card">
        <div class="dl-card-head">
          <div>
            <span class="dl-card-kicker">What happens</span>
            <h2>Extraction pipeline</h2>
            <p>Every stage is deterministic and documented.</p>
          </div>
        </div>
        <div class="dl-step-overview dl-analyze-steps">
          <div class="dl-step"><span class="dl-step-order">1</span><div><strong>Validate input</strong><p>Archive safety or GitHub URL checks.</p></div></div>
          <div class="dl-step"><span class="dl-step-order">2</span><div><strong>Parse Java</strong><p>Eclipse JDT resolves source and bindings.</p></div></div>
          <div class="dl-step"><span class="dl-step-order">3</span><div><strong>Calculate metrics</strong><p>CK/PROMISE or CK + WCHU + LDHH + entropy.</p></div></div>
          <div class="dl-step"><span class="dl-step-order">4</span><div><strong>Persist CSV</strong><p>File storage plus one metric_datasets row.</p></div></div>
        </div>

        <div *ngIf="created as item" class="dl-alert dl-alert-success">
          <strong>{{ item.displayName }} is ready.</strong><br>
          {{ item.totalFiles | number }} classes · {{ item.totalMetrics }} features ·
          {{ item.datasetFamily }}
          <div class="dl-actions" style="margin-top:12px">
            <a class="dl-btn dl-btn-sm" routerLink="/predictions">Use in prediction</a>
            <a class="dl-btn dl-btn-ghost dl-btn-sm" routerLink="/datasets">Open storage</a>
          </div>
        </div>
      </article>
    </section>
  `
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
