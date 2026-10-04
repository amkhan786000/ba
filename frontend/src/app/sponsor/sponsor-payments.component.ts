import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate, isoDate, localDate, uploadUrl } from '../shared/format';

type Row = Record<string, any>;

interface Detail {
  grantee: Row;
  bankDetails: Row | null;
  payments: Row[];
  courseInfo: Row | null;
  annualScheduleAmount: number | string | null;
}

interface Installment {
  n: number; due: string; paidDate: string; expected: string; actual: string;
  status: string; badge: string; payment: Row | null; receipt: string | null; proof: string | null;
}

/** Port of templates/sponsor/payment.html */
@Component({
  selector: 'app-sponsor-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  styles: [`.table-schedule td, .table-schedule th { vertical-align: middle; }`],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Sponsorship Payments</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Process Student Installment</h4>
            <div class="form-group">
              <label for="studentSelect">Select Assigned Student</label>
              <select class="form-control" id="studentSelect" [(ngModel)]="selectedId" (ngModelChange)="select()">
                <option value="">-- Select a Student --</option>
                <option *ngFor="let d of details" [value]="d.grantee['user_id']">{{ d.grantee['name'] }} ({{ d.grantee['user_id'] }})</option>
              </select>
            </div>

            <div *ngIf="current" class="mt-4">
              <div class="row mb-3">
                <div class="col-12 col-md-6 border-right">
                  <h5 class="text-success mb-1">{{ current.grantee['name'] }}</h5>
                  <p class="mb-1 text-muted">Student ID: <span class="font-weight-bold text-dark">{{ current.grantee['user_id'] }}</span></p>
                  <p class="mb-1 text-muted">Academic Year: <span class="text-dark">{{ current.grantee['year'] || 'N/A' }}</span></p>
                </div>
                <div class="col-12 col-md-6 pl-md-4">
                  <h6 class="text-primary"><i class="mdi mdi-bank"></i> Bank Verification</h6>
                  <p class="mb-0">Bank: {{ current.bankDetails?.['bank_name'] || 'N/A' }} (A/C: {{ current.bankDetails?.['account_number'] || 'N/A' }})</p>
                  <p class="mb-0">IFSC: {{ current.bankDetails?.['ifsc_code'] || 'N/A' }}</p>
                </div>
              </div>

              <div *ngIf="!current.courseInfo?.['assigned_at']" class="alert alert-warning mt-3">The student has not been assigned a course yet.</div>
              <ng-container *ngIf="current.courseInfo?.['assigned_at']">
                <hr><h5 class="mt-3">Full Installment Schedule</h5>
                <div class="table-responsive">
                  <table class="table table-bordered table-schedule text-center">
                    <thead><tr><th>#</th><th>Exp. Date</th><th>Paid Date</th><th>Exp. Amount</th><th>Actual Paid</th><th>Status</th><th>Receipts</th><th>Action</th></tr></thead>
                    <tbody>
                      <tr *ngFor="let i of schedule" [class.table-success]="i.payment">
                        <td>{{ i.n }}</td><td>{{ i.due }}</td><td>{{ i.paidDate }}</td><td>₹{{ i.expected }}</td><td>{{ i.actual }}</td>
                        <td><span class="badge" [ngClass]="i.badge">{{ i.status }}</span></td>
                        <td>
                          <span *ngIf="!i.payment">—</span>
                          <div *ngIf="i.payment" class="d-flex flex-column align-items-center">
                            <a *ngIf="i.receipt" [href]="i.receipt" target="_blank" class="small text-info mb-1">Bank Receipt</a>
                            <span *ngIf="!i.receipt" class="small text-muted">No Receipt</span>
                            <a *ngIf="i.proof" [href]="i.proof" target="_blank" class="small text-success font-weight-bold">Spent Proof</a>
                            <span *ngIf="!i.proof" class="small text-muted font-italic">Spent Proof Pending</span>
                          </div>
                        </td>
                        <td>
                          <button *ngIf="i.payment" class="btn btn-xs btn-light" disabled>PAID</button>
                          <button *ngIf="!i.payment" type="button" class="btn btn-xs btn-success" (click)="openPay(i)">Pay Now</button>
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

    <div *ngIf="pay" class="modal fade show d-block" tabindex="-1" (click)="pay = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-success text-white">
            <h5 class="modal-title text-white">Recording Payment: Installment #{{ pay.installment }}</h5>
            <button type="button" class="close text-white" (click)="pay = null">&times;</button>
          </div>
          <form #pf="ngForm" (ngSubmit)="submitPay()">
            <div class="modal-body">
              <p>Payment for: <strong class="text-primary">{{ current?.grantee?.['name'] }}</strong></p>
              <div class="alert alert-light border small">
                <strong>Target Bank Details:</strong>
                <p class="mb-0">Beneficiary: {{ current?.bankDetails?.['account_name'] || 'N/A' }}</p>
                <p class="mb-0">Bank: {{ current?.bankDetails?.['bank_name'] || 'N/A' }}</p>
                <p class="mb-0">Account No: {{ current?.bankDetails?.['account_number'] || 'N/A' }}</p>
                <p class="mb-0">IFSC: {{ current?.bankDetails?.['ifsc_code'] || 'N/A' }}</p>
              </div>
              <div class="form-group"><label>Actual Date of Payment</label><input type="date" class="form-control" name="paymentDate" [(ngModel)]="pay.paymentDate" required></div>
              <div class="form-group"><label>Installment Amount (INR)</label><input type="number" step="0.01" class="form-control" name="amount" [(ngModel)]="pay.amount" required></div>
              <div class="form-group"><label>Upload Foundation Receipt</label><input type="file" class="form-control-file" (change)="pay.receipt = fileOf($event)" required></div>
            </div>
            <div class="modal-footer">
              <button type="submit" class="btn btn-success btn-block waves-effect waves-light" [disabled]="pf.invalid || !pay.receipt || saving">Confirm and Save Transaction</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class SponsorPaymentsComponent implements OnInit {
  details: Detail[] = [];
  map: Record<string, Detail> = {};
  selectedId = '';
  current: Detail | null = null;
  schedule: Installment[] = [];
  pay: { installment: number; amount: number | null; paymentDate: string; receipt: File | null } | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    // "Pay Now" on the dashboard links here with ?granteeId=...
    this.selectedId = this.route.snapshot.queryParamMap.get('granteeId') ?? '';
    this.load();
  }

  load(): void {
    this.api.get<{ paymentDetails: Detail[]; studentDataMap: Record<string, Detail> }>('/sponsor/payments').subscribe({
      next: (r) => { this.details = r.paymentDetails; this.map = r.studentDataMap; this.select(); },
      error: (e) => (this.error = errorText(e, 'Could not load payments.'))
    });
  }

  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }

  /** Same rules as updateStudentDetails() in the Flask page. */
  select(): void {
    this.current = this.selectedId ? this.map[this.selectedId] ?? null : null;
    this.schedule = [];
    const c = this.current?.courseInfo;
    const start = asDate(c?.['assigned_at']);
    if (!this.current || !c || !start) return;
    const paid = [...this.current.payments].reverse(); // API sends newest first
    const total = (parseInt(String(c['number_of_semesters']), 10) / 2) * 4;
    const expected = (parseFloat(String(this.current.annualScheduleAmount ?? 0)) / 4).toFixed(2);
    const now = new Date();
    for (let i = 1; i <= total; i++) {
      const due = new Date(start);
      due.setMonth(start.getMonth() + 3 * i);
      const p = paid[i - 1] ?? null;
      const overdue = !p && due < now;
      this.schedule.push({
        n: i, due: due.toLocaleDateString(), expected,
        paidDate: p ? localDate(p['payment_date']) : '—',
        actual: p ? `₹${parseFloat(String(p['amount'])).toFixed(2)}` : '—',
        status: p ? 'Paid' : overdue ? 'Overdue' : 'Pending',
        badge: p ? 'badge-success' : overdue ? 'badge-danger' : 'badge-warning',
        payment: p, receipt: p ? uploadUrl(p['receipt_url']) : null, proof: p ? uploadUrl(p['student_proof_url']) : null
      });
    }
  }

  openPay(i: Installment): void {
    this.pay = { installment: i.n, amount: Number(i.expected), paymentDate: isoDate(new Date()), receipt: null };
  }

  submitPay(): void {
    if (!this.pay?.receipt || !this.selectedId) return;
    const f = new FormData();
    f.append('granteeId', this.selectedId);
    f.append('amount', String(this.pay.amount ?? 0));
    f.append('paymentDate', this.pay.paymentDate);
    f.append('receipt', this.pay.receipt);
    this.saving = true;
    this.api.post<{ message: string }>('/sponsor/payments', f).subscribe({
      next: (r) => { this.saving = false; this.pay = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not record the payment.'); }
    });
  }
}
