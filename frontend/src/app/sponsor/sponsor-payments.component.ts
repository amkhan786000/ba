import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { isoDate, uploadUrl } from '../shared/format';
import { InstallmentRow, installmentBadge } from '../shared/installments';
import { CardTableDirective } from '../shared/card-table.directive';
import { CheckoutOrder, payWithRazorpay } from '../shared/razorpay';
import { AppDatePipe } from '../shared/app-date';
import { DateInputComponent } from '../shared/date-input.component';

type Row = Record<string, any>;

interface Detail {
  grantee: Row;
  bankDetails: Row | null;
  payments: Row[];
  courseInfo: Row | null;
  annualScheduleAmount: number | string | null;
  /** Installments between this sponsor and the student (created when the student was mapped to the sponsor). */
  installments: InstallmentRow[];
  /** Why there are no (more) installments, e.g. no payment start date yet. */
  installmentProblem: string | null;
  /** A student who moved to another sponsor: only the installments this sponsor paid, read-only. */
  former: boolean;
}

/** One installment with its student (all students on one list). */
interface Item { d: Detail; i: InstallmentRow; sid: string }

const STATUS_ORDER: Record<string, number> = { Overdue: 0, Due: 1, 'Not Due': 2, Paid: 3 };

/**
 * Sponsor > Payments: every installment of every sponsored student on one list (what needs paying first), with
 * cards for what is due now, the next payment and what was paid this year, and filters by student, status and
 * calendar year (a sponsor may get students in different years). Recording a payment works as before.
 */
