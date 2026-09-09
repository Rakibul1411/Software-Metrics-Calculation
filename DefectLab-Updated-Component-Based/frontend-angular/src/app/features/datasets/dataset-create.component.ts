import { Component } from '@angular/core';
import { BaseFormComponent } from '../../core/base';
import { DatasetFamily, DatasetType } from '../../core/models/defectlab.model';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';
import { DatasetsFacade } from './datasets.facade';

@Component({
  selector: 'app-dataset-create',
  standalone: false,
  templateUrl: './dataset-create.component.html'
})
export class DatasetCreateComponent extends BaseFormComponent {
  projectName = '';
  projectVersion = '';
  family: DatasetFamily = 'PROMISE';
  origin: DatasetType = 'PREDEFINED';
  file: File | null = null;

  readonly familyOptions: RadioOption[] = [
    { value: 'PROMISE', label: 'PROMISE' },
    { value: 'AEEEM', label: 'AEEEM' }
  ];

  readonly originOptions: RadioOption[] = [
    { value: 'PREDEFINED', label: 'Predefined dataset' },
    { value: 'MANUAL', label: 'Manually extracted' }
  ];

  protected override readonly listRoute = ['/datasets'];

  constructor(private readonly facade: DatasetsFacade) {
    super();
  }

  get canSubmit(): boolean {
    return !!this.file
      && this.projectName.trim().length > 0
      && this.projectVersion.trim().length > 0;
  }

  onFamilyChange(value: string): void {
    this.family = value as DatasetFamily;
  }

  onOriginChange(value: string): void {
    this.origin = value as DatasetType;
  }

  selectFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload(): void {
    if (!this.canSubmit) {
      return;
    }
    this.submitWith(
      this.facade.upload({
        file: this.file!,
        projectName: this.projectName,
        projectVersion: this.projectVersion,
        family: this.family,
        type: this.origin
      }),
      { success: 'Dataset added successfully.', redirect: this.listRoute });
  }
}
