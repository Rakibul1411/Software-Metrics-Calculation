import { Component, Input } from '@angular/core';
import { DetailField } from './ui-detail-fields.model';

/**
 * A record's attributes as label : value rows, flowing down one column then
 * the next — the summary block at the top of a detail page (dataset,
 * prediction run, report), instead of a grid of stat tiles for data that
 * isn't a KPI.
 */
@Component({
  selector: 'ui-detail-fields',
  standalone: false,
  template: `
    <div class="dl-detail-fields">
      <div class="dl-detail-field" *ngFor="let field of fields">
        <span class="dl-detail-field-label">{{ field.label }}</span>
        <span class="dl-detail-field-colon">:</span>
        <span class="dl-detail-field-value">{{ field.value ?? '—' }}</span>
      </div>
    </div>
  `
})
export class UiDetailFieldsComponent {
  @Input({ required: true }) fields: DetailField[] = [];
}
