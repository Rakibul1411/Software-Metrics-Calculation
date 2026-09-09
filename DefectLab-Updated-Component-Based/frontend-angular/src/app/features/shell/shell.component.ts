import { Component, OnInit, inject } from '@angular/core';
import { NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { BaseComponent } from '../../core/base';
import { SessionService } from '../../core/services/session.service';
import { ThemeService } from '../../core/services/theme.service';
import { ShellFacade } from './shell.facade';

@Component({
  selector: 'app-shell',
  standalone: false,
  templateUrl: './shell.component.html'
})
export class ShellComponent extends BaseComponent implements OnInit {
  mobileNavOpen = false;
  title = 'Dashboard';
  description = 'Monitor datasets, runs, and evaluation readiness.';

  readonly session = inject(SessionService);
  readonly theme = inject(ThemeService);

  constructor(private readonly facade: ShellFacade) {
    super();
  }

  get primaryNav() {
    return this.facade.primaryNav;
  }

  get analysisNav() {
    return this.facade.analysisNav;
  }

  get isDark(): boolean {
    return this.theme.isDark;
  }

  ngOnInit(): void {
    this.applyHeading(this.router.url);
    this.watch(this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd)
    )).subscribe(event => {
      this.applyHeading(event.urlAfterRedirects);
      this.closeMobileNav();
    });
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  toggleMobileNav(): void {
    this.mobileNavOpen = !this.mobileNavOpen;
  }

  closeMobileNav(): void {
    this.mobileNavOpen = false;
  }

  initials(name: string): string {
    return this.facade.initials(name);
  }

  private applyHeading(url: string): void {
    const heading = this.facade.headingFor(url);
    this.title = heading.title;
    this.description = heading.description;
  }
}
