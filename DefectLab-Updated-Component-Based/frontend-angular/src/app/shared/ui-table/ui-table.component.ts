import {
  Component,
  ContentChildren,
  Input,
  QueryList,
  TemplateRef
} from '@angular/core';
import { UiTableCellDirective } from './ui-table-cell.directive';
import { TableColumn } from './ui-table.model';

/**
 * The application's one data table: column-driven so every page configures
 * rather than reimplements it. The first and last columns freeze while the
 * table scrolls horizontally.
 *
 * Cell content defaults to row[column.key]. To render something else for a
 * column (badges, buttons, compound cells), project
 * <ng-template uiTableCell="key" let-row>...</ng-template>.
 */
@Component({
  selector: 'ui-table',
  standalone: false,
  templateUrl: './ui-table.component.html'
})
export class UiTableComponent {
  @Input({ required: true }) columns: TableColumn[] = [];
  @Input() rows: unknown[] = [];
  /** Row property used for *ngFor trackBy; falls back to index when omitted. */
  @Input() trackByKey?: string;
  /** Extra class for the scroll wrapper, e.g. a page-specific max-height rule. */
  @Input() scrollClass?: string;

  @ContentChildren(UiTableCellDirective) cellTemplates!: QueryList<UiTableCellDirective>;

  cellTemplate(key: string): TemplateRef<{ $implicit: unknown }> | null {
    return this.cellTemplates?.find(item => item.key === key)?.templateRef ?? null;
  }

  cellValue(row: unknown, column: TableColumn): unknown {
    return (row as Record<string, unknown>)[column.key];
  }

  trackByFn = (index: number, row: unknown): unknown => {
    const key = this.trackByKey;
    return key ? (row as Record<string, unknown>)[key] : index;
  };
}
