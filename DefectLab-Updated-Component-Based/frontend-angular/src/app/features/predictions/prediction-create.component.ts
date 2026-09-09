import { Component, OnInit } from '@angular/core';
import { BaseFormComponent } from '../../core/base';
import { FAMILY_RADIO_OPTIONS } from '../../core/constants/dataset-filter.options';
import { DatasetFamily } from '../../core/models/defectlab.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { PredictionTargetsFacade, TargetSelection } from './prediction-targets.facade';
import { PredictionsFacade } from './predictions.facade';

@Component({
  selector: 'app-prediction-create',
  standalone: false,
  templateUrl: './prediction-create.component.html'
})
export class PredictionCreateComponent extends BaseFormComponent implements OnInit {
  datasetFamily: DatasetFamily = 'PROMISE';
  sourceId: number | null = null;
  manualId: number | null = null;
  predefinedId: number | null = null;
  k = 3;
  coral = true;
  threshold = 0.5;

  readonly familyOptions = FAMILY_RADIO_OPTIONS;
  readonly kOptions: SelectOption[] =
    [1, 2, 3, 4, 5].map(value => ({ value, label: String(value) }));

  protected override readonly listRoute = ['/predictions'];

  constructor(
    private readonly targets: PredictionTargetsFacade,
    private readonly predictions: PredictionsFacade
  ) {
    super();
  }

  ngOnInit(): void {
    this.watch(this.targets.load()).subscribe({ error: () => {} });
  }

  get modelName(): string {
    return this.predictions.modelName;
  }

  get sourceSelectOptions(): SelectOption[] {
    return this.targets.sourceOptions(this.datasetFamily, this.selection);
  }

  get manualSelectOptions(): SelectOption[] {
    return this.targets.manualOptions(this.datasetFamily, this.selection);
  }

  get predefinedSelectOptions(): SelectOption[] {
    return this.targets.predefinedOptions(this.datasetFamily);
  }

  onFamilyChange(value: string): void {
    this.datasetFamily = value as DatasetFamily;
    this.sourceId = null;
    this.manualId = null;
    this.predefinedId = null;
  }

  onSourceChange(value: string | number | null): void {
    this.sourceId = value as number | null;
    this.reconcile();
  }

  onManualChange(value: string | number | null): void {
    this.manualId = value as number | null;
    this.reconcile();
  }

  onPredefinedChange(value: string | number | null): void {
    this.predefinedId = value as number | null;
  }

  onKChange(value: string | number | null): void {
    this.k = value as number;
  }

  onThresholdChange(value: string): void {
    this.threshold = Number(value);
  }

  canRun(): boolean {
    return this.targets.canRun(this.selection);
  }

  run(): void {
    if (!this.canRun()) {
      return;
    }
    this.submitWith(
      this.predictions.run({
        ...this.selection,
        k: this.k,
        coral: this.coral,
        threshold: this.threshold
      }),
      { success: 'Prediction run completed successfully.', redirect: this.listRoute });
  }

  private get selection(): TargetSelection {
    return {
      sourceId: this.sourceId,
      manualId: this.manualId,
      predefinedId: this.predefinedId
    };
  }

  /** Drops choices the latest edit made ineligible. */
  private reconcile(): void {
    const next = this.targets.reconcile(this.datasetFamily, this.selection);
    this.manualId = next.manualId;
    this.predefinedId = next.predefinedId;
  }
}
