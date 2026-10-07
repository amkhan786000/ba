import { Component, EventEmitter, HostListener, OnDestroy, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription, interval, startWith, switchMap, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';

interface NotificationItem {
  id: number; title: string; message: string; category: string | null; link: string | null; read: boolean; createdAt: string | null;
}

const ICONS: Record<string, string> = {
  application: 'mdi-file-document-outline',
  payment: 'mdi-cash-multiple',
  reminder: 'mdi-alarm',
  progress: 'mdi-chart-line',
  mapping: 'mdi-account-switch',
  account: 'mdi-shield-account',
  announcement: 'mdi-bullhorn'
};

/** Bell in the top bar: unread badge (checked every minute) and a dropdown of the latest notifications. */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bell" (click)="$event.stopPropagation()">
      <button type="button" class="icon-btn" (click)="toggle()" aria-label="Notifications">
        <i class="mdi mdi-bell-outline"></i>
        <span class="bell-badge" *ngIf="unread > 0">{{ unread > 99 ? '99+' : unread }}</span>
      </button>
      <div class="bell-menu" *ngIf="open">
        <div class="bell-head">
          <strong>Notifications</strong>
          <button type="button" class="btn btn-link btn-sm p-0" *ngIf="unread > 0" (click)="markAll()">Mark all as read</button>
        </div>
        <div class="bell-list">
          <div *ngIf="loading" class="bell-empty"><span class="spinner-border spinner-border-sm"></span></div>
          <div *ngIf="!loading && !items.length" class="bell-empty">
            <i class="mdi mdi-bell-check-outline"></i>
            <span>You're all caught up.</span>
          </div>
          <a *ngFor="let n of items" class="bell-item" [class.unread]="!n.read" (click)="openItem(n)">
            <span class="bell-icon" [attr.data-cat]="n.category"><i class="mdi" [ngClass]="icon(n.category)"></i></span>
            <span class="bell-text">
              <strong>{{ n.title }}</strong>
              <span>{{ n.message }}</span>
              <small>{{ ago(n.createdAt) }}</small>
            </span>
          </a>
        </div>
      </div>
    </div>
  `
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  /** Emitted when the list opens, so the shell can close its other menus. */
  @Output() opened = new EventEmitter<void>();

  open = false;
  loading = false;
  unread = 0;
  items: NotificationItem[] = [];
  private sub?: Subscription;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.sub = interval(60_000).pipe(
      startWith(0),
      switchMap(() => this.api.get<{ unread: number }>('/notifications/unread-count').pipe(catchError(() => of({ unread: this.unread }))))
    ).subscribe((r) => (this.unread = r.unread));
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  toggle(): void {
    this.open = !this.open;
    if (this.open) {
      this.opened.emit();
      this.load();
    }
  }

  load(): void {
    this.loading = true;
    this.api.get<{ unread: number; items: NotificationItem[] }>('/notifications').subscribe({
      next: (r) => { this.loading = false; this.items = r.items; this.unread = r.unread; },
      error: () => (this.loading = false)
    });
  }

  openItem(n: NotificationItem): void {
    if (!n.read) {
      n.read = true;
      this.unread = Math.max(0, this.unread - 1);
      this.api.post(`/notifications/${n.id}/read`, {}).subscribe();
    }
    if (n.link) {
      this.open = false;
      this.router.navigateByUrl(n.link);
    }
  }

  markAll(): void {
    this.api.post('/notifications/read-all', {}).subscribe(() => {
      this.items.forEach((n) => (n.read = true));
      this.unread = 0;
    });
  }

  icon(category: string | null): string { return ICONS[category ?? ''] ?? 'mdi-bell-outline'; }

  ago(value: string | null): string {
    if (!value) return '';
    const then = new Date(value).getTime();
    if (isNaN(then)) return '';
    const mins = Math.round((Date.now() - then) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins} min ago`;
    const hours = Math.round(mins / 60);
    if (hours < 24) return `${hours} h ago`;
    const days = Math.round(hours / 24);
    return days < 7 ? `${days} d ago` : new Date(value).toLocaleDateString();
  }

  @HostListener('document:click')
  @HostListener('document:keydown.escape')
  close(): void { this.open = false; }
}
