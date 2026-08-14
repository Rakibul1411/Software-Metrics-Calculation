import { Component, EventEmitter, Input, Output } from '@angular/core';

export type UiInputType = 'text' | 'email' | 'password' | 'number';

/** The app's one labelled text field — every page binds [(value)] instead of hand-rolling a dl-field. */
@Component({
  selector: 'ui-input',
  standalone: false,
  templateUrl: './ui-input.component.html'
})
export class UiInputComponent {
  @Input() label = '';
  @Input() type: UiInputType = 'text';
  @Input() placeholder = '';
  @Input() helper = '';
  @Input() required = false;
  @Input() disabled = false;
  @Input() autocomplete?: string;
  @Input() minlength?: number;
  @Input() maxlength?: number;
  @Input() min?: number;
  @Input() max?: number;
  @Input() step?: number;
  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();

  onInput(raw: string): void {
    this.value = raw;
    this.valueChange.emit(raw);
  }
}
