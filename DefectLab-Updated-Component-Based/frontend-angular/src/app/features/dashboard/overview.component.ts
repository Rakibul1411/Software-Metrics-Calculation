import { Component, OnInit } from '@angular/core';
import { DashboardData } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';

@Component({
  selector: 'app-overview',
  standalone: false,
  templateUrl: './overview.component.html'
})
export class OverviewComponent implements OnInit {
  data: DashboardData | null = null;
  loading = true;
  error = '';

  constructor(private readonly api: DefectLabApiService) {}

  ngOnInit(): void {
    this.api.dashboard().subscribe({
      next: data => {
        this.data = data;
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.error ?? 'Could not load the dashboard.';
        this.loading = false;
      }
    });
  }

  originLabel(value: string): string {
    return value === 'MANUAL' ? 'Manual extraction' : 'Predefined';
  }

  modelSetting(config: { k: number }): string {
    return `K=${config.k}`;
  }

  percent(value: number, total: number): number {
    return total > 0 ? Math.max(0, Math.min(100, (value / total) * 100)) : 0;
  }

  totalRecentRows(items: DashboardData['recentDatasets']): number {
    return items.reduce((sum, item) => sum + item.totalFiles, 0);
  }

  chartPoints(items: DashboardData['recentDatasets']): string {
    return this.chartDots(items).map(point => `${point.x},${point.y}`).join(' ');
  }

  chartAreaPoints(items: DashboardData['recentDatasets']): string {
    const points = this.chartPoints(items);
    return points ? `20,170 ${points} 580,170` : '';
  }

  chartDots(items: DashboardData['recentDatasets']): Array<{ x: number; y: number }> {
    if (!items.length) return [];
    const values = items.map(item => item.totalFiles);
    const max = Math.max(...values, 1);
    if (items.length === 1) {
      const y = 160 - (values[0] / max) * 120;
      return [{ x: 20, y }, { x: 580, y }];
    }
    return values.map((value, index) => ({
      x: 20 + (index / (values.length - 1)) * 560,
      y: 160 - (value / max) * 120
    }));
  }
}
