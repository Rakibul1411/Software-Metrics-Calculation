import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Observable } from 'rxjs';
import { ToastService } from '../ui-toast/toast.service';

/**
 * The app's one "Delete X" flow: trigger button + confirm dialog + API call +
 * toast. Every detail page wires this to its own delete request instead of
 * re-implementing pendingDelete/deleting state by hand.
 */
@Component({
  selector: 'ui-delete-action',
  standalone: false,
  templateUrl: './ui-delete-action.component.html'
})
export class UiDeleteActionComponent {
  @Input() label = 'Delete';
  @Input() dialogTitle = 'Delete item';
  @Input() confirmMessage = 'This cannot be undone.';
  @Input() successMessage = 'Deleted successfully.';
  @Input() disabled = false;
  /** Returns the delete request; called only after the user confirms. */
  @Input({ required: true }) deleteFn!: () => Observable<unknown>;
  /** Emitted after a successful delete, so the caller can navigate/reload. */
  @Output() deleted = new EventEmitter<void>();

  pendingDelete = false;
  deleting = false;

  constructor(private readonly toast: ToastService) {}

  open(): void {
    this.pendingDelete = true;
  }

  confirm(): void {
    if (this.deleting) return;
    this.deleting = true;
    this.deleteFn().subscribe({
      next: () => {
        this.deleting = false;
        this.pendingDelete = false;
        this.toast.success(this.successMessage);
        this.deleted.emit();
      },
      error: (failure: { error?: { error?: string } }) => {
        this.deleting = false;
        this.pendingDelete = false;
        const message = failure?.error?.error ?? 'Could not delete.';
        this.toast.error(message);
      }
    });
  }

  cancel(): void {
    if (this.deleting) return;
    this.pendingDelete = false;
  }
}
