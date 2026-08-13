import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';

/** Shared file input presentation; the feature still owns validation and handling. */
@Component({
  selector: 'ui-file-picker',
  standalone: false,
  templateUrl: './ui-file-picker.component.html'
})
export class UiFilePickerComponent {
  /** The field's name, shown above the drop zone like any other dl-field label. */
  @Input() fieldLabel = '';
  @Input() required = false;
  @Input() accept = '';
  @Input() label = 'Drop a file here to upload';
  @Input() helper = '';
  @Input() buttonLabel = 'browse for files';
  @Output() fileChange = new EventEmitter<Event>();

  @ViewChild('fileInput') private readonly fileInputRef!: ElementRef<HTMLInputElement>;

  dragActive = false;

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    const files = event.dataTransfer?.files;
    if (!files || files.length === 0) return;
    const input = this.fileInputRef.nativeElement;
    input.files = files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }
}
