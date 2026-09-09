import { Component, ElementRef, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { SelectOption } from './ui-select.model';

/** Matches the panel's max-height in the stylesheet, plus its 4px offset. */
const MENU_HEIGHT = 284;

/**
 * A dropdown with a fixed, scrollable panel — unlike a native <select>, whose
 * popup size the browser decides on its own and we can't style. The panel
 * opens below the trigger, or above it when the viewport has no room below:
 * the rows-per-page control sits on the last line of a full-height card, so a
 * downward panel opened straight off the bottom of the window.
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
  /** Hint shown under the control, same contract as ui-input's. */
  @Input() helper = '';
  @Input() required = false;
  @Input() disabled = false;
  /** 'auto' flips the panel up when the viewport lacks room below. */
  @Input() menuPlacement: 'auto' | 'down' | 'up' = 'auto';
  @Output() valueChange = new EventEmitter<string | number | null>();

  open = false;
  dropUp = false;

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
    if (this.open) {
      this.dropUp = this.shouldDropUp();
    }
  }

  /** Measured on open: the control can be anywhere by the time it is clicked. */
  private shouldDropUp(): boolean {
    if (this.menuPlacement !== 'auto') {
      return this.menuPlacement === 'up';
    }
    const trigger = this.host.nativeElement.querySelector('.dl-select-trigger');
    if (!trigger) {
      return false;
    }
    const box = trigger.getBoundingClientRect();
    const below = window.innerHeight - box.bottom;
    return below < MENU_HEIGHT && box.top > below;
  }

  select(option: SelectOption): void {
    this.value = option.value;
    this.valueChange.emit(option.value);
    this.open = false;
  }
}
