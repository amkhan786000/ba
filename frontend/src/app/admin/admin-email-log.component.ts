import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface EmailRow {
  email_id: number; sent_at: string; to_address: string; recipient_code: string | null; recipient_name: string | null;
  subject: string | null; attachments: string | null; status: 'SENT' | 'FAILED'; error: string | null; sent_by_name: string | null;
}
interface EmailDetail extends EmailRow { body: string | null }

/**
 * Admin > Email Log: every email the application sent (or tried to send), with its full text, as evidence.
 * One-time codes and temporary passwords are stored as [hidden].
 */
@Component({
  selector: 'app-admin-email-log',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, CardTableDirective],
  styles: [`
    .subject { max-width: 380px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    @media (max-width: 991.98px) { .subject { max-width: 180px; } }
    .email-body { white-space: pre-wrap; font-family: inherit; background: #f8f9fa; border-radius: .25rem; padding: 1rem; margin: 0; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Email Log</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="card">
      <div class="card-body">
        <p class="text-muted">
          Every email the application sends is kept here with its full text: notifications, reminders, broadcasts,
          mapping emails and sign-in codes (codes and temporary passwords are hidden).
        </p>
        <div class="d-flex flex-column flex-lg-row mb-3">
          <input class="form-control mb-2 mb-lg-0 mr-lg-2" style="max-width: 320px" placeholder="Search address or subject"
                 [(ngModel)]="q" (ngModelChange)="search$.next()">
          <select class="form-control mb-2 mb-lg-0 mr-lg-2" style="max-width: 160px" [(ngModel)]="status" (ngModelChange)="reload()">
            <option value="">All</option><option value="SENT">Sent</option><option value="FAILED">Failed</option>
          </select>
          <div class="d-flex align-items-center">
            <label class="mb-0 mr-1 small text-muted" for="from">From</label>
            <input id="from" type="date" class="form-control mr-2" [(ngModel)]="from" (ngModelChange)="reload()">
            <label class="mb-0 mr-1 small text-muted" for="to">To</label>
            <input id="to" type="date" class="form-control" [(ngModel)]="to" (ngModelChange)="reload()">
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead><tr><th>Sent</th><th>To</th><th>Subject</th><th>Status</th><th>Triggered by</th></tr></thead>
            <tbody>
              <tr *ngIf="loading"><td colspan="5" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !rows.length"><td colspan="5" class="text-center text-muted">No emails found.</td></tr>
              <tr *ngFor="let e of rows" class="clickable" (click)="open(e)">
                <td class="text-nowrap">{{ e.sent_at | date: 'd MMM yyyy, h:mm a' }}</td>
                <td>
                  {{ e.recipient_name || e.to_address }}
                  <div class="small text-muted">{{ e.recipient_code ? e.recipient_code + ' · ' : '' }}{{ e.recipient_name ? e.to_address : '' }}</div>
                </td>
                <td class="subject" [title]="e.subject">{{ e.subject }}<i *ngIf="e.attachments" class="mdi mdi-paperclip ml-1 text-muted"></i></td>
                <td>
                  <span class="badge" [ngClass]="e.status === 'SENT' ? 'badge-success' : 'badge-danger'">{{ e.status === 'SENT' ? 'Sent' : 'Failed' }}</span>
                </td>
                <td><small>{{ e.sent_by_name || 'System' }}</small></td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [total]="total" [page]="page" [pageSize]="pageSize" (pageChange)="page = $event; load()"></app-pager>
      </div>
    </div>

    <div *ngIf="detail" class="modal fade show d-block" tabindex="-1" (click)="detail = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ detail.subject }}</h5>
            <button type="button" class="close" (click)="detail = null">&times;</button>
          </div>
          <div class="modal-body">
            <dl class="row small mb-3">
              <dt class="col-sm-3">To</dt><dd class="col-sm-9">{{ detail.to_address }}<span *ngIf="detail.recipient_name"> ({{ detail.recipient_name }}, {{ detail.recipient_code }})</span></dd>
              <dt class="col-sm-3">Sent</dt><dd class="col-sm-9">{{ detail.sent_at | date: 'd MMM yyyy, h:mm:ss a' }}</dd>
              <dt class="col-sm-3">Status</dt>
              <dd class="col-sm-9">
                <span class="badge" [ngClass]="detail.status === 'SENT' ? 'badge-success' : 'badge-danger'">{{ detail.status === 'SENT' ? 'Sent' : 'Failed' }}</span>
                <span *ngIf="detail.error" class="text-danger ml-1">{{ detail.error }}</span>
              </dd>
              <ng-container *ngIf="detail.attachments"><dt class="col-sm-3">Attachments</dt><dd class="col-sm-9">{{ detail.attachments }}</dd></ng-container>
            </dl>
            <pre class="email-body">{{ detail.body }}</pre>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminEmailLogComponent implements OnInit, OnDestroy {
  rows: EmailRow[] = [];
  total = 0;
  page = 1;
  pageSize = 25;
  q = '';
  status = '';
  from = '';
  to = '';
  loading = false;
  error = '';
  detail: EmailDetail | null = null;
  readonly search$ = new Subject<void>();
  private sub?: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.sub = this.search$.pipe(debounceTime(300)).subscribe(() => this.reload());
    this.load();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  reload(): void { this.page = 1; this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<{ total: number; rows: EmailRow[] }>('/admin/email-log', {
      q: this.q, status: this.status, from: this.from, to: this.to, page: this.page - 1, size: this.pageSize
    }).subscribe({
      next: (r) => { this.loading = false; this.rows = r.rows; this.total = r.total; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the email log.'); }
    });
  }

  open(e: EmailRow): void {
    this.api.get<EmailDetail>(`/admin/email-log/${e.email_id}`).subscribe({
      next: (d) => (this.detail = d),
      error: (err) => (this.error = errorText(err, 'Could not open the email.'))
    });
  }
}
