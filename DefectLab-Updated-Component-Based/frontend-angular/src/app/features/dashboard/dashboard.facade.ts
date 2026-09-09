import { Injectable } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  DashboardData,
  DatasetSummary,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { ChartData } from '../../shared/ui-bar-chart/ui-bar-chart.model';

/** Datasets shown in the volume chart before the tail is left to the catalog. */
const VOLUME_LIMIT = 8;
/** Scored runs compared side by side; past three, colour stops being readable. */
const QUALITY_LIMIT = 3;

export interface DefectStats {
  totalRuns: number;
  totalPredictedBuggy: number;
  totalPredictedClean: number;
  totalPredictedClasses: number;
  defectRate: number;
  avgF1: number | null;
  avgRocAuc: number | null;
  avgAccuracy: number | null;
  scoredRunsCount: number;
  highestRiskDataset: string | null;
  highestRiskCount: number;
}

/** Everything the overview screen renders, assembled once. */
export interface DashboardView {
  data: DashboardData;
  datasets: DatasetSummary[];
  runs: PredictionRunSummary[];
  volume: ChartData;
  volumeTotal: number;
  composition: ChartData;
  quality: ChartData;
  balance: ChartData;
  defectStats: DefectStats;
}

/**
 * Owns the overview screen's read model. The dashboard endpoint only carries
 * the counters and the two recent lists, so the charts are aggregated here
 * from the dataset and prediction-run collections the API already exposes —
 * the component stays a template binding surface.
 */
@Injectable({ providedIn: 'root' })
export class DashboardFacade {
  constructor(private readonly api: DefectLabApiService) {}

  load(): Observable<DashboardView> {
    return forkJoin({
      data: this.api.dashboard(),
      datasets: this.api.listDatasets(),
      runs: this.api.listPredictionRuns()
    }).pipe(map(result => this.assemble(result.data, result.datasets, result.runs)));
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

  private assemble(
    data: DashboardData,
    datasets: DatasetSummary[],
    runs: PredictionRunSummary[]
  ): DashboardView {
    return {
      data,
      datasets,
      runs,
      volume: this.volumeChart(datasets),
      volumeTotal: datasets.reduce((sum, item) => sum + item.totalFiles, 0),
      composition: this.compositionChart(datasets),
      quality: this.qualityChart(runs),
      balance: this.balanceChart(runs),
      defectStats: this.calculateDefectStats(runs)
    };
  }

  private calculateDefectStats(runs: PredictionRunSummary[]): DefectStats {
    const totalRuns = runs.length;
    const totalPredictedBuggy = runs.reduce(
      (sum, r) => sum + (r.summary?.predictedBuggy ?? 0),
      0
    );
    const totalPredictedClean = runs.reduce(
      (sum, r) => sum + (r.summary?.predictedClean ?? 0),
      0
    );
    const totalPredictedClasses = totalPredictedBuggy + totalPredictedClean;
    const defectRate =
      totalPredictedClasses > 0
        ? (totalPredictedBuggy / totalPredictedClasses) * 100
        : 0;

    const scoredRuns = runs.filter(
      r => r.evaluation && typeof r.evaluation.f1?.value === 'number'
    );
    const scoredRunsCount = scoredRuns.length;
    const avgF1 =
      scoredRunsCount > 0
        ? scoredRuns.reduce((sum, r) => sum + (r.evaluation!.f1.value ?? 0), 0) /
          scoredRunsCount
        : null;

    const rocAucRuns = runs.filter(
      r => r.evaluation && typeof r.evaluation.rocAuc?.value === 'number'
    );
    const avgRocAuc =
      rocAucRuns.length > 0
        ? rocAucRuns.reduce(
            (sum, r) => sum + (r.evaluation!.rocAuc.value ?? 0),
            0
          ) / rocAucRuns.length
        : null;

    const accRuns = runs.filter(
      r => r.evaluation && typeof r.evaluation.accuracy?.value === 'number'
    );
    const avgAccuracy =
      accRuns.length > 0
        ? accRuns.reduce(
            (sum, r) => sum + (r.evaluation!.accuracy.value ?? 0),
            0
          ) / accRuns.length
        : null;

    let highestRiskDataset: string | null = null;
    let highestRiskCount = 0;
    for (const run of runs) {
      const buggy = run.summary?.predictedBuggy ?? 0;
      if (buggy > highestRiskCount) {
        highestRiskCount = buggy;
        highestRiskDataset = run.targetDataset?.displayName ?? `Run #${run.id}`;
      }
    }

    return {
      totalRuns,
      totalPredictedBuggy,
      totalPredictedClean,
      totalPredictedClasses,
      defectRate,
      avgF1,
      avgRocAuc,
      avgAccuracy,
      scoredRunsCount,
      highestRiskDataset,
      highestRiskCount
    };
  }

  /** Magnitude across projects: one series, one colour, sorted so the bars rank. */
  private volumeChart(datasets: DatasetSummary[]): ChartData {
    const top = [...datasets]
      .sort((a, b) => b.totalFiles - a.totalFiles)
      .slice(0, VOLUME_LIMIT);
    return {
      categories: top.map(item => `${item.displayName} · ${item.datasetFamily}`),
      series: [{ label: 'Classes', values: top.map(item => item.totalFiles) }]
    };
  }

  /** Part-to-whole: how each family's datasets split between the two origins. */
  private compositionChart(datasets: DatasetSummary[]): ChartData {
    const families = ['PROMISE', 'AEEEM'];
    const count = (family: string, type: string) =>
      datasets.filter(item => item.datasetFamily === family && item.datasetType === type).length;
    return {
      categories: families,
      series: [
        { label: 'Manual extracted', values: families.map(family => count(family, 'MANUAL')) },
        { label: 'Predefined', values: families.map(family => count(family, 'PREDEFINED')) }
      ]
    };
  }

  /** Only labelled targets are scored, and every metric shares the 0–1 axis. */
  private qualityChart(runs: PredictionRunSummary[]): ChartData {
    const scored = runs.filter(run => run.evaluation).slice(0, QUALITY_LIMIT);
    return {
      categories: ['Accuracy', 'Precision', 'Recall', 'F1', 'ROC-AUC'],
      series: scored.map(run => ({
        label: `#${run.id} · ${run.targetDataset.displayName}`,
        values: [
          this.metric(run, 'accuracy'),
          this.metric(run, 'precision'),
          this.metric(run, 'recall'),
          this.metric(run, 'f1'),
          this.metric(run, 'rocAuc')
        ]
      }))
    };
  }

  /** Class imbalance is the thing to spot, so each run is normalised to 100%. */
  private balanceChart(runs: PredictionRunSummary[]): ChartData {
    const recent = runs.slice(0, 6);
    return {
      categories: recent.map(run => `#${run.id} · ${run.targetDataset.displayName}`),
      series: [
        {
          label: 'Predicted buggy',
          tone: 'alert',
          values: recent.map(run => run.summary.predictedBuggy)
        },
        { label: 'Predicted clean', values: recent.map(run => run.summary.predictedClean) }
      ]
    };
  }

  private metric(
    run: PredictionRunSummary,
    key: 'accuracy' | 'precision' | 'recall' | 'f1' | 'rocAuc'
  ): number {
    const value = run.evaluation?.[key]?.value;
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
  }
}
