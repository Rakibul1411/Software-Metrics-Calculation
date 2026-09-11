import { Injectable } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import {
  DashboardData,
  DatasetPreview,
  DatasetSummary,
  PredictionRunSummary
} from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { CodeSmellService } from '../../core/services/code-smell.service';
import { ClassAnalysisResult, CodeSmell } from '../../core/models/code-smell.model';
import { ChartData } from '../../shared/ui-bar-chart/ui-bar-chart.model';

/** Datasets shown in the volume chart before the tail is left to the catalog. */
const VOLUME_LIMIT = 8;
/** Scored runs compared side by side; past three, colour stops being readable. */
const QUALITY_LIMIT = 3;

export interface TopHotspot {
  classIdentifier: string;
  simpleName: string;
  packageName: string;
  defectProbability: number;
  riskRank: number;
  runId: number;
  targetDatasetName: string;
  reportKey: string;
  loc: number;
  wmc: number;
  cbo: number;
  lcom: number;
  blastRadius: number;
  maintainabilityIndex: number;
  maintainabilityRating: string;
  smells: CodeSmell[];
  primarySmell?: string;
}

export interface ArchitecturalHealthStats {
  totalAnalyzedClasses: number;
  totalGodClasses: number;
  totalSpaghettiCoupling: number;
  totalIncoherentModules: number;
  overallMaintainabilityIndex: number;
  maintainabilityRating: 'A' | 'B' | 'C';
  cleanClassesCount: number;
  warningClassesCount: number;
  criticalHotspotsCount: number;
}

export interface DefectStats {
  totalRuns: number;
  totalPredictedBuggy: number;
  totalPredictedClean: number;
  totalPredictedClasses: number;
  defectRate: number;
  avgF1: number | null;
  avgRocAuc: number | null;
  avgAccuracy: number | null;
  avgPrecision: number | null;
  avgRecall: number | null;
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
  architecturalStats: ArchitecturalHealthStats;
  topHotspots: TopHotspot[];
}

/**
 * Owns the overview screen's read model. The dashboard endpoint only carries
 * the counters and the two recent lists, so the charts are aggregated here
 * from the dataset and prediction-run collections the API already exposes —
 * the component stays a template binding surface.
 */
@Injectable({ providedIn: 'root' })
export class DashboardFacade {
  constructor(
    private readonly api: DefectLabApiService,
    private readonly codeSmellService: CodeSmellService
  ) {}

  load(): Observable<DashboardView> {
    return forkJoin({
      data: this.api.dashboard(),
      datasets: this.api.listDatasets(),
      runs: this.api.listPredictionRuns()
    }).pipe(
      switchMap(result => {
        const latestRun = result.runs[0];
        if (!latestRun) {
          return of(this.assemble(result.data, result.datasets, result.runs, [], this.computeArchStats([])));
        }

        const reportKey = latestRun.comparisonGroupId || `run-${latestRun.id}`;

        const predictions$ = this.api.predictions(latestRun.id, true, 6).pipe(
          catchError(() => of([]))
        );
        const preview$ = latestRun.targetDataset?.id
          ? this.api.previewDataset(latestRun.targetDataset.id, 0, 5000).pipe(
              catchError(() => of(null))
            )
          : of(null);

        return forkJoin({
          predictions: predictions$,
          preview: preview$
        }).pipe(
          map(({ predictions, preview }) => {
            const dictRows = preview ? this.toDictRows(preview) : [];
            const analyzedAll = dictRows.length
              ? this.codeSmellService.parseClassAnalysisList(dictRows)
              : [];

            const matchedItems = this.codeSmellService.parsePredictionWithDatasetRows(
              predictions, dictRows);

            const hotspots: TopHotspot[] = predictions.map((p, idx) => {
              const matched = matchedItems[idx];
              const simpleName = matched?.className.split('.').pop() || p.classIdentifier.split('.').pop() || p.classIdentifier;
              const lastDot = matched?.className.lastIndexOf('.') ?? -1;
              const packageName = lastDot > 0 ? matched.className.substring(0, lastDot) : '(root package)';
              const primarySmell = matched?.smells[0]?.name;

              return {
                classIdentifier: p.classIdentifier,
                simpleName,
                packageName,
                defectProbability: p.defectProbability,
                riskRank: p.riskRank,
                runId: latestRun.id,
                targetDatasetName: latestRun.targetDataset.displayName,
                reportKey,
                loc: matched?.loc ?? Math.round(p.defectProbability * 600),
                wmc: matched?.wmc ?? Math.round(p.defectProbability * 25),
                cbo: matched?.cbo ?? Math.round(p.defectProbability * 15),
                lcom: matched?.lcom ?? 0,
                blastRadius: matched?.blastRadius ?? (matched?.ca || 0),
                maintainabilityIndex: matched?.maintainabilityIndex ?? 70,
                maintainabilityRating: matched?.maintainabilityRating ?? 'B',
                smells: matched?.smells ?? [],
                primarySmell
              };
            });

            const archStats = this.computeArchStats(analyzedAll);

            return this.assemble(
              result.data,
              result.datasets,
              result.runs,
              hotspots,
              archStats
            );
          }),
          catchError(() =>
            of(this.assemble(result.data, result.datasets, result.runs, [], this.computeArchStats([])))
          )
        );
      })
    );
  }

