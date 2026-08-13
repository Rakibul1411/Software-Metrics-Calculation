import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { PredictionRunSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

@Component({
  selector: 'app-predictions',
  standalone: false,
  templateUrl: './predictions.component.html'
})
export class PredictionsComponent implements OnInit {
  runs: PredictionRunSummary[] = [];
  error = '';
  search = '';
  searchOpen = false;

  @ViewChild('searchInput') private readonly searchInputRef?: ElementRef<HTMLInputElement>;

  readonly runsColumns: TableColumn[] = [
    { key: 'run', label: 'Run ID', sticky: 'start', className: 'dl-mono' },
    { key: 'group', label: 'Comparison Group', className: 'dl-mono' },
    { key: 'target', label: 'Target Dataset' },
    { key: 'type', label: 'Data Source' },
    { key: 'model', label: 'Model' },
    { key: 'buggy', label: 'Predicted Buggy', align: 'right' },
    { key: 'created', label: 'Created At' },
    { key: 'inspect', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(private readonly api: DefectLabApiService) {}

  ngOnInit(): void { this.load(); }

  get filtered(): PredictionRunSummary[] {
    const query = this.search.trim().toLowerCase();
    return this.runs.filter(run =>
      !query || run.targetDataset.displayName.toLowerCase().includes(query));
  }

  openSearch(): void {
    this.searchOpen = true;
    setTimeout(() => this.searchInputRef?.nativeElement.focus());
  }

  closeSearch(): void {
    this.searchOpen = false;
    this.search = '';
  }

  load(): void {
    this.api.listPredictionRuns().subscribe({
      next: rows => this.runs = rows,
      error: error => this.error = error?.error?.error ?? 'Could not load prediction runs.'
    });
  }

  setting(run: PredictionRunSummary): string {
    return `K=${run.modelConfig.k}`;
  }
}
