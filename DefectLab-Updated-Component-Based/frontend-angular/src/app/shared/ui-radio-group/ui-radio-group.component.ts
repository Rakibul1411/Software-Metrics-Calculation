import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RadioOption } from './ui-radio-group.model';

let nextGroupId = 0;

/** A labelled row of radio choices — the app's one way to pick one of a few named values. */
@Component({
  selector: 'ui-radio-group',
  standalone: false,
  templateUrl: './ui-radio-group.component.html'
})
export class UiRadioGroupComponent {
  @Input() label = '';
  @Input({ required: true }) options: RadioOption[] = [];
  @Input() value: string | null = null;
  @Input() disabled = false;
  @Output() valueChange = new EventEmitter<string>();

  readonly name = `dl-radio-${nextGroupId++}`;

  select(optionValue: string): void {
    if (this.disabled || this.value === optionValue) return;
    this.value = optionValue;
    this.valueChange.emit(optionValue);
  }
}