  private toDictRows(preview: DatasetPreview): Array<Record<string, string | number>> {
    if (!preview?.headers || !preview?.rows) return [];
    return preview.rows.map(rowVals => {
      const obj: Record<string, string | number> = {};
      preview.headers.forEach((h, i) => {
        const val = rowVals[i];
        const num = Number(val);
        const parsed = !isNaN(num) && val !== '' && val !== null && val !== undefined ? num : val;
        obj[h] = parsed;
        obj[h.toLowerCase()] = parsed;
      });
      return obj;
    });
  }

  private computeArchStats(items: ClassAnalysisResult[]): ArchitecturalHealthStats {
    if (!items || items.length === 0) {
      return {
        totalAnalyzedClasses: 0,
        totalGodClasses: 0,
        totalSpaghettiCoupling: 0,
        totalIncoherentModules: 0,
        overallMaintainabilityIndex: 75,
        maintainabilityRating: 'B',
        cleanClassesCount: 0,
        warningClassesCount: 0,
        criticalHotspotsCount: 0
      };
    }

    const totalGodClasses = items.filter(it => it.smells.some(s => s.type === 'GOD_CLASS')).length;
    const totalSpaghettiCoupling = items.filter(it => it.smells.some(s => s.type === 'SPAGHETTI_COUPLING')).length;
    const totalIncoherentModules = items.filter(it => it.smells.some(s => s.type === 'INCOHERENT_MODULE')).length;
    const totalMi = items.reduce((sum, it) => sum + (it.maintainabilityIndex || 70), 0);
    const overallMi = Math.round(totalMi / items.length);

    const maintainabilityRating: 'A' | 'B' | 'C' =
      overallMi >= 75 ? 'A' : (overallMi >= 55 ? 'B' : 'C');

    const criticalHotspotsCount = items.filter(
      it => it.riskScore >= 0.6 || it.smells.some(s => s.severity === 'CRITICAL')
    ).length;
    const warningClassesCount = items.filter(
      it => (it.riskScore >= 0.35 && it.riskScore < 0.6) || (it.smells.length > 0 && !it.smells.some(s => s.severity === 'CRITICAL'))
    ).length;
    const cleanClassesCount = Math.max(0, items.length - criticalHotspotsCount - warningClassesCount);

    return {
      totalAnalyzedClasses: items.length,
      totalGodClasses,
      totalSpaghettiCoupling,
      totalIncoherentModules,
      overallMaintainabilityIndex: overallMi,
      maintainabilityRating,
      cleanClassesCount,
      warningClassesCount,
      criticalHotspotsCount
    };
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
    runs: PredictionRunSummary[],
    topHotspots: TopHotspot[] = [],
    architecturalStats?: ArchitecturalHealthStats
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
      defectStats: this.calculateDefectStats(runs),
      architecturalStats: architecturalStats || this.computeArchStats([]),
      topHotspots
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

    const precRuns = runs.filter(
      r => r.evaluation && typeof r.evaluation.precision?.value === 'number'
    );
    const avgPrecision =
      precRuns.length > 0
        ? precRuns.reduce(
            (sum, r) => sum + (r.evaluation!.precision.value ?? 0),
            0
          ) / precRuns.length
        : null;

    const recRuns = runs.filter(
      r => r.evaluation && typeof r.evaluation.recall?.value === 'number'
    );
    const avgRecall =
      recRuns.length > 0
        ? recRuns.reduce(
            (sum, r) => sum + (r.evaluation!.recall.value ?? 0),
            0
          ) / recRuns.length
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
      avgPrecision,
      avgRecall,
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
