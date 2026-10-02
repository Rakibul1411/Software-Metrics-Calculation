import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Reusable table toolbar search bar with icon and quick-clear action.
 */
@Component({
  selector: 'ui-search-bar',
  standalone: false,
  templateUrl: './ui-search-bar.component.html'
})
export class UiSearchBarComponent {
  @Input() query = '';
  @Input() placeholder = 'Search…';
  @Output() readonly queryChange = new EventEmitter<string>();

  onInput(value: string): void {
    this.query = value;
    this.queryChange.emit(value);
  }

  clear(): void {
    this.query = '';
    this.queryChange.emit('');
  }
}
