import { Component, ElementRef, EventEmitter, HostListener, Input, Output } from '@angular/core';

/** A download button that opens a small menu to pick the file format. */
@Component({
  selector: 'ui-download-menu',
  standalone: false,
  templateUrl: './ui-download-menu.component.html'
})
export class UiDownloadMenuComponent {
  @Input() label = 'Download';
  @Input() variant: 'primary' | 'ghost' = 'primary';
  @Input() size: 'md' | 'sm' = 'md';
  @Input({ required: true }) csvUrl!: string;
  @Input({ required: true }) arffUrl!: string;
  @Output() downloaded = new EventEmitter<'CSV' | 'ARFF'>();

  open = false;

  constructor(private readonly host: ElementRef<HTMLElement>) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open && !this.host.nativeElement.contains(event.target as Node)) {
      this.open = false;
    }
  }
}
