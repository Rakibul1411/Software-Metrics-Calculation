import { Component, ElementRef, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { SelectOption } from './ui-select.model';

/**
 * A dropdown with a fixed, scrollable panel that only ever opens below the
 * trigger — unlike a native <select>, whose popup size and up/down
 * direction the browser decides on its own and we can't style.
 */
@Component({
  selector: 'ui-select',
  standalone: false,
  templateUrl: './ui-select.component.html'
})
export class UiSelectComponent {
  @Input() label = '';
  @Input({ required: true }) options: SelectOption[] = [];
  @Input() value: string | number | null = null;
  @Input() placeholder = 'Select…';
  @Input() required = false;
  @Input() disabled = false;
  @Output() valueChange = new EventEmitter<string | number | null>();

  open = false;

  constructor(private readonly host: ElementRef<HTMLElement>) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open && !this.host.nativeElement.contains(event.target as Node)) {
      this.open = false;
    }
  }

  get selectedLabel(): string {
    const match = this.options.find(option => option.value === this.value);
    return match ? match.label : this.placeholder;
  }

  toggle(): void {
    if (this.disabled) return;
    this.open = !this.open;
  }

  select(option: SelectOption): void {
    this.value = option.value;
    this.valueChange.emit(option.value);
    this.open = false;
  }
}
