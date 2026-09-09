import { Component, OnInit } from '@angular/core';
import { BaseFormComponent } from '../../core/base';
import { FAMILY_FILTER_OPTIONS } from '../../core/constants/dataset-filter.options';
import { MetricComparisonPair } from '../../core/models/defectlab.model';
import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { ComparisonsFacade } from './comparisons.facade';

@Component({
  selector: 'app-comparison-create',
  standalone: false,
  templateUrl: './comparison-create.component.html'
})
export class ComparisonCreateComponent extends BaseFormComponent implements OnInit {
  pairs: MetricComparisonPair[] = [];
  familyFilter = '';
  selectedKey = '';

  readonly familyFilterOptions = FAMILY_FILTER_OPTIONS;

  protected override readonly listRoute = ['/metric-comparisons'];

  constructor(private readonly facade: ComparisonsFacade) {
    super();
  }

  ngOnInit(): void {
    this.load();
  }

  /** Only pairs without a stored result are worth offering here. */
  get uncomparedPairs(): MetricComparisonPair[] {
    return this.pairs.filter(pair => !pair.cached);
  }

  get filteredPairs(): MetricComparisonPair[] {
    return this.uncomparedPairs.filter(
      pair => !this.familyFilter || pair.datasetFamily === this.familyFilter);
  }

  get pairOptions(): SelectOption[] {
    return this.filteredPairs.map(pair => ({ value: pair.key, label: pair.displayName }));
  }

  get selectedPair(): MetricComparisonPair | undefined {
    return this.uncomparedPairs.find(pair => pair.key === this.selectedKey);
  }

  onFamilyFilterChange(value: string | number | null): void {
    this.familyFilter = (value as string) ?? '';
    if (!this.filteredPairs.some(pair => pair.key === this.selectedKey)) {
      this.selectedKey = '';
    }
    if (!this.filteredPairs.length) {
      this.toast.info('Every eligible dataset pair has already been compared.');
    }
  }

  onSelectedKeyChange(value: string | number | null): void {
    this.selectedKey = (value as string) ?? '';
  }

  load(): void {
    this.watch(this.facade.pairs()).subscribe({
      next: rows => (this.pairs = rows),
      error: () => {}
    });
  }

  run(): void {
    const pair = this.selectedPair;
    if (!pair) {
      return;
    }
    this.submitWith(this.facade.run(pair), {
      success: 'Comparison completed successfully.',
      redirect: this.listRoute
    });
  }
}
