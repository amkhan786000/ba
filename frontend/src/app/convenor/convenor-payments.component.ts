import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate, localDate, uploadUrl } from '../shared/format';
import { InstallmentRow, installmentBadge } from '../shared/installments';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';

type Row = Record<string, any>;

interface StudentData {
  grantee: Row;
  bankDetails: Row;
  courseInfo: Row | null;
  paidRecords: Row[];
  installments: InstallmentRow[];
  installmentProblem: string | null;
}

/** Convenor > Payments: student picker, the stored installments (Paid / Due / Not Due), record-payment popup, history. */
@Component({
  selector: 'app-convenor-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Convenor Payments</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-md-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Make a Payment</h4>
            <p class="text-muted">Select a student to view their payment schedule and record a new payment.</p>
            <div class="form-group">
              <label for="studentSelect">Select Student</label>
              <select class="form-control" id="studentSelect" [(ngModel)]="selectedId" (ngModelChange)="select()">
                <option value="">-- Select a Student --</option>
                <option *ngFor="let s of students" [value]="s['id']">{{ s['name'] }}</option>
              </select>
            </div>

            <div *ngIf="current">
              <h5>Student: {{ current.grantee['name'] || 'N/A' }}</h5>
              <p>Session Year: {{ current.grantee['year'] || 'Not Specified' }}</p>
              <p>Bank: {{ current.bankDetails['bank_name'] || 'N/A' }} (Acc: {{ current.bankDetails['account_number'] || 'N/A' }})</p>
              <p>IFSC: {{ current.bankDetails['ifsc_code'] || 'N/A' }}</p>
              <p>{{ lastPayment }}</p>
              <p>Payment Status: {{ currentStatus }}</p>

              <p *ngIf="current.installmentProblem" class="alert alert-warning">
                {{ current.installments.length ? 'No further installments: ' : 'The payment schedule is not ready yet: ' }}{{ current.installmentProblem }}
              </p>
              <ng-container *ngIf="current.installments.length">
                <hr><h5 class="mt-3">Installment Schedule</h5>
                <p class="text-muted small">Payments you record stay pending until the office approves them; then they pay the earliest unpaid installment.</p>
                <div class="table-responsive">
                  <table class="table table-hover table-centered">
                    <thead class="thead-light"><tr><th>#</th><th>Due Date</th><th>Amount</th><th>Status</th><th>Paid</th><th>Paid On</th><th>Action</th></tr></thead>
                    <tbody>
                      <tr *ngFor="let i of current.installments">
                        <td>{{ i.installment_no }}</td>
                        <td>{{ i.due_date | date: 'd MMM yyyy' }}</td>
                        <td>₹{{ i.amount | number: '1.2-2' }}</td>
                        <td><span class="badge" [ngClass]="badge(i.status)">{{ i.status }}</span></td>
                        <td>{{ i.paid_amount !== null ? '₹' + (i.paid_amount | number: '1.2-2') : '—' }}</td>
                        <td>{{ i.paid_date ? (i.paid_date | date: 'd MMM yyyy') : '—' }}</td>
                        <td>
                          <button *ngIf="i.payment_id" class="btn btn-sm btn-light" disabled>Paid</button>
                          <button *ngIf="!i.payment_id" type="button" class="btn btn-sm btn-success" (click)="openPay(i)">Record Payment</button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </ng-container>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Official Payment History</h4>
            <p class="text-muted">This table shows all payments recorded by you.</p>
            <div class="table-responsive mt-4">
              <table class="table table-centered mb-0">
                <thead><tr><th>Student Name</th><th>Amount</th><th>Date</th><th>Status</th><th>Receipt</th></tr></thead>
                <tbody>
                  <tr *ngIf="!history.length"><td colspan="5" class="text-center">No past payments found.</td></tr>
                  <tr *ngFor="let p of history | paginate: pg.page : pg.size">
                    <td>{{ p['grantee_name'] }}</td>
                    <td>₹{{ p['amount'] | number: '1.2-2' }}</td>
                    <td>{{ p['payment_date'] ? (iso(p['payment_date'])) : 'N/A' }}</td>
                    <td>
                      <span *ngIf="lower(p['status']) === 'paid'" class="badge badge-success">Paid</span>
                      <span *ngIf="lower(p['status']) === 'pending'" class="badge badge-info">Pending</span>
                      <span *ngIf="lower(p['status']) !== 'paid' && lower(p['status']) !== 'pending'" class="badge badge-secondary">{{ p['status'] }}</span>
                    </td>
                    <td><a *ngIf="p['receipt_url']; else noReceipt" [href]="file(p['receipt_url'])" target="_blank">View Receipt</a><ng-template #noReceipt>No receipt</ng-template></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="history.length"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="pay" class="modal fade show d-block" tabindex="-1" (click)="pay = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Record Payment: Installment #{{ pay.installment }}</h5>
            <button type="button" class="close" (click)="pay = null">&times;</button>
          </div>
          <form (ngSubmit)="submitPay()">
            <div class="modal-body">
              <p>You are recording a payment for <strong>{{ current?.grantee?.['name'] }}</strong>.</p>
              <div class="alert alert-info">
                <strong>Beneficiary Bank Details:</strong>
                <p class="mb-0">Beneficiary: {{ current?.bankDetails?.['account_name'] || 'N/A' }}</p>
                <p class="mb-0">Bank Name: {{ current?.bankDetails?.['bank_name'] || 'N/A' }}</p>
                <p class="mb-0">Account No: {{ current?.bankDetails?.['account_number'] || 'N/A' }}</p>
                <p class="mb-0">IFSC Code: {{ current?.bankDetails?.['ifsc_code'] || 'N/A' }}</p>
              </div>
              <hr />
              <div class="form-group"><label>Amount (INR)</label><input type="text" class="form-control" [value]="pay.amount" readonly /></div>
              <div class="form-group"><label>Upload Receipt</label><input type="file" class="form-control-file" (change)="pay.receipt = fileOf($event)" required /></div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="pay = null">Cancel</button>
              <button type="submit" class="btn btn-success" [disabled]="!pay.receipt || saving">Confirm and Submit</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class ConvenorPaymentsComponent implements OnInit {
  readonly pg = new PageState();
  students: Row[] = [];
  dataMap: Record<string, StudentData> = {};
  history: Row[] = [];
  /** users.id of the selected student (as text: the key of studentDataMap). */
  selectedId = '';
  current: StudentData | null = null;
  currentStatus = '';
  lastPayment = '';
  pay: { installment: number; amount: string; receipt: File | null } | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<{ studentsForDropdown: Row[]; studentDataMap: Record<string, StudentData>; pastPayments: Row[] }>('/convenor/payments').subscribe({
      next: (r) => { this.students = r.studentsForDropdown; this.dataMap = r.studentDataMap; this.history = r.pastPayments; this.select(); },
      error: (e) => (this.error = errorText(e, 'Could not load payments.'))
    });
  }

  lower(v: unknown): string { return String(v ?? '').toLowerCase(); }
  iso(v: unknown): string { const d = asDate(v); return d ? d.toISOString().slice(0, 10) : 'N/A'; }
  file(p: unknown): string { return uploadUrl(p) ?? '#'; }
  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }

  select(): void {
    this.current = this.selectedId ? this.dataMap[this.selectedId] ?? null : null;
    if (!this.current) return;
    const records = this.current.paidRecords ?? [];
    const last = records[records.length - 1];
    this.lastPayment = last
      ? `Last Payment: ₹${parseFloat(String(last['amount'])).toFixed(2)} on ${localDate(last['payment_date'])} (Status: ${last['status']})`
      : 'Last Payment: No payments yet';
    const rows = this.current.installments ?? [];
    this.currentStatus = !rows.length ? 'No schedule yet'
      : rows.every((r) => r.status === 'Paid') ? 'All Paid'
      : rows.some((r) => r.status === 'Due') ? 'Due' : 'Not Due';
  }

  badge(status: string): string { return installmentBadge(status); }

  openPay(i: InstallmentRow): void { this.pay = { installment: i.installment_no, amount: String(i.amount), receipt: null }; }

  submitPay(): void {
    if (!this.pay?.receipt || !this.selectedId) return;
    const f = new FormData();
    f.append('granteeId', this.selectedId);
    f.append('amount', this.pay.amount);
    f.append('receipt', this.pay.receipt);
    this.saving = true;
    this.api.post<{ message: string }>('/convenor/payments', f).subscribe({
      next: (r) => { this.saving = false; this.pay = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'An error occurred while processing the payment.'); }
    });
  }
}
