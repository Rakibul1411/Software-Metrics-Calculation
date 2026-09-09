import { Component, OnInit } from '@angular/core';
import { BaseComponent } from '../../core/base';
import { DashboardFacade, DashboardView } from './dashboard.facade';

@Component({
  selector: 'app-overview',
  standalone: false,
  templateUrl: './overview.component.html'
})
export class OverviewComponent extends BaseComponent implements OnInit {
  view: DashboardView | null = null;
  loading = true;

  constructor(private readonly facade: DashboardFacade) {
    super();
  }

  ngOnInit(): void {
    this.watch(this.facade.load()).subscribe({
      next: view => {
        this.view = view;
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }

  originLabel(value: string): string {
    return this.facade.originLabel(value);
  }

  modelSetting(config: { k: number }): string {
    return this.facade.modelSetting(config);
  }

  percent(value: number, total: number): number {
    return this.facade.percent(value, total);
  }
}
