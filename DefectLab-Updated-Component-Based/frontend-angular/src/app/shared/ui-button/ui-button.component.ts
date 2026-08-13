import { Component, EventEmitter, Input, Output } from '@angular/core';
import { UiIconName } from '../ui-icon/ui-icon.component';

/**
 * The only button in the application.
 *
 * Rendered as a <button> or, when `href`/`link` is set, as an anchor — so a
 * navigation and an action look identical without any page restyling either.
 */
@Component({
  selector: 'ui-button',
  standalone: false,
  templateUrl: './ui-button.component.html'
})
export class UiButtonComponent {
  @Input() variant: 'primary' | 'ghost' | 'danger' = 'primary';
  @Input() size: 'md' | 'sm' = 'md';
  @Input() icon?: UiIconName;
  @Input() type: 'button' | 'submit' = 'button';
  @Input() disabled = false;
  @Input() loading = false;
  /** Router path; renders an <a routerLink>. */
  @Input() link?: string | unknown[];
  /** External/absolute URL; renders a plain <a href>. */
  @Input() href?: string;
  @Input() title?: string;
  @Input() ariaLabel?: string;
  @Output() pressed = new EventEmitter<void>();

  get classes(): string {
    return [
      'dl-btn',
      this.variant === 'ghost' ? 'dl-btn-ghost' : '',
      this.variant === 'danger' ? 'dl-btn-danger' : '',
      this.size === 'sm' ? 'dl-btn-sm' : ''
    ].filter(Boolean).join(' ');
  }
}