@Component({
  selector: 'app-sponsor-payments',
  standalone: true,
  imports: [DateInputComponent, AppDatePipe, CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  styles: [`
    .table-schedule td, .table-schedule th { vertical-align: middle; }
    .card-box.clickable { cursor: pointer; }
    tr.needs-pay { background: var(--warning-50, #fff8e6); }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Sponsorship Payments</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row" *ngIf="loaded">
      <div class="col-12 col-md-4">
        <div class="card-box clickable" (click)="status = 'NOW'; studentId = ''; year = ''" title="Show what to pay now">
          <h4 class="header-title mt-0">Due now</h4>
          <h2 [class.text-danger]="dueNow.count">₹{{ dueNow.amount | number: '1.0-2' }}</h2>
          <small class="text-muted">{{ dueNow.count ? dueNow.count + ' installment' + (dueNow.count === 1 ? '' : 's') + (dueNow.overdue ? ', ' + dueNow.overdue + ' overdue' : '') : 'Nothing to pay right now' }}</small>
        </div>
      </div>
      <div class="col-12 col-md-4">
        <div class="card-box">
          <h4 class="header-title mt-0">Next payment</h4>
          <ng-container *ngIf="next as n; else noNext">
            <h2>₹{{ n.amount | number: '1.0-2' }}</h2>
            <small class="text-muted">{{ n.date | appDate }} · {{ n.names.length === 1 ? n.names[0] : n.names.length + ' students (' + n.names.join(', ') + ')' }}</small>
          </ng-container>
          <ng-template #noNext><h2>—</h2><small class="text-muted">No upcoming installments</small></ng-template>
        </div>
      </div>
      <div class="col-12 col-md-4">
        <div class="card-box">
          <h4 class="header-title mt-0">Paid in {{ thisYear }}</h4>
          <h2>₹{{ paidThisYear.amount | number: '1.0-2' }}</h2>
          <small class="text-muted">{{ paidThisYear.count }} installment{{ paidThisYear.count === 1 ? '' : 's' }}</small>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="row align-items-end">
          <div class="col-12 col-md-4 form-group">
            <label for="f-student">Student</label>
            <select id="f-student" class="form-control" [(ngModel)]="studentId">
              <option value="">All students</option>
              <option *ngFor="let d of details" [value]="d.grantee['id']">{{ d.grantee['name'] }} ({{ d.grantee['user_id'] }}){{ d.former ? ' (no longer mapped to you)' : '' }}</option>
            </select>
          </div>
          <div class="col-6 col-md-3 form-group">
            <label for="f-status">Status</label>
            <select id="f-status" class="form-control" [(ngModel)]="status">
              <option value="">All</option>
              <option value="NOW">To pay now (overdue or due)</option>
              <option value="Overdue">Overdue</option>
              <option value="Due">Due</option>
              <option value="Not Due">Upcoming</option>
              <option value="Paid">Paid</option>
            </select>
          </div>
          <div class="col-6 col-md-2 form-group">
            <label for="f-year">Year</label>
            <select id="f-year" class="form-control" [(ngModel)]="year">
              <option value="">All years</option>
              <option *ngFor="let y of years" [value]="y">{{ y }}</option>
            </select>
          </div>
          <div class="col-12 col-md-3 form-group text-md-right">
            <button type="button" class="btn btn-outline-secondary btn-sm mb-1" (click)="statement()" [disabled]="downloading"
                    [title]="'Your payments in ' + statementYear + ' as a PDF'">
              <i class="mdi mdi-file-pdf-box mr-1"></i>{{ downloading ? 'Preparing…' : 'Statement ' + statementYear }}</button>
            <button type="button" class="btn btn-outline-secondary btn-sm mb-1 ml-1" (click)="history()" [disabled]="downloading"
                    [title]="year ? 'Your payments in ' + year + ' as Excel' : 'All your payments as Excel'">
              <i class="mdi mdi-file-excel-box mr-1"></i>Excel</button>
            <button type="button" class="btn btn-outline-secondary btn-sm mb-1 ml-1" (click)="calendar()" [disabled]="downloading"
                    title="Your upcoming due dates as calendar reminders (3 days before each)">
              <i class="mdi mdi-calendar-plus mr-1"></i>Add to calendar</button>
          </div>
        </div>

        <div *ngIf="selected as s" class="border rounded p-2 mb-3 small">
          <strong class="text-success">{{ s.grantee['name'] }}</strong> · Student ID {{ s.grantee['user_id'] }} · Session year {{ s.grantee['year'] || 'N/A' }}
          <div *ngIf="s.former" class="text-info">No longer mapped to you: these are the installments you paid for them.</div>
          <div *ngIf="!s.former && s.installmentProblem" class="text-warning">
            {{ s.installments.length ? 'No further installments: ' : 'The payment schedule is not ready yet: ' }}{{ s.installmentProblem }} Please contact the office.</div>
        </div>
        <div *ngIf="!studentId && problems.length" class="alert alert-warning small">
          <div *ngFor="let d of problems">{{ d.grantee['name'] }}: {{ d.installments.length ? 'no further installments' : 'payment schedule not ready yet' }} ({{ d.installmentProblem }}). Please contact the office.</div>
        </div>

        <div *ngIf="online?.pays_through_rahbar" class="alert mb-3" [ngClass]="online?.available ? 'alert-success' : 'alert-info'">
          <ng-container *ngIf="online?.available; else notOpen">
            <i class="mdi mdi-shield-check-outline mr-1"></i>You pay through Rahbar: tick the installments and choose <strong>Pay online</strong>
            (UPI, Indian cards or net banking). You get an 80G receipt by email, and the office sends the money on to your student.
            <span *ngIf="online?.test_mode" class="badge badge-warning ml-1">Test mode: no real money</span>
            <div *ngIf="chosen.size" class="mt-2"><button type="button" class="btn btn-success btn-sm" (click)="openOnline()">
              <i class="mdi mdi-cash-fast mr-1"></i>Pay online ₹{{ chosenTotal | number: '1.0-2' }} for {{ chosen.size }} installment{{ chosen.size === 1 ? '' : 's' }}</button>
              <button type="button" class="btn btn-link btn-sm" (click)="chosen.clear()">Clear</button></div>
          </ng-container>
          <ng-template #notOpen>You pay through Rahbar. Online payment opens soon; meanwhile please contact the office to pay.</ng-template>
        </div>

        <div class="table-responsive">
          <table class="table table-sm table-schedule mb-0">
            <thead><tr><th *ngIf="canPayOnline" style="width: 30px"></th><th>Student</th><th>#</th><th>Due date</th><th>Amount</th><th>Status</th><th>Paid on</th><th>Receipts</th><th></th></tr></thead>
            <tbody>
              <tr *ngIf="loaded && !rows.length"><td [attr.colspan]="canPayOnline ? 9 : 8" class="text-center text-muted">{{ items.length ? 'Nothing matches these filters.' : 'No installments yet.' }}</td></tr>
              <tr *ngFor="let r of rows" [class.needs-pay]="!r.i.payment_id && (r.i.status === 'Overdue' || r.i.status === 'Due') && !r.d.former">
                <td *ngIf="canPayOnline"><input *ngIf="payableOnline(r)" type="checkbox" [checked]="chosen.has(r.i.installment_id)"
                  (change)="toggleChosen(r)" [attr.aria-label]="'Choose installment ' + r.i.installment_no"></td>
                <td><strong>{{ r.d.grantee['name'] }}</strong><div class="small text-muted">{{ r.d.grantee['user_id'] }}<span *ngIf="r.d.former"> · no longer yours</span></div></td>
                <td>{{ r.i.installment_no }}</td>
                <td>{{ r.i.due_date | appDate }}</td>
                <td>₹{{ r.i.amount | number: '1.2-2' }}</td>
                <td><span class="badge" [ngClass]="badge(r.i.status)">{{ r.i.status === 'Not Due' ? 'Upcoming' : r.i.status }}</span></td>
                <td>{{ r.i.paid_date ? (r.i.paid_date | appDate) : '—' }}<div *ngIf="r.i.paid_amount !== null" class="small text-muted">₹{{ r.i.paid_amount | number: '1.2-2' }}</div></td>
                <td>
                  <span *ngIf="!r.i.payment_id">—</span>
                  <div *ngIf="r.i.payment_id" class="d-flex flex-column">
                    <a href="" class="small" (click)="$event.preventDefault(); receipt(r.i.payment_id!)">Rahbar receipt (PDF)</a>
                    <a *ngIf="link(r.i.receipt_url) as u" [href]="u" target="_blank" rel="noopener" class="small text-info">Bank receipt</a>
                    <span *ngIf="!r.i.receipt_url" class="small text-muted">No receipt</span>
                    <a *ngIf="link(r.i.student_proof_url) as u" [href]="u" target="_blank" rel="noopener" class="small text-success font-weight-bold">Spent proof</a>
                    <span *ngIf="!r.i.student_proof_url" class="small text-muted font-italic">Spent proof pending</span>
                  </div>
                </td>
                <td class="text-right">
                  <button *ngIf="!r.i.payment_id && !r.d.former && !online?.pays_through_rahbar" type="button" class="btn btn-xs btn-success" (click)="openPay(r)">Pay now</button>
                  <button *ngIf="payableOnline(r)" type="button" class="btn btn-xs btn-success" (click)="chosen.clear(); toggleChosen(r); openOnline()">Pay online</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div *ngIf="onlineForm" class="modal fade show d-block" tabindex="-1" (click)="onlineForm = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-success text-white">
            <h5 class="modal-title text-white">Pay online ₹{{ chosenTotal | number: '1.0-2' }}</h5>
            <button type="button" class="close text-white" (click)="onlineForm = null" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <ul class="small mb-3"><li *ngFor="let x of chosenItems">{{ x.d.grantee['name'] }}: installment #{{ x.i.installment_no }}, ₹{{ x.i.amount | number: '1.0-2' }} (due {{ x.i.due_date | appDate }})</li></ul>
            <div class="form-group">
              <label for="o-pan">Your PAN</label>
              <input id="o-pan" class="form-control text-uppercase" maxlength="10" [(ngModel)]="onlineForm.pan" placeholder="ABCDE1234F">
              <small class="text-muted">Printed on your 80G receipt and reported to the Income Tax Department (Form 10BD).</small>
            </div>
            <div class="custom-control custom-checkbox">
              <input type="checkbox" class="custom-control-input" id="o-decl" [(ngModel)]="onlineForm.declaration">
              <label class="custom-control-label small" for="o-decl">I am an Indian citizen and I am paying from my own Indian bank account
                (savings, NRE or NRO). Bihar Anjuman cannot accept money from foreign citizens or foreign accounts.</label>
            </div>
            <p *ngIf="online?.test_mode" class="small text-warning mt-2 mb-0">Test mode: use Razorpay's test card or UPI; no real money moves.</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-success btn-block" (click)="payOnline()" [disabled]="paying || !onlineForm.declaration || onlineForm.pan.trim().length !== 10">
              {{ paying ? 'Waiting for the payment…' : 'Continue to payment' }}</button>
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
              <p>Payment for: <strong class="text-primary">{{ pay.d.grantee['name'] }}</strong></p>
              <div class="alert alert-light border small">
                <strong>Target Bank Details:</strong>
                <p class="mb-0">Beneficiary: {{ pay.d.bankDetails?.['account_name'] || 'N/A' }}</p>
                <p class="mb-0">Bank: {{ pay.d.bankDetails?.['bank_name'] || 'N/A' }}</p>
                <p class="mb-0">Account No: {{ pay.d.bankDetails?.['account_number'] || 'N/A' }}</p>
                <p class="mb-0">IFSC: {{ pay.d.bankDetails?.['ifsc_code'] || 'N/A' }}</p>
              </div>
              <div class="form-group"><label>Actual Date of Payment</label><app-date-input name="paymentDate" [(ngModel)]="pay.paymentDate" [required]="true"></app-date-input></div>
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
  readonly thisYear = new Date().getFullYear();
  /** Paying through Rahbar online (Razorpay): who may, their PAN and past online payments. */
  online: { pays_through_rahbar: boolean; available: boolean; test_mode: boolean; pan: string | null } | null = null;
  /** Installments ticked for one online payment (installment ids). */
  chosen = new Set<number>();
  onlineForm: { pan: string; declaration: boolean } | null = null;
  paying = false;
  details: Detail[] = [];
  items: Item[] = [];
  loaded = false;
  studentId = '';
  status = '';
  year = '';
  pay: { d: Detail; sid: string; installment: number; installmentId: number; amount: number | null; paymentDate: string; receipt: File | null } | null = null;
  saving = false;
  downloading = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    // "Pay Now" on the dashboard links here with ?granteeId=<users.id>
    this.studentId = this.route.snapshot.queryParamMap.get('granteeId') ?? '';
    this.load();
    this.api.get<NonNullable<typeof this.online>>('/sponsor/online-payments').subscribe({ next: (o) => (this.online = o), error: () => (this.online = null) });
  }

  /** The tick column shows for sponsors who pay through Rahbar once online payment is open. */
  get canPayOnline(): boolean { return !!this.online?.pays_through_rahbar && !!this.online?.available; }

  payableOnline(x: Item): boolean { return this.canPayOnline && !x.i.payment_id && !x.d.former; }

  toggleChosen(x: Item): void {
    if (this.chosen.has(x.i.installment_id)) this.chosen.delete(x.i.installment_id); else this.chosen.add(x.i.installment_id);
  }

  get chosenItems(): Item[] { return this.items.filter((x) => this.chosen.has(x.i.installment_id)); }

  get chosenTotal(): number { return this.chosenItems.reduce((s, x) => s + Number(x.i.amount), 0); }

  openOnline(): void {
    if (!this.chosen.size) return;
    this.onlineForm = { pan: this.online?.pan ?? '', declaration: false };
  }

  /** Rahbar opens the order, Razorpay takes the payment, Rahbar checks the signature and records it. */
  payOnline(): void {
    const f = this.onlineForm;
    if (!f) return;
    this.paying = true;
    this.api.post<CheckoutOrder>('/sponsor/online-payments/order', { installmentIds: [...this.chosen], pan: f.pan.trim().toUpperCase(), declaration: f.declaration })
      .subscribe({
        next: (order) => {
          this.onlineForm = null;
          payWithRazorpay(order).then((result) => {
            this.api.post<{ message: string }>('/sponsor/online-payments/confirm', result).subscribe({
              next: (r) => { this.paying = false; this.chosen.clear(); this.message = r.message; this.load(); },
              error: (e) => { this.paying = false; this.error = errorText(e, 'We could not confirm the payment yet. If money left your account, it will show here shortly.'); this.load(); }
            });
          }).catch((e: Error) => { this.paying = false; if (e.message !== 'Payment cancelled.') this.error = e.message; });
        },
        error: (e) => { this.paying = false; this.error = errorText(e, 'Could not start the payment.'); }
      });
  }

  load(): void {
    this.api.get<{ paymentDetails: Detail[] }>('/sponsor/payments').subscribe({
      next: (r) => {
        this.details = r.paymentDetails;
        this.items = this.details.flatMap((d) => d.installments.map((i) => ({ d, i, sid: String(d.grantee['id']) })));
        this.loaded = true;
      },
      error: (e) => (this.error = errorText(e, 'Could not load payments.'))
    });
  }

  /** Calendar years with installments (by due date), newest first. */
  get years(): number[] {
    return [...new Set(this.items.map((x) => new Date(x.i.due_date).getFullYear()))].sort((a, b) => b - a);
  }

  /** The filtered list: what needs paying first (overdue, due, upcoming by date), then paid ones, newest first. */
  get rows(): Item[] {
    return this.items
      .filter((x) => !this.studentId || x.sid === this.studentId)
      .filter((x) => !this.year || new Date(x.i.due_date).getFullYear() === Number(this.year))
      .filter((x) => !this.status || (this.status === 'NOW' ? this.toPay(x) : x.i.status === this.status))
      .sort((a, b) => (STATUS_ORDER[a.i.status] ?? 9) - (STATUS_ORDER[b.i.status] ?? 9)
        || (a.i.status === 'Paid' ? b.i.due_date.localeCompare(a.i.due_date) : a.i.due_date.localeCompare(b.i.due_date)));
  }

  get selected(): Detail | null { return this.details.find((d) => String(d.grantee['id']) === this.studentId) ?? null; }

  /** Students whose schedule is missing or ended early (shown when all students are listed). */
  get problems(): Detail[] { return this.details.filter((d) => !d.former && d.installmentProblem); }

  get dueNow(): { count: number; amount: number; overdue: number } {
    const now = this.items.filter((x) => this.toPay(x));
    return { count: now.length, amount: now.reduce((s, x) => s + Number(x.i.amount), 0), overdue: now.filter((x) => x.i.status === 'Overdue').length };
  }

  /** Everything due on the next due date (all students together): the date, total amount and whose installments. */
  get next(): { date: string; amount: number; names: string[] } | null {
    const upcoming = this.items.filter((x) => !x.i.payment_id && !x.d.former && x.i.status === 'Not Due');
    if (!upcoming.length) return null;
    const date = upcoming.map((x) => x.i.due_date).sort()[0];
    const same = upcoming.filter((x) => x.i.due_date === date);
    return {
      date,
      amount: same.reduce((sum, x) => sum + Number(x.i.amount), 0),
      names: [...new Set(same.map((x) => String(x.d.grantee['name'])))]
    };
  }

  get paidThisYear(): { count: number; amount: number } {
    const paid = this.items.filter((x) => x.i.payment_id && x.i.paid_date && new Date(x.i.paid_date).getFullYear() === this.thisYear);
    return { count: paid.length, amount: paid.reduce((s, x) => s + Number(x.i.paid_amount ?? 0), 0) };
  }

  /** The chosen year, or last year (a full year) when all years are shown. */
  get statementYear(): number { return this.year ? Number(this.year) : this.thisYear - 1; }

  private toPay(x: Item): boolean {
    return !x.i.payment_id && !x.d.former && (x.i.status === 'Overdue' || x.i.status === 'Due');
  }

  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }
  badge(status: string): string { return installmentBadge(status); }
  link(path: string | null): string | null { return uploadUrl(path); }

  statement(): void {
    const y = this.statementYear;
    this.downloading = true;
    this.api.download('/sponsor/statement', { year: y }, `Rahbar_statement_${y}.pdf`).subscribe({
      next: () => (this.downloading = false),
      error: (e) => { this.downloading = false; this.error = errorText(e, 'Could not create the statement.'); }
    });
  }

  /** Rahbar's receipt for one payment (PDF). */
  receipt(paymentId: number): void {
    this.api.download(`/sponsor/payments/${paymentId}/receipt`, undefined, `Rahbar_receipt_${paymentId}.pdf`).subscribe({
      error: (e) => (this.error = errorText(e, 'Could not create the receipt.'))
    });
  }

  /** Payment history as Excel: the chosen year, or everything. */
  history(): void {
    this.downloading = true;
    this.api.download('/sponsor/payments/history', { year: this.year || null }, `Rahbar_payments_${this.year || 'all'}.xlsx`).subscribe({
      next: () => (this.downloading = false),
      error: (e) => { this.downloading = false; this.error = errorText(e, 'Could not create the Excel file.'); }
    });
  }

  /** Upcoming due dates as an .ics file: opening it adds them to the sponsor's calendar app. */
  calendar(): void {
    this.downloading = true;
    this.api.download('/sponsor/payments/calendar', undefined, 'Rahbar_payments.ics').subscribe({
      next: () => { this.downloading = false; this.message = 'Open the downloaded file to add your due dates to your calendar.'; },
      error: (e) => { this.downloading = false; this.error = errorText(e, 'Could not create the calendar file.'); }
    });
  }

  openPay(x: Item): void {
    this.pay = { d: x.d, sid: x.sid, installment: x.i.installment_no, installmentId: x.i.installment_id, amount: Number(x.i.amount),
      paymentDate: isoDate(new Date()), receipt: null };
  }

  submitPay(): void {
    if (!this.pay?.receipt) return;
    const f = new FormData();
    f.append('granteeId', this.pay.sid);
    f.append('installmentId', String(this.pay.installmentId));
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
