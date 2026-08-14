import { Directive, Input, TemplateRef } from '@angular/core';

/** Marks a <ng-template uiTableCell="columnKey" let-row> as that column's cell renderer. */
@Directive({
  selector: 'ng-template[uiTableCell]',
  standalone: false
})
export class UiTableCellDirective {
  @Input('uiTableCell') key = '';

  constructor(public readonly templateRef: TemplateRef<{ $implicit: unknown }>) {}
}
