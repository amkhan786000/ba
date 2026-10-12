import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { isoDate } from '../shared/format';

interface Item { id: number; student_name: string; student_ref_id: string; amount: number; passed_on_at: string | null; passed_on_ref: string | null }
interface Row {
  id: number; purpose: 'SPONSOR' | 'DONATION'; status: 'CREATED' | 'PAID' | 'FAILED'; amount: number; name: string; email: string | null;
  phone: string | null; pan: string | null; note: string | null; receipt_no: string | null; gateway_payment_id: string | null;
  paid_at: string | null; created_at: string | null; items: Item[];
}
interface Settings {
  enabled: boolean; keys_configured: boolean; webhook_configured: boolean; test_mode: boolean; receipt_ready: boolean;
  receipt: Record<string, string>; receipt_fields: Record<string, string>; webhook_path: string;
}
interface Person { id: number; user_id: string; name: string }

/**
 * Sponsors & Payments > Online Payments (Razorpay): sponsors' online payments and website donations with their 80G
 * receipts; money to pass on to students; which sponsors pay through Rahbar; settings and receipt details.
 */
@Component({
  selector: 'app-admin-online-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Online Payments</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <div *ngIf="settings && !(settings.enabled && settings.keys_configured)" class="alert alert-info small">
      Online payments are <strong>off</strong>{{ !settings.keys_configured ? ' (the Razorpay keys are not set on the server yet)' : '' }}. See the Settings tab.
    </div>
    <div *ngIf="settings?.test_mode && settings?.keys_configured" class="alert alert-warning small">Razorpay <strong>test mode</strong>: payments are test payments, no real money.</div>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item" *ngFor="let t of tabs"><a class="nav-link" href="" [class.active]="tab === t.key" (click)="$event.preventDefault(); go(t.key)">{{ t.label }}</a></li>
    </ul>

    <!-- payments and donations -->
    <div class="card" *ngIf="tab === 'payments' || tab === 'pass-on'"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-center mb-2">
        <select *ngIf="tab === 'payments'" class="form-control form-control-sm mb-2" style="width: auto" [(ngModel)]="purpose" (ngModelChange)="load()" aria-label="Kind">
          <option value="">Sponsors and donations</option><option value="SPONSOR">Sponsors' payments</option><option value="DONATION">Website donations</option>
        </select>
        <p *ngIf="tab === 'pass-on'" class="text-muted small mb-2">Money sponsors paid online through Rahbar, still to be sent to each student's bank account.
          After the transfer, enter the date and bank reference: the student is told.</p>
        <strong class="mb-2">To pass on: ₹{{ toPassOn | number: '1.0-2' }}</strong>
      </div>
      <p *ngIf="!shown.length" class="text-muted mb-0">Nothing here.</p>
      <div *ngFor="let r of shown" class="border rounded p-3 mb-2">
        <div class="d-flex flex-wrap justify-content-between">
          <div>
            <strong>{{ r.name }}</strong> <span class="badge" [ngClass]="r.purpose === 'SPONSOR' ? 'badge-soft-info' : 'badge-soft-success'">{{ r.purpose === 'SPONSOR' ? 'Sponsor' : 'Donation' }}</span>
            <span class="badge ml-1" [ngClass]="r.status === 'PAID' ? 'badge-success' : r.status === 'FAILED' ? 'badge-danger' : 'badge-light border'">{{ r.status === 'PAID' ? 'Paid' : r.status === 'FAILED' ? 'Failed' : 'Not completed' }}</span>
            <div class="small text-muted">{{ r.email }}{{ r.phone ? ' · ' + r.phone : '' }}{{ r.pan ? ' · PAN ' + r.pan : '' }}</div>
            <div *ngIf="r.note" class="small">For: {{ r.note }}</div>
          </div>
          <div class="text-right">
            <h5 class="mb-0">₹{{ r.amount | number: '1.0-2' }}</h5>
            <div class="small text-muted">{{ (r.paid_at || r.created_at) | date: 'd MMM yyyy, h:mm a' }}</div>
            <a *ngIf="r.status === 'PAID'" href="" class="small" (click)="$event.preventDefault(); downloadReceipt(r)">{{ r.receipt_no }} (PDF)</a>
          </div>
        </div>
        <table *ngIf="r.items.length" class="table table-sm mb-0 mt-2">
          <thead><tr><th>Student</th><th class="text-right">Amount</th><th>Passed on to the student</th></tr></thead>
          <tbody><tr *ngFor="let i of r.items">
            <td>{{ i.student_name }} <small class="text-muted">{{ i.student_ref_id }}</small></td>
            <td class="text-right">₹{{ i.amount | number: '1.0-2' }}</td>
            <td>
              <span *ngIf="i.passed_on_at">{{ i.passed_on_at | date: 'd MMM yyyy' }} · {{ i.passed_on_ref }}</span>
              <span *ngIf="!i.passed_on_at && r.status !== 'PAID'" class="text-muted">—</span>
              <div *ngIf="!i.passed_on_at && r.status === 'PAID' && canEdit" class="d-flex flex-wrap">
                <input type="date" class="form-control form-control-sm mr-1 mb-1" style="width: 150px" [(ngModel)]="passOn[i.id].date" aria-label="Date sent">
                <input class="form-control form-control-sm mr-1 mb-1" style="width: 180px" placeholder="Bank reference (UTR)" [(ngModel)]="passOn[i.id].reference">
                <button class="btn btn-sm btn-outline-primary mb-1" (click)="savePassOn(i)" [disabled]="busy || !passOn[i.id].reference.trim()">Sent</button>
              </div>
              <span *ngIf="!i.passed_on_at && r.status === 'PAID' && !canEdit" class="text-warning">Not yet</span>
            </td>
          </tr></tbody>
        </table>
      </div>
    </div></div>

    <!-- sponsors -->
    <div class="card" *ngIf="tab === 'sponsors'"><div class="card-body">
      <p class="text-muted small">Sponsors listed here pay their students' installments to Rahbar online (Sponsor portal > Payments > Pay online) and get an 80G
        receipt; the office passes the money on. Everyone else keeps paying the student directly and uploading the bank receipt.</p>
      <div class="d-flex flex-wrap mb-3" *ngIf="canEdit">
        <select class="form-control mr-2 mb-2" style="max-width: 360px" [(ngModel)]="addSponsor" aria-label="Sponsor">
          <option [ngValue]="null">Choose a sponsor to add…</option>
          <option *ngFor="let s of others" [ngValue]="s.id">{{ s.name }} ({{ s.user_id }})</option>
        </select>
        <button class="btn btn-primary mb-2" (click)="setThrough(addSponsor!, true)" [disabled]="!addSponsor || busy">Pays through Rahbar</button>
      </div>
      <p *ngIf="!through.length" class="text-muted mb-0">No sponsor pays through Rahbar yet.</p>
      <div *ngFor="let s of through" class="d-flex justify-content-between align-items-center border-bottom py-2">
        <span>{{ s.name }} <small class="text-muted">{{ s.user_id }}</small></span>
        <button *ngIf="canEdit" class="btn btn-xs btn-outline-secondary" (click)="setThrough(s.id, false)" [disabled]="busy">Pays directly instead</button>
      </div>
    </div></div>

    <!-- settings -->
    <div class="card" *ngIf="tab === 'settings' && settings"><div class="card-body">
      <h5 class="mt-0">Razorpay</h5>
      <ul class="small">
        <li>Keys on the server: <strong [class.text-success]="settings.keys_configured" [class.text-danger]="!settings.keys_configured">{{ settings.keys_configured ? 'set' : 'not set' }}</strong>
          {{ settings.keys_configured ? (settings.test_mode ? '(test keys)' : '(live keys)') : '(RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in the server .env)' }}</li>
        <li>Webhook secret: <strong [class.text-success]="settings.webhook_configured" [class.text-danger]="!settings.webhook_configured">{{ settings.webhook_configured ? 'set' : 'not set' }}</strong>
          (RAZORPAY_WEBHOOK_SECRET). In Razorpay > Webhooks add <code>{{ webhookUrl }}</code> with the events <code>payment.captured</code> and <code>order.paid</code>.</li>
        <li>In Razorpay keep <strong>international payments switched off</strong> (no FCRA registration).</li>
      </ul>
      <h5>80G receipt details</h5>
      <div class="form-row">
        <div class="form-group col-md-6" *ngFor="let k of receiptKeys">
          <label [for]="'r-' + k">{{ settings.receipt_fields[k] }}</label>
          <textarea *ngIf="k === 'receipt.address'" [id]="'r-' + k" class="form-control" rows="2" [(ngModel)]="receipt[k]" [disabled]="!canEdit"></textarea>
          <input *ngIf="k !== 'receipt.address'" [id]="'r-' + k" class="form-control" [(ngModel)]="receipt[k]" [disabled]="!canEdit">
        </div>
      </div>
      <div class="custom-control custom-switch mb-3">
        <input type="checkbox" class="custom-control-input" id="r-on" [(ngModel)]="enabled" [disabled]="!canEdit">
        <label class="custom-control-label" for="r-on">Online payments switched on (sponsors who pay through Rahbar, and website donations)</label>
      </div>
      <button *ngIf="canEdit" class="btn btn-primary" (click)="saveSettings()" [disabled]="busy">Save</button>
    </div></div>
  `
})
export class AdminOnlinePaymentsComponent implements OnInit {
  readonly tabs = [
    { key: 'payments', label: 'Payments & donations' }, { key: 'pass-on', label: 'To pass on to students' },
    { key: 'sponsors', label: 'Sponsors paying through Rahbar' }, { key: 'settings', label: 'Settings' }
  ];
  tab = 'payments';
  purpose = '';
  rows: Row[] = [];
  toPassOn = 0;
  passOn: Record<number, { date: string; reference: string }> = {};
  through: Person[] = [];
  others: Person[] = [];
  addSponsor: number | null = null;
  settings: Settings | null = null;
  receipt: Record<string, string> = {};
  enabled = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('ONLINE_PAYMENTS', 'EDIT'); }
  get receiptKeys(): string[] { return Object.keys(this.settings?.receipt_fields ?? {}); }
  get webhookUrl(): string { return location.origin + (this.settings?.webhook_path ?? ''); }

  /** The pass-on tab: paid sponsor payments with something still to pass on. */
  get shown(): Row[] {
    if (this.tab !== 'pass-on') return this.rows;
    return this.rows.filter((r) => r.status === 'PAID' && r.items.some((i) => !i.passed_on_at));
  }

  ngOnInit(): void { this.loadSettings(); this.load(); }

  go(tab: string): void {
    this.tab = tab;
    if (tab === 'sponsors') this.loadSponsors();
    if (tab === 'pass-on' && this.purpose) { this.purpose = ''; }
    if (tab === 'payments' || tab === 'pass-on') this.load();
  }

  load(): void {
    this.api.get<{ rows: Row[]; to_pass_on: number }>('/admin/online-payments', { purpose: this.tab === 'pass-on' ? 'SPONSOR' : this.purpose }).subscribe({
      next: (r) => {
        this.rows = r.rows;
        this.toPassOn = Number(r.to_pass_on);
        for (const row of r.rows) for (const i of row.items) this.passOn[i.id] ??= { date: isoDate(new Date()), reference: '' };
      },
      error: (e) => (this.error = errorText(e, 'Could not load the online payments.'))
    });
  }

  loadSettings(): void {
    this.api.get<Settings>('/admin/online-payments/settings').subscribe({
      next: (s) => { this.settings = s; this.receipt = { ...s.receipt }; this.enabled = s.enabled; },
      error: (e) => (this.error = errorText(e, 'Could not load the settings.'))
    });
  }

  loadSponsors(): void {
    this.api.get<{ through_rahbar: Person[]; others: Person[] }>('/admin/online-payments/sponsors').subscribe({
      next: (r) => { this.through = r.through_rahbar; this.others = r.others; },
      error: (e) => (this.error = errorText(e, 'Could not load the sponsors.'))
    });
  }

  downloadReceipt(r: Row): void {
    this.api.download(`/admin/online-payments/${r.id}/receipt`, undefined, `${(r.receipt_no ?? 'receipt').replace(/\//g, '-')}.pdf`).subscribe({
      error: (e) => (this.error = errorText(e, 'Could not create the receipt.'))
    });
  }

  savePassOn(i: Item): void {
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/online-payments/items/${i.id}/pass-on`, this.passOn[i.id]).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }

  setThrough(id: number, through: boolean): void {
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/online-payments/sponsors/${id}`, { through }).subscribe({
      next: (r) => { this.busy = false; this.addSponsor = null; this.message = r.message; this.loadSponsors(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }

  saveSettings(): void {
    this.busy = true;
    this.api.put<{ message: string }>('/admin/online-payments/settings', { ...this.receipt, enabled: this.enabled }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.loadSettings(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the settings.'); }
    });
  }
}
