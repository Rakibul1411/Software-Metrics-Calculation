import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Standard page and detail view header across DefectLab.
 * Provides unified breadcrumb navigation, title hierarchy, badge slots, and action buttons.
 */
@Component({
  selector: 'ui-page-header',
  standalone: false,
  templateUrl: './ui-page-header.component.html',
  styleUrls: ['./ui-page-header.component.css']
})
export class UiPageHeaderComponent {
  @Input() title = '';
  @Input() fallbackTitle = '';
  @Input() parentLabel = '';
  @Input() parentLink: unknown[] | string | null = null;
  @Input() currentLabel = '';
  @Output() readonly parentClick = new EventEmitter<void>();
}
