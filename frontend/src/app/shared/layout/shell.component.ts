import { Component, HostListener, Input, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ADMIN_LINKS, AuthUser, NAV_GROUPS, ROLE_LABELS, Section, areasOf, canAccess } from '../../core/models/user.model';

/** The first admin screen the user may open (after switching "View as"). */
function landingAdminPath(user: AuthUser | null): string {
  return ADMIN_LINKS.find((l) => l.section && canAccess(user, l.section))?.path ?? '/admin/profile';
}
import { NotificationBellComponent } from '../notifications/notification-bell.component';

export interface NavLink {
  path: string;
  label: string;
  icon?: string;
  /** Admin screens: shown only when the user may view this section. */
  section?: Section;
  /** Key of a NAV_GROUPS entry: the link is shown under that group's heading, which opens and closes. */
  group?: string;
  /** Shown to the Super Admin only. */
  superAdmin?: boolean;
  /** Shown to the Super Admin and the Application Admin only. */
  admin?: boolean;
}

/** One line of the sidebar: a link, or a group of links. */
type NavEntry = { link: NavLink; group?: undefined } | { group: { key: string; label: string; icon: string; links: NavLink[] }; link?: undefined };

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
          <ng-container *ngFor="let e of entries; trackBy: entryKey">
            <a *ngIf="e.link as link" class="nav-item" [routerLink]="link.path" routerLinkActive="active"
               [title]="collapsed ? link.label : ''" (click)="mobileOpen = false">
              <i class="mdi" [ngClass]="link.icon || 'mdi-circle-outline'"></i>
              <span>{{ link.label }}</span>
            </a>
            <ng-container *ngIf="e.group as g">
              <button type="button" class="nav-item nav-group" [class.open]="isOpen(g.key)" [class.has-active]="groupActive(g.key)"
                      [attr.aria-expanded]="isOpen(g.key)" [title]="collapsed ? g.label : ''" (click)="toggleGroup(g.key)">
                <i class="mdi" [ngClass]="g.icon"></i>
                <span>{{ g.label }}</span>
                <i class="mdi mdi-chevron-down nav-caret"></i>
              </button>
              <div class="nav-sub" *ngIf="isOpen(g.key) && !collapsed">
                <a *ngFor="let link of g.links; trackBy: linkKey" class="nav-item" [routerLink]="link.path" routerLinkActive="active" (click)="mobileOpen = false">
                  <i class="mdi" [ngClass]="link.icon || 'mdi-circle-outline'"></i>
                  <span>{{ link.label }}</span>
                </a>
              </div>
            </ng-container>
          </ng-container>
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
            <a class="help-btn" [routerLink]="[sectionRoot, 'faq']" routerLinkActive="active" title="Help & FAQ" aria-label="Help & FAQ">
              <i class="mdi mdi-help-circle-outline"></i><span>Help</span>
            </a>
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
              <ng-container *ngIf="viewAsRoles.length > 1">
                <div class="user-menu-label">View as</div>
                <a href="" (click)="$event.preventDefault(); viewAs(null)" [class.active]="sectionRoot === '/admin' && !acting">
                  <i class="mdi mdi-account-multiple-outline"></i><span>All my roles</span><i *ngIf="sectionRoot === '/admin' && !acting" class="mdi mdi-check ml-auto"></i></a>
                <a *ngFor="let r of viewAsRoles" href="" (click)="$event.preventDefault(); viewAs(r.id)" [class.active]="acting === r.id">
                  <i class="mdi mdi-account-eye-outline"></i><span>{{ r.name }}</span><i *ngIf="acting === r.id" class="mdi mdi-check ml-auto"></i></a>
              </ng-container>
              <ng-container *ngIf="areas.length > 1">
                <div class="user-menu-label">Switch to</div>
                <a *ngFor="let a of areas" [routerLink]="a.path" (click)="goArea($event, a)" [class.active]="a.root === sectionRoot">
                  <i class="mdi" [ngClass]="a.root === '/admin' ? 'mdi-shield-account-outline' : 'mdi-account-switch-outline'"></i><span>{{ a.label }}</span>
                  <i *ngIf="a.root === sectionRoot" class="mdi mdi-check ml-auto"></i></a>
              </ng-container>
              <a [routerLink]="[sectionRoot, 'profile']" (click)="menuOpen = false"><i class="mdi mdi-account-circle-outline"></i><span>My profile</span></a>
              <a [routerLink]="[sectionRoot, 'faq']" (click)="menuOpen = false"><i class="mdi mdi-help-circle-outline"></i><span>Help & FAQ</span></a>
              <a href="#" class="danger" (click)="logout($event)"><i class="mdi mdi-logout-variant"></i><span>Sign out</span></a>
            </div>
            </div>
          </div>
        </header>

        <main class="app-content">
          <div *ngIf="actingName" class="alert alert-info d-flex flex-wrap align-items-center py-2 acting-banner">
            <i class="mdi mdi-account-eye-outline mr-2"></i>
            <span class="flex-fill">Viewing as <strong>{{ actingName }}</strong>: you see the menu and records this role gets.</span>
            <button type="button" class="btn btn-sm btn-outline-primary ml-2" (click)="viewAs(null)">Back to all my roles</button>
          </div>
          <div *ngIf="auth.currentUser()?.emailMissing && !emailBannerHidden" class="alert alert-warning d-flex align-items-start email-banner">
            <i class="mdi mdi-email-alert-outline mr-2 mt-1"></i>
            <div class="flex-fill">
              <strong>Add your email address.</strong>
              We don't have a real email for you, so you miss reminders, payment notices and sign-in help.
              <a [routerLink]="[sectionRoot, 'profile']" class="font-weight-bold ml-1">Add it in My profile</a>
            </div>
            <button type="button" class="close ml-2" aria-label="Hide" (click)="hideEmailBanner()"><span>&times;</span></button>
          </div>
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
  @Input() set links(v: NavLink[] | undefined) { this._links = v ?? []; this.updateLabel(); this.openActiveGroup(); }
  /** Menu entries the user may open (admin entries carry the section they need VIEW on). */
  get links(): NavLink[] {
    return this._links.filter((l) => (!l.section || this.auth.can(l.section)) && (!l.superAdmin || this.auth.isSuperAdmin())
      && (!l.admin || this.auth.isAdmin()));
  }

  /** The sidebar: plain links, and groups (in the order of their first link) holding the links the user may open. */
  get entries(): NavEntry[] {
    const out: NavEntry[] = [];
    const groups = new Map<string, NavLink[]>();
    for (const link of this.links) {
      const def = link.group ? NAV_GROUPS[link.group] : undefined;
      if (!link.group || !def) { out.push({ link }); continue; }
      let list = groups.get(link.group);
      if (!list) {
        list = [];
        groups.set(link.group, list);
        out.push({ group: { key: link.group, label: def.label, icon: def.icon, links: list } });
      }
      list.push(link);
    }
    return out;
  }

  entryKey(_: number, e: NavEntry): string { return e.link ? e.link.path : 'group:' + e.group!.key; }

  linkKey(_: number, l: NavLink): string { return l.path; }

  /** Groups the user opened; the group of the current page opens by itself. */
  private openGroups = new Set<string>();

  isOpen(key: string): boolean { return this.openGroups.has(key); }

  groupActive(key: string): boolean {
    const url = this.router.url.split(/[?#]/)[0];
    return this.links.some((l) => l.group === key && (url === l.path || url.startsWith(l.path + '/')));
  }

  toggleGroup(key: string): void {
    if (this.collapsed) {
      // icons-only sidebar: widen it and show the group
      this.collapsed = false;
      this.openGroups.add(key);
      return;
    }
    if (this.openGroups.has(key)) this.openGroups.delete(key); else this.openGroups.add(key);
  }

  private openActiveGroup(): void {
    for (const e of this.entries) if (e.group && this.groupActive(e.group.key)) this.openGroups.add(e.group.key);
  }
  private _links: NavLink[] = [];

  @ViewChild(NotificationBellComponent) private bell?: NotificationBellComponent;

  menuOpen = false;
  mobileOpen = false;
  /** The "add your email" banner was hidden for this browser session. */
  emailBannerHidden = (() => { try { return sessionStorage.getItem('rahbar_email_banner') === 'hidden'; } catch { return false; } })();
  collapsed = false;
  currentLabel = '';
  readonly year = new Date().getFullYear();
  private sub: Subscription;

  constructor(public auth: AuthService, private router: Router) {
    // Permissions may have changed in Admin > Roles since sign-in: refresh them (the menu follows).
    if (this.auth.currentUser()) this.auth.refreshAccess().subscribe({ next: () => this.updateLabel(), error: () => {} });
    this.sub = this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.updateLabel();
      this.openActiveGroup();
      this.menuOpen = false;
    });
  }

  hideEmailBanner(): void {
    this.emailBannerHidden = true;
    try { sessionStorage.setItem('rahbar_email_banner', 'hidden'); } catch { /* private mode */ }
  }

  get homeLink(): string { return this.links.length ? this.links[0].path : '/'; }

  /** "/admin", "/sponsor", ... taken from the first menu link. */
  get sectionRoot(): string { return '/' + (this.homeLink.split('/')[1] ?? ''); }

  /** Admin area and portals this person can switch between (several roles). */
  get areas(): { label: string; path: string; root: string }[] { return areasOf(this.auth.currentUser()); }

  /** Staff roles the user can "View as" (shown when they have more than one; portals have their own area). */
  get viewAsRoles(): { id: number; name: string }[] { return (this.auth.currentUser()?.roles ?? []).filter((r) => !r.portal); }

  get acting(): number | null { return this.auth.currentUser()?.actingRoleId ?? null; }

  get actingName(): string | null {
    const id = this.acting;
    return id === null ? null : (this.auth.currentUser()?.roles ?? []).find((r) => r.id === id)?.name ?? 'one role';
  }

  /** Switches "View as" and opens the first screen that role may use. */
  viewAs(roleId: number | null): void {
    this.menuOpen = false;
    this.auth.actAs(roleId).subscribe({
      next: () => this.router.navigateByUrl(landingAdminPath(this.auth.currentUser())),
      error: () => {}
    });
  }

  /** Going to a portal leaves "View as" (a portal is one role already). */
  goArea(event: Event, a: { path: string; root: string }): void {
    this.menuOpen = false;
    if (a.root !== '/admin' && this.acting !== null) {
      event.preventDefault();
      this.auth.actAs(null).subscribe({ next: () => this.router.navigateByUrl(a.path), error: () => {} });
    }
  }

  get roleLabel(): string {
    const user = this.auth.currentUser();
    if (this.actingName) return 'Viewing as ' + this.actingName;
    const main = user?.roleName || ROLE_LABELS[user?.roleId ?? 0] || 'User';
    const more = (user?.roles?.length ?? 1) - 1;
    return more > 0 ? main + ' +' + more : main;
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
