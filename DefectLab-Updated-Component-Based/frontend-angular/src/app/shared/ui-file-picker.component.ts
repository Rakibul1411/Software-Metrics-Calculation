import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Shared file input presentation; the feature still owns validation and handling. */
@Component({
  selector: 'ui-file-picker',
  standalone: false,
  template: `
    <label class="dl-file-panel">
      <input class="dl-sr-only" type="file" [attr.accept]="accept"
             (change)="fileChange.emit($event)">
      <span class="dl-file-copy">
        <strong>{{ label }}</strong>
        <small *ngIf="helper">{{ helper }}</small>
      </span>
      <span class="dl-btn dl-btn-ghost dl-btn-sm">{{ buttonLabel }}</span>
    </label>
  `
})
export class UiFilePickerComponent {
  @Input() accept = '';
  @Input() label = 'Choose a file';
  @Input() helper = '';
  @Input() buttonLabel = 'Browse file';
  @Output() fileChange = new EventEmitter<Event>();
}
