import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SelectOption } from '../../shared/ui-select/ui-select.model';

/** The three dataset choices a prediction run is built from. */
export interface TargetSelection {
  sourceId: number | null;
  manualId: number | null;
  predefinedId: number | null;
}

/**
 * The eligibility rules for a prediction run: which datasets may act as the
 * labeled source, which as a MANUAL target, which as a PREDEFINED target,
 * and how a selection has to be repaired when an earlier choice changes.
 *
 * These are real application rules rather than presentation, so they live
 * here instead of in the form component.
 */
@Injectable({ providedIn: 'root' })
export class PredictionTargetsFacade {
  private datasets: DatasetSummary[] = [];

  constructor(private readonly api: DefectLabApiService) {}

  load(): Observable<DatasetSummary[]> {
    return this.api.listDatasets().pipe(tap(rows => (this.datasets = rows)));
  }

  find(id: number | null): DatasetSummary | undefined {
    return this.datasets.find(item => item.id === id);
  }

  /** Labeled datasets of the chosen family, excluding the manual target. */
  sourceOptions(family: DatasetFamily, selection: TargetSelection): SelectOption[] {
    return this.datasets
      .filter(item =>
        item.hasActualLabel &&
        item.datasetFamily === family &&
        item.id !== selection.manualId)
      .map(item => this.option(item, 'LABELED'));
  }

  manualOptions(family: DatasetFamily, selection: TargetSelection): SelectOption[] {
    const rows = this.datasets.filter(item =>
      item.datasetType === 'MANUAL' &&
      item.datasetFamily === family &&
      item.id !== selection.sourceId);
    return [
      { value: null, label: 'No manual target' },
      ...rows.map(item => this.option(item, 'MANUAL'))
    ];
  }

  predefinedOptions(family: DatasetFamily): SelectOption[] {
    const rows = this.datasets.filter(item =>
      item.datasetType === 'PREDEFINED' &&
      item.hasActualLabel &&
      item.datasetFamily === family);
    return [
      { value: null, label: 'No predefined target' },
      ...rows.map(item => this.option(item, 'PREDEFINED'))
    ];
  }

  /**
   * Drops any choice the latest edit invalidated -- a target that now
   * matches the source, or one from a family the user switched away from.
   */
  reconcile(family: DatasetFamily, selection: TargetSelection): TargetSelection {
    const source = this.find(selection.sourceId);
    let manualId = selection.manualId;
    let predefinedId = selection.predefinedId;

    if (manualId !== null && manualId === selection.sourceId) {
      manualId = null;
    }
    if (source) {
      const manual = this.find(manualId);
      if (manual && manual.datasetFamily !== source.datasetFamily) {
        manualId = null;
      }
      const predefined = this.find(predefinedId);
      if (predefined && predefined.datasetFamily !== source.datasetFamily) {
        predefinedId = null;
      }
    }
    const available = this.predefinedOptions(family);
    if (predefinedId !== null && !available.some(item => item.value === predefinedId)) {
      predefinedId = null;
    }
    return { sourceId: selection.sourceId, manualId, predefinedId };
  }

  canRun(selection: TargetSelection): boolean {
    return !!selection.sourceId && (!!selection.manualId || !!selection.predefinedId);
  }

  private option(item: DatasetSummary, role: string): SelectOption {
    const scope = item.systemDataset ? 'BUNDLED' : 'YOUR DATA';
    return {
      value: item.id,
      label: `${item.displayName} · ${item.datasetFamily} · ${role} · ${scope}`
    };
  }
}
