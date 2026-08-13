import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { PredictionRunDetail } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { DetailField } from '../../shared/ui-detail-fields/ui-detail-fields.model';
import { TableColumn } from '../../shared/ui-table/ui-table.model';

@Component({
  selector: 'app-prediction-detail',
  standalone: false,
  templateUrl: './prediction-detail.component.html',
  providers: [DatePipe]
})
export class PredictionDetailComponent implements OnInit {
  run: PredictionRunDetail | null = null;
  loading = true;
  error = '';

  constructor(
    readonly api: DefectLabApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly datePipe: DatePipe
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'The prediction run was not specified.';
      this.loading = false;
      return;
    }
    this.load(id);
  }

  load(id: number): void {
    this.loading = true;
    this.error = '';
    this.api.predictionRun(id).subscribe({
      next: run => {
        this.run = run;
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the prediction run.';
        this.loading = false;
      }
    });
  }

  get runFields(): DetailField[] {
    const run = this.run;
    if (!run) return [];
    return [
      { label: 'Source dataset', value: run.sourceDataset.displayName },
      { label: 'Target dataset', value: run.targetDataset.displayName },
      { label: 'Target type', value: run.targetDataset.datasetType === 'PREDEFINED' ? 'Predefined' : 'Manual extracted' },
      { label: 'Metric family', value: run.modelConfig.datasetFamily },
      { label: 'Model', value: run.modelConfig.modelName },
      { label: 'Neighbors (K)', value: run.modelConfig.k },
      { label: 'Decision threshold', value: run.modelConfig.threshold },
      { label: 'Dataset alignment', value: run.modelConfig.coral ? 'Enabled' : 'Disabled' },
      { label: 'Created', value: this.datePipe.transform(run.createdAt, 'medium') }
    ];
  }

  metric(value: { value: number | null }): string {
    return value?.value === null || value?.value === undefined
      ? 'N/A' : value.value.toFixed(3);
  }

  get predictionDetailColumns(): TableColumn[] {
    const hasActual = this.run?.targetDataset.datasetType === 'PREDEFINED';
    const columns: TableColumn[] = [
      { key: 'riskRank', label: 'Rank', align: 'right', className: 'dl-col-rank', sticky: 'start', width: '8%' },
      { key: 'classIdentifier', label: 'File / identifier', className: 'dl-col-identifier', width: hasActual ? '46%' : '58%' },
      { key: 'defectProbability', label: 'Probability', align: 'right', className: 'dl-col-probability', width: '14%' },
      { key: 'predictedLabel', label: 'Predicted', width: hasActual ? '16%' : '20%' }
    ];
    if (hasActual) {
      columns.push({ key: 'actualLabel', label: 'Actual', width: '16%' });
    }
    columns[columns.length - 1].sticky = 'end';
    return columns;
  }

  back(): void {
    this.router.navigate(['/predictions']);
  }
}
