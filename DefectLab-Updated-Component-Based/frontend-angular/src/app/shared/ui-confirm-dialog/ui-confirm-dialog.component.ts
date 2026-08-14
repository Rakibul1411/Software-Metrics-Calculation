import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';

/** Shared yes/no confirmation modal — replaces window.confirm() for destructive actions. */
@Component({
  selector: 'ui-confirm-dialog',
  standalone: false,
  templateUrl: './ui-confirm-dialog.component.html'
})
export class UiConfirmDialogComponent {
  @Input() open = false;
  @Input() title = 'Are you sure?';
  @Input() message = '';
  @Input() confirmLabel = 'Delete';
  @Input() cancelLabel = 'Cancel';
  @Input() tone: 'danger' | 'primary' = 'danger';
  @Input() busy = false;
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open && !this.busy) {
      this.cancel();
    }
  }

  confirm(): void {
    this.confirmed.emit();
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
