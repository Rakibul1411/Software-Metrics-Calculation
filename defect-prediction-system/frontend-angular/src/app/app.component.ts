import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: false,
  template: `
    <header class="app-header">
      <a class="brand" href="#main" aria-label="CodeLens home">
        <span class="brand-mark" aria-hidden="true">
          <span></span><span></span><span></span>
        </span>
        <span>CodeLens</span>
        <small>Defect Prediction</small>
      </a>
      <div class="header-meta"><span></span> Metrics engine ready</div>
    </header>
    <main id="main"><app-metrics-extraction></app-metrics-extraction></main>
    <footer>Static analysis for smarter testing decisions.</footer>
  `,
  styleUrls: ['./app.component.css']
})
export class AppComponent {}
