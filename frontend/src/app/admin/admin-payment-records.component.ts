import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { isoDate, uploadUrl } from '../shared/format';
import { InstallmentRow, installmentBadge } from '../shared/installments';
import { CardTableDirective } from '../shared/card-table.directive';

/**
 * Admin > Payment Records: every installment between sponsors and students. They are created when a student is
 * mapped to a sponsor (course semesters, Payment Config of the session year, payment start date). Chapter-scoped
 * users see their own chapter's students. With edit permission a payment can be recorded against an installment.
 */
@Component({
  selector: 'app-admin-payment-records',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Payment Records</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-6 col-xl-3" *ngFor="let s of summary">
        <div class="card-box clickable" [class.selected]="status === s.status" (click)="status = status === s.status ? '' : s.status; pg.reset()">
          <h4 class="header-title mt-0"><span class="badge mr-1" [ngClass]="badge(s.status)">&nbsp;</span>{{ s.status }}</h4>
          <h2>{{ s.count }}</h2>
          <small class="text-muted">₹{{ s.amount | number: '1.0-2' }}</small>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
          <p class="text-muted mb-2 mb-md-0 mr-md-3">
            Installments are created when a student is mapped to a sponsor, from the course's semesters, the Payment Config
            of the student's session year and the student's payment start date. <strong>Due</strong> = due soon (see "Show as due" in Payment Config), <strong>Overdue</strong> = the date has passed and it isn't paid.
          </p>
          <input class="form-control" style="max-width: 280px" placeholder="Search student or sponsor" [(ngModel)]="q" (ngModelChange)="pg.reset()">
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-0">
            <thead><tr><th>Student</th><th>Sponsor</th><th>#</th><th>Due date</th><th>Amount</th><th>Status</th><th>Paid</th><th class="tablet-hide">Receipt</th><th *ngIf="canEdit"></th></tr></thead>
            <tbody>
              <tr *ngIf="loading"><td colspan="9" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !visible.length"><td colspan="9" class="text-center text-muted">No installments.</td></tr>
              <tr *ngFor="let r of visible | paginate: pg.page : pg.size">
                <td><strong>{{ r.student_name }}</strong><div class="small text-muted">{{ r.student_code }}</div></td>
                <td>{{ r.sponsor_name || '--' }}<div class="small text-muted">{{ r.sponsor_code }}</div></td>
                <td>{{ r.installment_no }}</td>
                <td>{{ r.due_date | date: 'd MMM yyyy' }}</td>
                <td>₹{{ r.amount | number: '1.2-2' }}</td>
                <td><span class="badge" [ngClass]="badge(r.status)">{{ r.status }}</span></td>
                <td>
                  <ng-container *ngIf="r.payment_id; else dash">₹{{ r.paid_amount | number: '1.2-2' }}<div class="small text-muted">{{ r.paid_date | date: 'd MMM yyyy' }}</div></ng-container>
                </td>
                <td class="tablet-hide">
                  <a *ngIf="link(r.receipt_url) as l; else dash" [href]="l" target="_blank" rel="noopener">View</a>
                  <a *ngIf="link(r.student_proof_url) as p" [href]="p" target="_blank" rel="noopener" class="d-block small text-success">Spent proof</a>
                </td>
                <td *ngIf="canEdit"><button *ngIf="!r.payment_id" class="btn btn-xs btn-success" (click)="openPay(r)">Record payment</button></td>
              </tr>
            </tbody>
          </table>
          <ng-template #dash>--</ng-template>
        </div>
        <app-pager [state]="pg" [total]="visible.length"></app-pager>
      </div>
    </div>

    <div *ngIf="pay" class="modal fade show d-block" tabindex="-1" (click)="pay = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Record payment: {{ pay.row.student_name }}, installment #{{ pay.row.installment_no }}</h5>
            <button type="button" class="close" (click)="pay = null">&times;</button>
          </div>
          <div class="modal-body">
            <p class="text-muted small">Credited to {{ pay.row.sponsor_name }} ({{ pay.row.sponsor_code }}); due {{ pay.row.due_date | date: 'd MMM yyyy' }}.</p>
            <div class="form-group"><label>Payment date</label><input type="date" class="form-control" [(ngModel)]="pay.paymentDate"></div>
            <div class="form-group"><label>Amount (₹)</label><input type="number" step="0.01" min="0" class="form-control" [(ngModel)]="pay.amount"></div>
            <div class="form-group"><label>Receipt <span class="text-muted">(optional)</span></label>
              <input type="file" class="form-control-file" accept="image/*,application/pdf" (change)="pay.receipt = fileOf($event)">
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="pay = null">Cancel</button>
            <button type="button" class="btn btn-success" (click)="savePay()" [disabled]="saving || !pay.amount || !pay.paymentDate">{{ saving ? 'Saving…' : 'Save as paid' }}</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminPaymentRecordsComponent implements OnInit {
  readonly pg = new PageState(25);
  rows: InstallmentRow[] = [];
  q = '';
  status = '';
  loading = false;
  saving = false;
  message = '';
  error = '';
  pay: { row: InstallmentRow; paymentDate: string; amount: number | null; receipt: File | null } | null = null;

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('PAYMENT_RECORDS', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<InstallmentRow[]>('/admin/payment-records').subscribe({
      next: (r) => { this.loading = false; this.rows = r; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load payment records.'); }
    });
  }

  get summary(): { status: string; count: number; amount: number }[] {
    return ['Overdue', 'Due', 'Not Due', 'Paid'].map((status) => {
      const rows = this.rows.filter((r) => r.status === status);
      return { status, count: rows.length, amount: rows.reduce((sum, r) => sum + Number(status === 'Paid' ? r.paid_amount ?? 0 : r.amount), 0) };
    });
  }

  get visible(): InstallmentRow[] {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter((r) => (!this.status || r.status === this.status)
      && (!q || [r.student_name, r.student_code, r.sponsor_name, r.sponsor_code].some((v) => (v ?? '').toLowerCase().includes(q))));
  }

  badge(status: string): string { return installmentBadge(status); }
  link(path: string | null): string | null { return uploadUrl(path); }
  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }

  openPay(r: InstallmentRow): void {
    this.pay = { row: r, paymentDate: isoDate(new Date()), amount: Number(r.amount), receipt: null };
  }

  savePay(): void {
    if (!this.pay) return;
    const f = new FormData();
    f.append('actionType', 'create');
    f.append('granteeId', String(this.pay.row.student_id));
    f.append('installmentId', String(this.pay.row.installment_id));
    f.append('amount', String(this.pay.amount));
    f.append('paymentDate', this.pay.paymentDate);
    f.append('status', 'Paid');
    if (this.pay.receipt) f.append('receipt', this.pay.receipt);
    this.saving = true;
    this.api.post<{ message: string }>('/admin/payments/record', f).subscribe({
      next: (r) => { this.saving = false; this.pay = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not record the payment.'); }
    });
  }
}
