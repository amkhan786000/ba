import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { errorText } from '../../shared/alerts/alerts.component';
import { RebSession } from './reb.models';
import { RebSponsorshipRow } from './reb-sponsorships.component';
import { AppDatePipe } from '../../shared/app-date';

interface LedgerRow { type: 'BILL' | 'PAYMENT'; id: number; date: string; text: string; status: string; debit?: number; credit?: number; balance: number }
interface Concession {
  id: number; session_name: string; fee_head_name: string; kind: string; value: number; reason: string; note: string | null;
  from_period: string | null; to_period: string | null;
}

const REASONS: Record<string, string> = { ORPHAN: 'Orphan', SIBLING: 'Sibling', STAFF_CHILD: "Staff member's child", NEED: 'Family need', OTHER: 'Other' };

/** A girl's fees on her page: balance, ledger, sponsorship and concessions, with a link to collect a payment. */
@Component({
  selector: 'app-reb-student-fees',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="card"><div class="card-body">
      <div class="d-flex justify-content-between align-items-center mb-2">
        <h4 class="header-title mb-0">Fees</h4>
        <a *ngIf="canCollect" class="btn btn-sm btn-success" routerLink="/admin/reb/fees" [queryParams]="{ tab: 'collect', student: studentId }">Collect</a>
      </div>
      <p class="mb-2">Owes <strong [class.text-danger]="balance > 0">₹{{ balance | number: '1.0-2' }}</strong></p>
      <p class="small mb-2" *ngFor="let s of activeSponsorships">Sponsored by <strong>{{ s.sponsor_name }}</strong> ({{ s.sponsor_code }}), ₹{{ s.monthly_amount | number: '1.0-2' }} a month since {{ s.start_period }}</p>
      <details>
        <summary class="small">Ledger ({{ ledger.length }})</summary>
        <div class="table-responsive mt-2"><table class="table table-sm mb-0">
          <thead><tr><th>Date</th><th></th><th class="text-right">Bill</th><th class="text-right">Paid</th><th class="text-right">Owes</th></tr></thead>
          <tbody>
            <tr *ngIf="!ledger.length"><td colspan="5" class="text-muted">No bills yet.</td></tr>
            <tr *ngFor="let l of ledger" [class.text-muted]="l.status === 'CANCELLED'">
              <td class="text-nowrap">{{ l.date | appDate }}</td><td class="small">{{ l.text }}
                <a *ngIf="l.type === 'PAYMENT'" href="" (click)="$event.preventDefault(); receipt(l.id)">receipt</a></td>
              <td class="text-right">{{ l.debit ? '₹' + (l.debit | number: '1.0-2') : '' }}</td>
              <td class="text-right">{{ l.credit ? '₹' + (l.credit | number: '1.0-2') : '' }}</td>
              <td class="text-right">₹{{ l.balance | number: '1.0-2' }}</td>
            </tr>
          </tbody>
        </table></div>
      </details>

      <div class="d-flex justify-content-between align-items-center mt-3 mb-1">
        <h5 class="mb-0">Concessions</h5>
        <button *ngIf="canApprove" class="btn btn-xs btn-outline-primary" (click)="openConcession()">Add</button>
      </div>
      <p *ngIf="!concessions.length" class="small text-muted mb-0">None.</p>
      <ul class="list-unstyled small mb-0">
        <li *ngFor="let c of concessions" class="d-flex justify-content-between border-bottom py-1">
          <span>{{ c.kind === 'PERCENT' ? (c.value | number: '1.0-2') + '%' : '₹' + (c.value | number: '1.0-2') }} off {{ c.fee_head_name }} · {{ reasons[c.reason] }}
            <span class="text-muted">· {{ c.session_name }}{{ c.from_period || c.to_period ? ', ' + (c.from_period || 'start') + ' to ' + (c.to_period || 'end') : '' }}</span>
            <div class="text-muted" *ngIf="c.note">{{ c.note }}</div></span>
          <button *ngIf="canApprove" class="btn btn-xs btn-link text-danger" (click)="removeConcession(c)">Remove</button>
        </li>
      </ul>
      <p *ngIf="error" class="text-danger small mt-2 mb-0">{{ error }}</p>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Add a concession</h5><button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body"><div class="row">
            <div class="col-6 form-group"><label for="c-session">Session</label>
              <select id="c-session" class="form-control" [(ngModel)]="form.sessionId"><option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option></select></div>
            <div class="col-6 form-group"><label for="c-head">On</label>
              <select id="c-head" class="form-control" [(ngModel)]="form.feeHeadId"><option [ngValue]="null">All fees</option>
                <option *ngFor="let h of heads" [ngValue]="h.id">{{ h.name }}</option></select></div>
            <div class="col-6 form-group"><label for="c-kind">Kind</label>
              <select id="c-kind" class="form-control" [(ngModel)]="form.kind"><option value="PERCENT">Percentage</option><option value="AMOUNT">Amount per month (₹)</option></select></div>
            <div class="col-6 form-group"><label for="c-value">{{ form.kind === 'PERCENT' ? 'Percent' : 'Amount (₹)' }} *</label><input id="c-value" type="number" min="0" class="form-control" [(ngModel)]="form.value"></div>
            <div class="col-6 form-group"><label for="c-from">From month</label><input id="c-from" type="month" class="form-control" [(ngModel)]="form.fromPeriod"></div>
            <div class="col-6 form-group"><label for="c-to">To month</label><input id="c-to" type="month" class="form-control" [(ngModel)]="form.toPeriod"></div>
            <div class="col-12 form-group"><label for="c-reason">Reason</label>
              <select id="c-reason" class="form-control" [(ngModel)]="form.reason"><option *ngFor="let r of reasonKeys" [value]="r">{{ reasons[r] }}</option></select></div>
            <div class="col-12 form-group mb-0"><label for="c-note">Note</label><input id="c-note" class="form-control" [(ngModel)]="form.note"></div>
          </div><p class="small text-muted mt-2 mb-0">Applies to bills made from now on.</p></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveConcession()" [disabled]="!form.value">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebStudentFeesComponent implements OnChanges {
  @Input({ required: true }) studentId!: number;

  readonly reasons = REASONS;
  readonly reasonKeys = Object.keys(REASONS);
  ledger: LedgerRow[] = [];
  balance = 0;
  concessions: Concession[] = [];
  sponsorships: RebSponsorshipRow[] = [];
  sessions: RebSession[] = [];
  heads: { id: number; name: string }[] = [];
  form: { sessionId: number | null; feeHeadId: number | null; kind: string; value: number | null; reason: string; note: string; fromPeriod: string; toPeriod: string } | null = null;
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canCollect(): boolean { return this.auth.can('REB_FEES', 'EDIT'); }
  get canApprove(): boolean { return this.auth.can('REB_FEE_APPROVALS', 'EDIT'); }
  get activeSponsorships(): RebSponsorshipRow[] { return this.sponsorships.filter((s) => s.status === 'ACTIVE'); }

  ngOnChanges(): void { this.load(); }

  load(): void {
    this.api.get<{ rows: LedgerRow[]; balance: number }>(`/admin/reb/students/${this.studentId}/ledger`).subscribe({
      next: (r) => { this.ledger = r.rows; this.balance = Number(r.balance); }, error: (e) => (this.error = errorText(e, 'Could not load the fees.'))
    });
    this.api.get<Concession[]>(`/admin/reb/students/${this.studentId}/concessions`).subscribe({ next: (r) => (this.concessions = r) });
    this.api.get<RebSponsorshipRow[]>(`/admin/reb/students/${this.studentId}/sponsorships`).subscribe({ next: (r) => (this.sponsorships = r) });
  }

  receipt(id: number): void {
    this.api.download(`/admin/reb/payments/${id}/receipt`, {}, 'receipt.pdf').subscribe({ error: (e) => (this.error = errorText(e, 'Could not create the receipt.')) });
  }

  openConcession(): void {
    if (!this.sessions.length) this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({ next: (r) => { this.sessions = r; if (this.form) this.form.sessionId = (r.find((s) => s.is_current) ?? r[0])?.id ?? null; } });
    if (!this.heads.length) this.api.get<{ id: number; name: string; active: boolean }[]>('/admin/reb/fee-heads').subscribe({ next: (r) => (this.heads = r.filter((h) => h.active)) });
    this.form = { sessionId: (this.sessions.find((s) => s.is_current) ?? this.sessions[0])?.id ?? null, feeHeadId: null, kind: 'PERCENT', value: null, reason: 'NEED', note: '', fromPeriod: '', toPeriod: '' };
  }

  saveConcession(): void {
    if (!this.form) return;
    this.api.post<{ message: string }>('/admin/reb/concessions', { ...this.form, studentId: this.studentId }).subscribe({
      next: () => { this.form = null; this.error = ''; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not save the concession.'))
    });
  }

  removeConcession(c: Concession): void {
    if (!confirm('Remove this concession? Bills already made keep it.')) return;
    this.api.delete<{ message: string }>(`/admin/reb/concessions/${c.id}`).subscribe({ next: () => this.load(), error: (e) => (this.error = errorText(e, 'Could not remove it.')) });
  }
}
