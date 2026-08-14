import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PredictionRunGroup } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

@Component({
  selector: 'app-reports',
  standalone: false,
  templateUrl: './reports.component.html'
})
export class ReportsComponent implements OnInit {
  groups: PredictionRunGroup[] = [];
  error = '';

  readonly columns: TableColumn[] = [
    { key: 'group', label: 'Group', sticky: 'start', className: 'dl-mono' },
    { key: 'source', label: 'Source' },
    { key: 'targets', label: 'Targets' },
    { key: 'model', label: 'Model' },
    { key: 'runs', label: 'Runs', align: 'right' },
    { key: 'created', label: 'Created at' },
    { key: 'view', label: 'View', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

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
