import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { DatasetFamily, DatasetType } from '../../core/models/defectlab.model';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';
import { ToastService } from '../../shared/ui-toast/toast.service';
import { DatasetsFacade } from './datasets.facade';

@Component({
  selector: 'app-dataset-create',
  standalone: false,
  templateUrl: './dataset-create.component.html'
})
export class DatasetCreateComponent {
  projectName = '';
  projectVersion = '';
  family: DatasetFamily = 'PROMISE';
  origin: DatasetType = 'PREDEFINED';
  file: File | null = null;
  uploading = false;

  readonly familyOptions: RadioOption[] = [
    { value: 'PROMISE', label: 'PROMISE' },
    { value: 'AEEEM', label: 'AEEEM' }
  ];

  readonly originOptions: RadioOption[] = [
    { value: 'PREDEFINED', label: 'Predefined dataset' },
    { value: 'MANUAL', label: 'Manually extracted' }
  ];

  constructor(
    private readonly facade: DatasetsFacade,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

  get canSubmit(): boolean {
    return !!this.file && this.projectName.trim().length > 0
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
    if (!this.canSubmit || this.uploading) return;
    this.uploading = true;
    this.facade.upload({
      file: this.file!,
      projectName: this.projectName,
      projectVersion: this.projectVersion,
      family: this.family,
      type: this.origin
    }).subscribe({
      next: () => {
        this.uploading = false;
        this.toast.success('Dataset added successfully.');
        this.router.navigate(['/datasets']);
      },
      error: () => {
        this.uploading = false;
      }
    });
  }

  back(): void {
    this.router.navigate(['/datasets']);
  }
}
