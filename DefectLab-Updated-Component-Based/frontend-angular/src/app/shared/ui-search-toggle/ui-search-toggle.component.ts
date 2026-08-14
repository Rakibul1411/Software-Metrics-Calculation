import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';

/**
 * The app's one "search icon that expands into an inline input" control,
 * used on every list page's header. Owns only the open/closed toggle and
 * focus handling; filtering stays with the caller since it differs per page.
 */
@Component({
  selector: 'ui-search-toggle',
  standalone: false,
  templateUrl: './ui-search-toggle.component.html'
})
export class UiSearchToggleComponent {
  @Input() label = 'Search';
  @Input() placeholder = 'Search…';
  @Input() query = '';
  @Output() queryChange = new EventEmitter<string>();

  open = false;

  @ViewChild('searchInput') private readonly inputRef?: ElementRef<HTMLInputElement>;

  openSearch(): void {
    this.open = true;
    setTimeout(() => this.inputRef?.nativeElement.focus());
  }

  closeSearch(): void {
    this.open = false;
    this.query = '';
    this.queryChange.emit('');
  }
}
