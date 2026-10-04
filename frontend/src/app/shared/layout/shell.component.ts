import { Component, HostListener, Input, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

export interface NavLink {
  path: string;
  label: string;
  icon?: string;
}

/**
 * Layout ported from the Flask templates (topbar + left sidebar + fixed footer,
 * Adminto theme). Title and menu come from the route's `data` (see app.routes.ts).
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div id="wrapper">
      <!-- Topbar -->
      <div class="navbar-custom">
        <div class="container-fluid">
          <ul class="list-unstyled topnav-menu topnav-menu-left m-0 float-left">
            <li>
              <button class="button-menu-mobile waves-effect" (click)="toggleSidebar()">
                <i class="mdi mdi-menu"></i>
              </button>
            </li>
            <li class="d-none d-lg-block">
              <h6 class="page-title mb-0 mt-3 ml-2">{{ title }}</h6>
            </li>
          </ul>

          <ul class="list-unstyled topnav-menu float-right mb-0">
            <li class="dropdown notification-list" (click)="$event.stopPropagation()">
              <a class="nav-link dropdown-toggle nav-user waves-effect" href="#" role="button" (click)="toggleMenu($event)">
                <img src="assets/theme/images/users/avatar-1.jpg" alt="user-image" class="rounded-circle" />
                <span class="d-none d-sm-inline-block ml-1">{{ auth.currentUser()?.name }}</span>
              </a>
              <div class="dropdown-menu dropdown-menu-right profile-dropdown" [class.show]="menuOpen">
                <a href="#" class="dropdown-item notify-item" (click)="logout($event)">
                  <i class="mdi mdi-logout-variant"></i>
                  <span>Logout</span>
                </a>
              </div>
            </li>
          </ul>

          <div class="logo-box-centered">
            <a [routerLink]="homeLink" class="text-success"><h4 class="m-0">RSMS</h4></a>
          </div>
        </div>
      </div>

      <!-- Left sidebar -->
      <div class="left-side-menu">
        <div class="slimscroll-menu">
          <div id="sidebar-menu">
            <ul class="metismenu" id="side-menu">
              <li class="menu-title">Navigation</li>
              <li *ngFor="let link of links" routerLinkActive="mm-active">
                <a [routerLink]="link.path" routerLinkActive="active" class="waves-effect" (click)="closeMobileSidebar()">
                  <i class="mdi" [ngClass]="link.icon || 'mdi-circle-outline'"></i>
                  <span>{{ link.label }}</span>
                </a>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Page content -->
      <div class="content-page">
        <div class="content">
          <div class="container-fluid">
            <router-outlet></router-outlet>
          </div>
        </div>
        <footer class="footer">
          <p class="mb-0">Developed by <strong>Cognifly AI System</strong></p>
        </footer>
      </div>
    </div>
  `
})
export class ShellComponent implements OnDestroy {
  @Input() title = '';
  @Input() links: NavLink[] = [];
  menuOpen = false;

  get homeLink(): string { return this.links.length ? this.links[0].path : '/'; }

  constructor(public auth: AuthService, private router: Router) {}

  /** Same behaviour as the theme's app.min.js: collapse on desktop, slide-in on mobile. */
  toggleSidebar(): void {
    if (window.innerWidth < 768) {
      document.body.classList.toggle('sidebar-enable');
    } else {
      document.body.classList.toggle('enlarged');
    }
  }

  closeMobileSidebar(): void {
    document.body.classList.remove('sidebar-enable');
  }

  toggleMenu(event: Event): void {
    event.preventDefault();
    this.menuOpen = !this.menuOpen;
  }

  @HostListener('document:click')
  closeMenu(): void {
    this.menuOpen = false;
  }

  logout(event: Event): void {
    event.preventDefault();
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  ngOnDestroy(): void {
    document.body.classList.remove('sidebar-enable', 'enlarged');
  }
}
