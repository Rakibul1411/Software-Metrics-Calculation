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
  templateUrl: './ui-detail-fields.component.html'
})
export class UiDetailFieldsComponent {
  @Input({ required: true }) fields: DetailField[] = [];

  trackByLabel(_index: number, field: DetailField): string {
    return field.label;
  }
}
