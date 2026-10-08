import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { uploadUrl } from '../shared/format';
import { InstallmentRow, installmentBadge } from '../shared/installments';
import { CardTableDirective } from '../shared/card-table.directive';

type Row = Record<string, any>;


/** Student > Payments: installments from the sponsor (Paid / Due / Not Due), bank details and spent-proof upload. */
@Component({
  selector: 'app-student-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">My Payment History</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Payment Schedule</h4>
            <p class="text-muted">Your installments from your sponsor. Please upload utilization proof for each installment received.</p>
            <div class="text-right mb-3">
              <button type="button" class="btn btn-primary waves-effect waves-light" (click)="openBank()"><i class="mdi mdi-bank mr-1"></i> My Bank Details</button>
            </div>
            <div class="table-responsive">
              <table class="table table-centered table-hover mb-0">
                <thead class="thead-light"><tr><th>#</th><th>Due Date</th><th>Amount</th><th>Status</th><th>Received</th><th>Bank Receipt</th><th>Your Spent Proof</th></tr></thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="7" class="text-center">Loading...</td></tr>
                  <tr *ngIf="!loading && !installments.length"><td colspan="7" class="text-center text-warning">
                    Your payment schedule is not ready yet{{ problem ? ': ' + problem : '.' }}
                  </td></tr>
                  <tr *ngFor="let i of installments">
                    <td>{{ i.installment_no }}</td>
                    <td>{{ i.due_date | date: 'd MMM yyyy' }}</td>
                    <td>₹{{ i.amount | number: '1.2-2' }}</td>
                    <td><span class="badge" [ngClass]="badge(i.status)">{{ i.status }}</span></td>
                    <td>{{ i.paid_amount !== null ? '₹' + (i.paid_amount | number: '1.2-2') : '—' }}</td>
                    <td><a *ngIf="link(i.receipt_url) as r; else dash" [href]="r" target="_blank">View</a></td>
                    <td>
                      <ng-container *ngIf="i.payment_id; else dash">
                        <a *ngIf="link(i.student_proof_url) as p" [href]="p" target="_blank" class="text-success font-weight-bold">View Proof</a>
                        <button *ngIf="!i.student_proof_url" class="btn btn-xs btn-outline-primary" (click)="openProof(i.payment_id)">Upload Proof</button>
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
  installments: InstallmentRow[] = [];
  problem: string | null = null;
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
    this.api.get<{ bankDetails: Row | null; installments: InstallmentRow[]; installmentProblem: string | null }>('/student/payments').subscribe({
      next: (r) => { this.loading = false; this.bankData = r.bankDetails ?? {}; this.installments = r.installments; this.problem = r.installmentProblem; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load your payments.'); }
    });
  }

  badge(status: string): string { return installmentBadge(status); }
  link(path: string | null): string | null { return uploadUrl(path); }

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
