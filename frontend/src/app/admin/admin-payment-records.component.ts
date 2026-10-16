import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';
import { isoDate, uploadUrl } from '../shared/format';
import { InstallmentRow, installmentBadge } from '../shared/installments';
import { CardTableDirective } from '../shared/card-table.directive';
import { AppDatePipe } from '../shared/app-date';
import { DateInputComponent } from '../shared/date-input.component';

/**
 * Admin > Payment Records: every installment between sponsors and students. They are created when a student is
 * mapped to a sponsor (course semesters, Payment Config of the session year, payment start date). Chapter-scoped
 * users see their own chapter's students. With edit permission a payment can be recorded against an installment.
 */
@Component({
  selector: 'app-admin-payment-records',
  standalone: true,
  imports: [DateInputComponent, AppDatePipe, CommonModule, FormsModule, AlertsComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Payment Records</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-6 col-xl-3" *ngFor="let s of summary">
        <div class="card-box clickable" [class.selected]="status === s.status" (click)="status = status === s.status ? '' : s.status; table.filter()">
          <h4 class="header-title mt-0"><span class="badge mr-1" [ngClass]="badge(s.status)">&nbsp;</span>{{ s.status }}</h4>
          <h2>{{ s.count }}</h2>
          <small class="text-muted">installment{{ s.count === 1 ? '' : 's' }}</small>
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
          <input class="form-control" style="max-width: 280px" placeholder="Search student or sponsor" [(ngModel)]="q" (ngModelChange)="table.search()">
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-0">
            <thead><tr><th>Student</th><th>Sponsor</th><th>Installment</th><th>Due date</th><th>Status</th><th>Paid on</th><th class="tablet-hide">Receipt</th><th *ngIf="canEdit"></th></tr></thead>
            <tbody>
              <tr *ngIf="table.loading && !table.rows.length"><td colspan="8" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!table.loading && !table.rows.length"><td colspan="8" class="text-center text-muted">No installments.</td></tr>
              <tr *ngFor="let r of table.rows">
                <td><strong>{{ r.student_name }}</strong><div class="small text-muted">{{ r.student_code }}</div></td>
                <td>{{ r.sponsor_name || '--' }}<div class="small text-muted">{{ r.sponsor_code }}</div></td>
                <td class="font-weight-bold">Installment {{ r.installment_no }}</td>
                <td>{{ r.due_date | appDate }}</td>
                <td><span class="badge" [ngClass]="badge(r.status)">{{ r.status }}</span></td>
                <td>
                  <ng-container *ngIf="r.payment_id; else dash">{{ r.paid_date | appDate }}</ng-container>
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
        <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
      </div>
    </div>

    <div *ngIf="pay" class="modal fade show d-block" tabindex="-1" (click)="pay = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Record payment: {{ pay.row.student_name }}, installment {{ pay.row.installment_no }}</h5>
            <button type="button" class="close" (click)="pay = null">&times;</button>
          </div>
          <div class="modal-body">
            <p class="text-muted small">Credited to {{ pay.row.sponsor_name }} ({{ pay.row.sponsor_code }}); due {{ pay.row.due_date | appDate }}.</p>
            <div class="form-group"><label>Payment date</label><app-date-input [(ngModel)]="pay.paymentDate"></app-date-input></div>
            <div class="form-group"><label>Amount paid (INR)</label><input type="number" step="0.01" min="1" class="form-control" [(ngModel)]="pay.amount">
              <small class="text-muted">As on the bank receipt. Rahbar keeps it; pages show the installment, not the amount.</small></div>
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
export class AdminPaymentRecordsComponent implements OnInit, OnDestroy {
  q = '';
  status = '';
  readonly table = new ServerTable<InstallmentRow, PagedResult<InstallmentRow> & { summary: { status: string; count: number }[] }>(
    (p) => this.api.get('/admin/payment-records', { q: this.q, status: this.status, ...p }),
    (e) => (this.error = errorText(e, 'Could not load payment records.')));
  saving = false;
  message = '';
  error = '';
  pay: { row: InstallmentRow; paymentDate: string; amount: number | null; receipt: File | null } | null = null;

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('PAYMENT_RECORDS', 'EDIT'); }

  ngOnInit(): void { this.table.load(); }
  ngOnDestroy(): void { this.table.destroy(); }

  /** Installments in each status (of everything the user may see; search and status filter aside). */
  get summary(): { status: string; count: number }[] { return this.table.last?.summary ?? []; }

  badge(status: string): string { return installmentBadge(status); }
  link(path: string | null): string | null { return uploadUrl(path); }
  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }

  openPay(r: InstallmentRow): void {
    this.pay = { row: r, paymentDate: isoDate(new Date()), amount: null, receipt: null };
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
      next: (r) => { this.saving = false; this.pay = null; this.message = r.message; this.table.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not record the payment.'); }
    });
  }
}
