import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate, localDate, uploadUrl } from '../shared/format';

type Row = Record<string, any>;

interface StudentData {
  grantee: Row;
  bankDetails: Row;
  courseInfo: Row | null;
  paidRecords: Row[];
}

interface Installment { n: number; due: string; amount: string; status: string; badge: string; paidOn: string; paid: boolean }

/** Port of templates/convenor/payment.html (student picker, installment schedule, record-payment popup, history). */
@Component({
  selector: 'app-convenor-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
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
                <option *ngFor="let s of students" [value]="s['user_id']">{{ s['name'] }}</option>
              </select>
            </div>

            <div *ngIf="current">
              <h5>Student: {{ current.grantee['name'] || 'N/A' }}</h5>
              <p>Grantee Academic Year: {{ current.grantee['year'] || 'Not Specified' }}</p>
              <p>Bank: {{ current.bankDetails['bank_name'] || 'N/A' }} (Acc: {{ current.bankDetails['account_number'] || 'N/A' }})</p>
              <p>IFSC: {{ current.bankDetails['ifsc_code'] || 'N/A' }}</p>
              <p>{{ lastPayment }}</p>
              <p>Payment Status: {{ currentStatus }}</p>

              <p *ngIf="!current.courseInfo?.['assigned_at']" class="alert alert-warning">Payment schedule is not available. The student has not been assigned a course yet.</p>
              <ng-container *ngIf="current.courseInfo?.['assigned_at']">
                <hr><h5 class="mt-3">Full Payment Schedule</h5>
                <div class="table-responsive">
                  <table class="table table-hover table-centered">
                    <thead class="thead-light"><tr><th>#</th><th>Due Date</th><th>Amount Paid</th><th>Status</th><th>Paid On</th><th>Action</th></tr></thead>
                    <tbody>
                      <tr *ngFor="let i of schedule">
                        <td>{{ i.n }}</td><td>{{ i.due }}</td><td>{{ i.amount }}</td>
                        <td><span class="badge" [ngClass]="i.badge">{{ i.status }}</span></td>
                        <td>{{ i.paidOn }}</td>
                        <td>
                          <button *ngIf="i.paid" class="btn btn-sm btn-light" disabled>{{ i.status }}</button>
                          <button *ngIf="!i.paid" type="button" class="btn btn-sm btn-success" (click)="openPay(i)">Record Payment</button>
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
                  <tr *ngFor="let p of history">
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
  students: Row[] = [];
  dataMap: Record<string, StudentData> = {};
  history: Row[] = [];
  selectedId = '';
  current: StudentData | null = null;
  schedule: Installment[] = [];
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

  /** Same schedule rules as updateStudentDetails() in the Flask page. */
  select(): void {
    this.current = this.selectedId ? this.dataMap[this.selectedId] ?? null : null;
    this.schedule = [];
    if (!this.current) return;
    const records = this.current.paidRecords ?? [];
    const last = records[records.length - 1];
    this.lastPayment = last
      ? `Last Payment: ₹${parseFloat(String(last['amount'])).toFixed(2)} on ${localDate(last['payment_date'])} (Status: ${last['status']})`
      : 'Last Payment: No payments yet';

    const c = this.current.courseInfo;
    const base = asDate(c?.['assigned_at']);
    if (!c || !base) { this.currentStatus = 'Course not assigned'; return; }

    const quarterly = (Number(c['fees_per_semester']) || 0) / 2;
    const total = Math.floor((Number(c['number_of_semesters']) / 2) * 4);
    const today = new Date(); today.setHours(0, 0, 0, 0);
    let next = 'All Paid';
    for (let i = 1; i <= total; i++) {
      const due = new Date(base);
      due.setMonth(due.getMonth() + 3 * i);
      const rec = records[i - 1];
      if (rec) {
        const st = String(rec['status'] ?? '');
        const status = st.charAt(0).toUpperCase() + st.slice(1);
        const badge = st.toLowerCase() === 'paid' ? 'badge-success' : st.toLowerCase() === 'pending' ? 'badge-info' : 'badge-warning';
        this.schedule.push({ n: i, due: due.toLocaleDateString(), amount: `₹${parseFloat(String(rec['amount'])).toFixed(2)}`, status, badge, paidOn: localDate(rec['payment_date']), paid: true });
      } else {
        const overdue = due < today;
        const status = overdue ? 'Overdue' : 'Pending';
        if (next === 'All Paid') next = status;
        this.schedule.push({ n: i, due: due.toLocaleDateString(), amount: '—', status, badge: overdue ? 'badge-danger' : 'badge-warning', paidOn: '—', paid: false });
      }
    }
    this.currentStatus = next;
    this.quarterly = quarterly.toFixed(2);
  }

  private quarterly = '0.00';

  openPay(i: Installment): void { this.pay = { installment: i.n, amount: this.quarterly, receipt: null }; }

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
