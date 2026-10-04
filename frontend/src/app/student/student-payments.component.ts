import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate, uploadUrl } from '../shared/format';

type Row = Record<string, any>;

interface Installment { n: number; due: string; status: string; badge: string; payment: Row | null; receipt: string | null; proof: string | null }

/** Port of templates/student/payment.html (schedule, bank-details popup, spent-proof upload). */
@Component({
  selector: 'app-student-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">My Payment History</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Payment Schedule</h4>
            <p class="text-muted">Full schedule for your course. Please upload utilization proof for each installment received.</p>
            <div class="text-right mb-3">
              <button type="button" class="btn btn-primary waves-effect waves-light" (click)="openBank()"><i class="mdi mdi-bank mr-1"></i> My Bank Details</button>
            </div>
            <div class="table-responsive">
              <table class="table table-centered table-hover mb-0">
                <thead class="thead-light"><tr><th>#</th><th>Due Date</th><th>Status</th><th>Amount</th><th>Bank Receipt</th><th>Your Spent Proof</th></tr></thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="6" class="text-center">Generating schedule...</td></tr>
                  <tr *ngIf="!loading && !hasCourse"><td colspan="6" class="text-center text-warning">Course not assigned yet.</td></tr>
                  <tr *ngFor="let i of schedule">
                    <td>{{ i.n }}</td>
                    <td>{{ i.due }}</td>
                    <td><span class="badge" [ngClass]="i.badge">{{ i.status }}</span></td>
                    <td>{{ i.payment ? '₹' + (i.payment['amount'] | number: '1.2-2') : '—' }}</td>
                    <td><a *ngIf="i.receipt; else dash" [href]="i.receipt" target="_blank">View</a></td>
                    <td>
                      <ng-container *ngIf="i.payment; else dash">
                        <a *ngIf="i.proof" [href]="i.proof" target="_blank" class="text-success font-weight-bold">View Proof</a>
                        <button *ngIf="!i.proof" class="btn btn-xs btn-outline-primary" (click)="openProof(i.payment['payment_id'])">Upload Proof</button>
                      </ng-container>
                    </td>
                  </tr>
                </tbody>
              </table>
              <ng-template #dash>—</ng-template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Bank details (the Flask popup had no save button; this one saves) -->
    <div *ngIf="bank" class="modal fade show d-block" tabindex="-1" (click)="bank = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">My Bank Details</h5><button type="button" class="close" (click)="bank = null">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label>Bank Name</label><input type="text" class="form-control" [(ngModel)]="bank.bankName" /></div>
            <div class="form-group"><label>Account Number</label><input type="text" class="form-control" [(ngModel)]="bank.accountNumber" /></div>
            <div class="form-group"><label>Account Holder Name</label><input type="text" class="form-control" [(ngModel)]="bank.accountName" /></div>
            <div class="form-group"><label>IFSC Code</label><input type="text" class="form-control" [(ngModel)]="bank.ifscCode" /></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="bank = null">Close</button>
            <button type="button" class="btn btn-primary" (click)="saveBank()" [disabled]="saving">Save</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="proofFor !== null" class="modal fade show d-block" tabindex="-1" (click)="proofFor = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Upload Fee Receipt</h5><button type="button" class="close" (click)="proofFor = null">&times;</button></div>
          <div class="modal-body">
            <div class="form-group">
              <label>Select Document (PDF/Image)</label>
              <input type="file" class="form-control-file" accept="image/*,application/pdf" (change)="proof = fileOf($event)">
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="proofFor = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="submitProof()" [disabled]="!proof || saving">Upload</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class StudentPaymentsComponent implements OnInit {
  schedule: Installment[] = [];
  hasCourse = false;
  loading = true;
  bankData: Row = {};
  bank: { bankName: string; accountNumber: string; accountName: string; ifscCode: string } | null = null;
  proofFor: number | null = null;
  proof: File | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<{ payments: Row[]; bankDetails: Row | null; courseInfo: Row | null }>('/student/payments').subscribe({
      next: (r) => { this.loading = false; this.bankData = r.bankDetails ?? {}; this.build(r.courseInfo, r.payments); },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load your payments.'); }
    });
  }

  /** Same rules as the Flask page script. */
  private build(course: Row | null, payments: Row[]): void {
    this.schedule = [];
    const base = asDate(course?.['assigned_at']);
    this.hasCourse = !!base;
    if (!course || !base) return;
    const total = Math.floor((Number(course['number_of_semesters']) / 2) * 4);
    const today = new Date(); today.setHours(0, 0, 0, 0);
    for (let i = 1; i <= total; i++) {
      const due = new Date(base);
      due.setMonth(due.getMonth() + 3 * i);
      const p = payments[i - 1] ?? null;
      const overdue = !p && due < today;
      this.schedule.push({
        n: i, due: due.toLocaleDateString(), payment: p,
        status: p ? 'Paid' : overdue ? 'Overdue' : 'Due',
        badge: p ? 'badge-success' : overdue ? 'badge-danger' : 'badge-warning',
        receipt: p ? uploadUrl(p['receipt_url']) : null,
        proof: p ? uploadUrl(p['student_proof_url']) : null
      });
    }
  }

  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }

  openBank(): void {
    const b = this.bankData;
    this.bank = { bankName: b['bank_name'] ?? '', accountNumber: b['account_number'] ?? '', accountName: b['account_name'] ?? '', ifscCode: b['ifsc_code'] ?? '' };
  }

  saveBank(): void {
    if (!this.bank) return;
    const body = {
      bankName: this.bank.bankName.trim(), accountNumber: this.bank.accountNumber.trim(),
      accountName: this.bank.accountName.trim(), ifscCode: this.bank.ifscCode.trim()
    };
    this.saving = true;
    this.api.post<{ message: string }>('/student/bank-details', body).subscribe({
      next: (r) => { this.saving = false; this.bank = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save your bank details.'); }
    });
  }

  openProof(paymentId: number): void { this.proofFor = paymentId; this.proof = null; }

  submitProof(): void {
    if (this.proofFor === null || !this.proof) return;
    const f = new FormData();
    f.append('proofFile', this.proof);
    this.saving = true;
    this.api.post<{ message: string }>(`/student/payments/${this.proofFor}/proof`, f).subscribe({
      next: (r) => { this.saving = false; this.proofFor = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not upload the proof.'); }
    });
  }
}
