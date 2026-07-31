import { Component } from '@angular/core';

/**
 * Root shell. The dashboard chrome lives in {@code ShellComponent}; this component
 * only hosts the router so the login screen can render full-bleed.
 */
@Component({
  selector: 'app-root',
  standalone: false,
  template: '<router-outlet></router-outlet>'
})
export class AppComponent {}
