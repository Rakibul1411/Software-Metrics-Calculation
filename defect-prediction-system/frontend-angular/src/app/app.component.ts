import { Component, OnInit } from '@angular/core';

const THEME_STORAGE_KEY = 'dl-theme';

@Component({
  selector: 'app-root',
  standalone: false,
  template: `
    <header class="app-header">
      <a class="brand" href="#main" aria-label="DefectLab home">
        <span class="brand-mark" aria-hidden="true">DL</span>
        <span>
          <strong>DefectLab</strong>
          <small>Java metrics & prediction</small>
        </span>
      </a>
      <div class="header-actions">
        <span class="scope">PROMISE · AEEEM · shallow CORAL</span>
        <button type="button"
                class="theme-toggle"
                [attr.aria-pressed]="isDark"
                aria-label="Toggle dark mode"
                title="Toggle dark mode"
                (click)="toggleTheme()">
          <svg *ngIf="!isDark" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="4.2"/>
            <path d="M12 2.5v2.4M12 19.1v2.4M4.6 4.6l1.7 1.7M17.7 17.7l1.7 1.7M2.5 12h2.4M19.1 12h2.4M4.6 19.4l1.7-1.7M17.7 6.3l1.7-1.7"/>
          </svg>
          <svg *ngIf="isDark" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20.4 14.7A8.6 8.6 0 0 1 9.3 3.6a8.6 8.6 0 1 0 11.1 11.1Z"/>
          </svg>
        </button>
      </div>
    </header>
    <main id="main">
      <app-metrics-extraction></app-metrics-extraction>
    </main>
    <footer>
      Research-oriented source analysis. Report PROMISE output as schema-compatible
      unless it is validated against the original CKJM bytecode pipeline.
    </footer>
  `,
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  isDark = false;

  ngOnInit(): void {
    this.isDark = document.documentElement.getAttribute('data-theme') === 'dark';
  }

  toggleTheme(): void {
    this.isDark = !this.isDark;
    const theme = this.isDark ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }
}
