import { Component, HostListener, Input, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ROLE_LABELS, Section } from '../../core/models/user.model';
import { NotificationBellComponent } from '../notifications/notification-bell.component';

export interface NavLink {
  path: string;
  label: string;
  icon?: string;
  /** Admin screens: shown only when the user may view this section. */
  section?: Section;
}

/**
 * App layout: dark brand sidebar + sticky top bar + content area.
 * Title and menu come from the route's `data` (see app.routes.ts). Styles live in src/styles.scss (.app-*).
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, NotificationBellComponent],
  template: `
    <div class="app" [class.app--collapsed]="collapsed" [class.app--mobile-open]="mobileOpen">
      <aside class="app-sidebar">
        <a class="app-brand" [routerLink]="homeLink" (click)="mobileOpen = false">
          <span class="brand-mark">ba</span>
          <span class="brand-text"><strong>Rahbar</strong><small>Bihar Anjuman</small></span>
        </a>
        <nav class="app-nav">
          <div class="nav-section">{{ title }}</div>
          <a *ngFor="let link of links" class="nav-item" [routerLink]="link.path" routerLinkActive="active"
             [title]="collapsed ? link.label : ''" (click)="mobileOpen = false">
            <i class="mdi" [ngClass]="link.icon || 'mdi-circle-outline'"></i>
            <span>{{ link.label }}</span>
          </a>
        </nav>
        <div class="sidebar-foot">Connecting people to serve humanity</div>
      </aside>
      <div class="app-backdrop" (click)="mobileOpen = false"></div>

      <div class="app-main">
        <header class="app-topbar">
          <button type="button" class="icon-btn" (click)="toggleSidebar()" aria-label="Toggle menu">
            <i class="mdi mdi-menu"></i>
          </button>
          <div class="topbar-crumbs">
            <small>{{ title }}</small>
            <strong>{{ currentLabel }}</strong>
          </div>
          <div class="topbar-right">
            <app-notification-bell (opened)="menuOpen = false"></app-notification-bell>
            <div class="user-wrap" (click)="$event.stopPropagation()">
            <div class="user-chip" role="button" tabindex="0" (click)="toggleUserMenu()" (keydown.enter)="toggleUserMenu()">
              <span class="avatar">{{ initials }}</span>
              <span class="user-meta">
                <strong>{{ auth.currentUser()?.name }}</strong>
                <small>{{ roleLabel }}</small>
              </span>
              <i class="mdi mdi-chevron-down"></i>
            </div>
            <div class="user-menu" *ngIf="menuOpen">
              <div class="user-menu-head">
                <strong>{{ auth.currentUser()?.name }}</strong>
                <small>{{ roleLabel }} · {{ auth.currentUser()?.userId }}</small>
              </div>
              <a [routerLink]="[sectionRoot, 'profile']" (click)="menuOpen = false"><i class="mdi mdi-account-circle-outline"></i><span>My profile</span></a>
              <a href="#" class="danger" (click)="logout($event)"><i class="mdi mdi-logout-variant"></i><span>Sign out</span></a>
            </div>
            </div>
          </div>
        </header>

        <main class="app-content">
          <router-outlet></router-outlet>
        </main>

        <footer class="app-footer">
          <span>© {{ year }} Rahbar · Bihar Anjuman</span>
          <span>Developed by <strong>Cognifly AI System</strong></span>
        </footer>
      </div>
    </div>
  `
})
export class ShellComponent implements OnDestroy {
  @Input() title = '';
  @Input() set links(v: NavLink[] | undefined) { this._links = v ?? []; this.updateLabel(); }
  /** Menu entries the user may open (admin entries carry the section they need VIEW on). */
  get links(): NavLink[] { return this._links.filter((l) => !l.section || this.auth.can(l.section)); }
  private _links: NavLink[] = [];

  @ViewChild(NotificationBellComponent) private bell?: NotificationBellComponent;

  menuOpen = false;
  mobileOpen = false;
  collapsed = false;
  currentLabel = '';
  readonly year = new Date().getFullYear();
  private sub: Subscription;

  constructor(public auth: AuthService, private router: Router) {
    // Permissions may have changed in Admin > Roles since sign-in: refresh them (the menu follows).
    if (this.auth.currentUser()) this.auth.refreshAccess().subscribe({ next: () => this.updateLabel(), error: () => {} });
    this.sub = this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.updateLabel();
      this.menuOpen = false;
    });
  }

  get homeLink(): string { return this.links.length ? this.links[0].path : '/'; }

  /** "/admin", "/sponsor", ... taken from the first menu link. */
  get sectionRoot(): string { return '/' + (this.homeLink.split('/')[1] ?? ''); }

  get roleLabel(): string {
    const user = this.auth.currentUser();
    return user?.roleName || ROLE_LABELS[user?.roleId ?? 0] || 'User';
  }

  get initials(): string {
    const name = (this.auth.currentUser()?.name ?? '').trim();
    if (!name) return '?';
    const parts = name.split(/\s+/);
    return ((parts[0][0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }

  /** Label of the menu entry the current URL belongs to (longest matching path wins). */
  private updateLabel(): void {
    const url = this.router.url.split(/[?#]/)[0];
    const match = this.links
      .filter((l) => url === l.path || url.startsWith(l.path + '/'))
      .sort((a, b) => b.path.length - a.path.length)[0];
    this.currentLabel = url.endsWith('/profile') ? 'My profile' : match?.label ?? this.title;
  }

  /** Opens / closes the user menu (and closes the notifications list, so only one menu is open). */
  toggleUserMenu(): void {
    this.menuOpen = !this.menuOpen;
    this.bell?.close();
  }

  /** Desktop: collapse to icons. Mobile: slide the menu in. */
  toggleSidebar(): void {
    if (window.innerWidth < 992) this.mobileOpen = !this.mobileOpen;
    else this.collapsed = !this.collapsed;
  }

  @HostListener('document:click')
  closeMenu(): void {
    this.menuOpen = false;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.menuOpen = false;
    this.mobileOpen = false;
  }

  logout(event: Event): void {
    event.preventDefault();
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }
}
