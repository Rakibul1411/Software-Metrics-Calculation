import { Component, OnInit } from '@angular/core';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';
import { DatasetsFacade } from './datasets.facade';

@Component({
  selector: 'app-datasets',
  standalone: false,
  templateUrl: './datasets.component.html'
})
export class DatasetsComponent implements OnInit {
  loading = true;
  search = '';
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

  constructor(readonly facade: DatasetsFacade) {}

  ngOnInit(): void { this.load(); }

  get filtered(): DatasetSummary[] {
    return this.facade.filterList({
      search: this.search, family: this.familyFilter, origin: this.originFilter
    });
  }

  load(): void {
    this.loading = true;
    this.facade.loadList().subscribe({
      next: () => { this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  countFamily(family: DatasetFamily): number {
    return this.facade.countByFamily(family);
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
  }

  onOriginFilterChange(value: string | number | null): void {
    this.originFilter = (value as string) ?? '';
  }
}
