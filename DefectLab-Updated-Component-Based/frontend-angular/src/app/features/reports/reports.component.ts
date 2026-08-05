import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PredictionRunGroup } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-reports',
  standalone: false,
  template: `
    <section class="dl-card">
      <div class="dl-card-head"><div><h2>Prediction reports</h2>
        <p>Only complete MANUAL + PREDEFINED comparison groups appear here.</p></div>
        <button class="dl-btn dl-btn-ghost dl-btn-sm" type="button" (click)="load()">Refresh</button>
      </div>
      <div *ngIf="error" class="dl-alert dl-alert-error">{{ error }}</div>
      <div class="dl-table-wrap" *ngIf="groups.length; else empty">
        <table class="dl-table"><thead><tr>
          <th>Group</th><th>Source</th><th>Targets</th><th>Model</th>
          <th>Runs</th><th>Created</th><th>View</th>
        </tr></thead>
          <tbody><tr *ngFor="let group of groups">
            <td class="dl-mono">{{ group.comparisonGroupId || groupKey(group) }}</td>
            <td>{{ group.runs[0].sourceDataset.displayName }}</td>
            <td>{{ targetNames(group) }}</td>
            <td>{{ group.runs[0].modelConfig.modelName }}</td>
            <td>{{ group.runs.length }}</td>
            <td>{{ group.runs[0].createdAt | date:'medium' }}</td>
            <td>
              <button class="dl-btn dl-btn-ghost dl-btn-sm dl-view-button"
                      type="button" title="View report details"
                      aria-label="View report details"
                      (click)="view(group)">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Zm9.5 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"/>
                </svg>
              </button>
            </td>
          </tr></tbody>
        </table>
      </div>
      <ng-template #empty><div class="dl-empty">
        No complete dual-target prediction reports yet.
      </div></ng-template>
    </section>
  `
})
export class ReportsComponent implements OnInit {
  groups: PredictionRunGroup[] = [];
  error = '';

  constructor(
    readonly api: DefectLabApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.listPredictionGroups().subscribe({
      next: rows => this.groups = rows.filter(group => this.isCompleteGroup(group)),
      error: error => this.error = error?.error?.error ?? 'Could not load reports.'
    });
  }

  groupKey(group: PredictionRunGroup): string {
    return group.comparisonGroupId || `run-${group.runs[0].id}`;
  }

  targetNames(group: PredictionRunGroup): string {
    return group.runs
      .map(run => `${run.targetDataset.displayName} (${run.targetDataset.datasetType})`)
      .join(' + ');
  }

  view(group: PredictionRunGroup): void {
    this.router.navigate(['/reports', this.groupKey(group)]);
  }

  private isCompleteGroup(group: PredictionRunGroup): boolean {
    return !!group.comparisonGroupId &&
      group.runs.some(run => run.targetDataset.datasetType === 'MANUAL') &&
      group.runs.some(run => run.targetDataset.datasetType === 'PREDEFINED');
  }
}
