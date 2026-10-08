import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';

interface Due {
  student_id: number; student_code: string; student_name: string; student_phone: string | null;
  sponsor_id: number; sponsor_code: string | null; sponsor_name: string | null;
  course_start: string | null; installments_total: number; installments_due: number; installments_paid: number;
  overdue: number; next_due_date: string | null; status: string;
}

/** Admin > Payment Dues: quarterly installments due vs paid per sponsored student, and reminders. */
@Component({
  selector: 'app-admin-payment-dues',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
          <h4 class="page-title mb-2 mb-md-0">Payment Dues</h4>
          <button *ngIf="canEdit" class="btn btn-primary" (click)="send()" [disabled]="sending">
            <i class="mdi mdi-send mr-1"></i>{{ sending ? 'Sending…' : 'Send reminders now' }}
          </button>
        </div>
      </div>
    </div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-6 col-xl-3" *ngFor="let s of summary">
        <div class="card-box clickable" [class.selected]="filter === s.status" (click)="filter = filter === s.status ? '' : s.status">
          <h4 class="header-title mt-0">{{ s.status }}</h4>
          <h2>{{ s.count }}</h2>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
          <p class="text-muted mb-2 mb-md-0">
            From the installments created when each student was mapped to a sponsor (see Payment Records).
            Reminders go out automatically every morning: once per overdue installment, and a week before the next one.
          </p>
          <input class="form-control" style="max-width: 260px" placeholder="Search student or sponsor" [(ngModel)]="q">
        </div>
        <div class="table-responsive">
          <table class="table mb-0">
            <thead><tr><th>Student</th><th>Sponsor</th><th>Payments start</th><th>Paid / due / total</th><th>Next due</th><th>Status</th></tr></thead>
            <tbody>
              <tr *ngIf="loading"><td colspan="6" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !visible.length"><td colspan="6" class="text-center text-muted">Nothing to show.</td></tr>
              <tr *ngFor="let d of visible | paginate: pg.page : pg.size">
                <td><strong>{{ d.student_name }}</strong><div class="small text-muted">{{ d.student_code }}</div></td>
                <td>{{ d.sponsor_name || '--' }}<div class="small text-muted">{{ d.sponsor_code }}</div></td>
                <td>{{ d.course_start ? (d.course_start | date: 'd MMM yyyy') : '--' }}</td>
                <td>
                  <strong>{{ d.installments_paid }}</strong> / {{ d.installments_due }} / {{ d.installments_total }}
                  <div class="progress mt-1" style="max-width: 140px" *ngIf="d.installments_total">
                    <div class="progress-bar" [style.width.%]="pct(d)"></div>
                  </div>
                </td>
                <td>{{ d.next_due_date ? (d.next_due_date | date: 'd MMM yyyy') : '--' }}</td>
                <td>
                  <span class="badge" [ngClass]="badge(d.status)">{{ d.status }}</span>
                  <div class="small text-danger" *ngIf="d.overdue">{{ d.overdue }} overdue</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [state]="pg" [total]="visible.length"></app-pager>
      </div>
    </div>
  `
})
export class AdminPaymentDuesComponent implements OnInit {
  readonly pg = new PageState();
  rows: Due[] = [];
  q = '';
  filter = '';
  loading = false;
  sending = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('PAYMENT_DUES', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<Due[]>('/admin/payments/dues').subscribe({
      next: (r) => { this.loading = false; this.rows = r; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load payment dues.'); }
    });
  }

  get summary(): { status: string; count: number }[] {
    // "On hold" (students whose studies are paused) only shows when there are any.
    return ['Overdue', 'Due soon', 'On schedule', 'Completed', 'On hold'].map((status) => ({
      status, count: this.rows.filter((r) => r.status === status).length
    })).filter((s) => s.status !== 'On hold' || s.count);
  }

  get visible(): Due[] {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter((r) => (!this.filter || r.status === this.filter)
      && (!q || [r.student_name, r.student_code, r.sponsor_name, r.sponsor_code].some((v) => (v ?? '').toLowerCase().includes(q))));
  }

  pct(d: Due): number { return d.installments_total ? Math.min(100, (d.installments_paid / d.installments_total) * 100) : 0; }

  badge(status: string): string {
    switch (status) {
      case 'Overdue': return 'badge-danger';
      case 'Due soon': return 'badge-warning';
      case 'On schedule': return 'badge-info';
      case 'Completed': return 'badge-success';
      case 'On hold': return 'badge-secondary';
      default: return 'badge-light';
    }
  }

  send(): void {
    this.sending = true;
    this.api.post<{ message: string }>('/admin/payments/reminders/run', {}).subscribe({
      next: (r) => { this.sending = false; this.message = r.message; },
      error: (e) => { this.sending = false; this.error = errorText(e, 'Could not send reminders.'); }
    });
  }
}
