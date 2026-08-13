import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

@Component({
  selector: 'app-datasets',
  standalone: false,
  templateUrl: './datasets.component.html'
})
export class DatasetsComponent implements OnInit {
  datasets: DatasetSummary[] = [];
  loading = true;
  loadError = '';
  search = '';
  searchOpen = false;
  familyFilter = '';
  originFilter = '';

  readonly familyFilterOptions: SelectOption[] = [
    { value: '', label: 'All families' },
    { value: 'PROMISE', label: 'PROMISE' },
    { value: 'AEEEM', label: 'AEEEM' }
  ];

  readonly originFilterOptions: SelectOption[] = [
    { value: '', label: 'All origins' },
    { value: 'PREDEFINED', label: 'Predefined dataset' },
    { value: 'MANUAL', label: 'Manually extracted' }
  ];

  @ViewChild('searchInput') private readonly searchInputRef?: ElementRef<HTMLInputElement>;

  readonly columns: TableColumn[] = [
    { key: 'projectName', label: 'Project Name', sticky: 'start', width: '28%' },
    { key: 'datasetFamily', label: 'Metrics Family', width: '10%' },
    { key: 'datasetType', label: 'Data Source', width: '14%' },
    { key: 'hasActualLabel', label: 'Labeled', width: '10%' },
    { key: 'totalFiles', label: 'Instances', align: 'right', width: '8%' },
    { key: 'totalMetrics', label: 'Features', align: 'right', width: '9%' },
    { key: 'createdAt', label: 'Created At', width: '13%' },
    { key: 'actions', label: 'Actions', sticky: 'end', className: 'dl-col-actions', width: '8%' }
  ];

  constructor(private readonly api: DefectLabApiService) {}

  ngOnInit(): void { this.load(); }

  get filtered(): DatasetSummary[] {
    const query = this.search.trim().toLowerCase();
    return this.datasets.filter(item =>
      (!query || `${item.projectName} ${item.projectVersion || ''}`.toLowerCase().includes(query)) &&
      (!this.familyFilter || item.datasetFamily === this.familyFilter) &&
      (!this.originFilter || item.datasetType === this.originFilter));
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.listDatasets().subscribe({
      next: rows => {
        this.datasets = rows;
        this.loading = false;
      },
      error: error => {
        this.loadError = error?.error?.error ?? 'Could not load metric storage.';
        this.loading = false;
      }
    });
  }

  openSearch(): void {
    this.searchOpen = true;
    setTimeout(() => this.searchInputRef?.nativeElement.focus());
  }

  closeSearch(): void {
    this.searchOpen = false;
    this.search = '';
  }

  countFamily(family: DatasetFamily): number {
    return this.datasets.filter(item => item.datasetFamily === family).length;
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
  }

  onOriginFilterChange(value: string | number | null): void {
    this.originFilter = (value as string) ?? '';
  }
}
