import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { PagedResult } from '../../shared/pager/server-table';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { PagerComponent, PageState, PaginatePipe } from '../../shared/pager/pager.component';
import { isoDate } from '../../shared/format';
import { RebSectionRow, RebSession, RebStudentRow } from './reb.models';
import { AppDatePipe } from '../../shared/app-date';

type Tab = 'collect' | 'bills' | 'dues' | 'daybook' | 'setup';

interface DueBill { id: number; invoice_no: string; period: string; label: string; due_date: string; overdue: boolean; family_outstanding: number; sponsor_outstanding: number }
interface Due {
  student: { id: number; admission_no: string; name: string; fee_plan: string };
  bills: DueBill[]; family_total: number; sponsor_total: number;
  sponsorship: { id: number; sponsor_user_id: number; monthly_amount: number; sponsor_name: string; sponsor_code: string } | null;
}
interface Bill {
  id: number; invoice_no: string; student_id: number; student_name: string; admission_no: string; section: string | null; period: string;
  due_date: string; net: number; sponsor_share: number; family_share: number; outstanding: number; status: string; overdue: boolean;
}
interface PlanRow { student_id: number; name: string; admission_no: string; section: string; skipped?: string; net?: number; sponsor_share?: number; family_share?: number; concession?: number }
interface Plan { period: string; label: string; bills: number; total: number; sponsor_total: number; family_total: number; rows: PlanRow[]; message?: string }
interface Head { id: number; name: string; frequency: string; residential_only: boolean; display_order: number; active: boolean }
interface GridRow { class_id: number; class_name: string; cells: { fee_head_id: number; amount: number | null; bill_month: number | null }[] }

export const FREQUENCIES: Record<string, string> = { MONTHLY: 'Every month', ANNUAL: 'Once a year', ONE_TIME: 'Once (first bill)' };
export const BILL_STATUS: Record<string, string> = { DUE: 'Due', PARTIAL: 'Part paid', PAID: 'Paid', WAIVED: 'Waived', CANCELLED: 'Cancelled' };
export const METHODS: Record<string, string> = { CASH: 'Cash', UPI: 'UPI', BANK: 'Bank transfer', CHEQUE: 'Cheque' };
export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function billBadge(b: { status: string; overdue?: boolean }): string {
  if (b.overdue) return 'badge-danger';
  return b.status === 'PAID' ? 'badge-success' : b.status === 'PARTIAL' ? 'badge-warning' : b.status === 'DUE' ? 'badge-info' : 'badge-secondary';
}

/**
 * Rahbar-e-Banaat > REB Fees: collect a payment (receipt), make the month's bills, see bills and dues, the day book
 * of payments, and set up fee heads and the amount of each for every class.
 */
@Component({
  selector: 'app-reb-fees',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, RouterLink, AlertsComponent, CardTableDirective, PagerComponent, PaginatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">REB Fees</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3 flex-nowrap overflow-auto">
      <li class="nav-item" *ngFor="let t of tabs"><a class="nav-link text-nowrap" href="" [class.active]="current === t.id" (click)="$event.preventDefault(); go(t.id)">{{ t.label }}</a></li>
    </ul>

    <!-- collect -->
    <div *ngIf="current === 'collect'" class="row">
      <div class="col-lg-5">
        <div class="card"><div class="card-body">
          <h4 class="header-title">Girl</h4>
          <input class="form-control mb-2" placeholder="Name or admission number" [(ngModel)]="girlQ" (ngModelChange)="searchGirls()" aria-label="Find a girl">
          <div class="list-group" *ngIf="girlOptions.length && !due">
            <a *ngFor="let g of girlOptions" class="list-group-item list-group-item-action py-2" tabindex="0" (click)="pickGirl(g.id)" (keydown.enter)="pickGirl(g.id)">
              {{ g.name }} <small class="text-muted">{{ g.admission_no }} · {{ g.section || 'no class' }}</small></a>
          </div>
          <div *ngIf="due">
            <div class="d-flex justify-content-between align-items-start">
              <div><strong>{{ due.student.name }}</strong><div class="small text-muted">{{ due.student.admission_no }}</div></div>
              <button class="btn btn-xs btn-light" (click)="due = null; girlQ = ''">Change</button>
            </div>
            <div *ngIf="due.sponsorship" class="small mt-2">Sponsor: <strong>{{ due.sponsorship.sponsor_name }}</strong> ({{ due.sponsorship.sponsor_code }}), ₹{{ due.sponsorship.monthly_amount | number: '1.0-2' }} a month</div>
            <table class="table table-sm mt-2 mb-0">
              <thead><tr><th>Month</th><th class="text-right">Family</th><th class="text-right">Sponsor</th></tr></thead>
              <tbody>
                <tr *ngIf="!due.bills.length"><td colspan="3" class="text-muted">Nothing is due.</td></tr>
                <tr *ngFor="let b of due.bills" [class.text-danger]="b.overdue">
                  <td>{{ b.label }}<span *ngIf="b.overdue" class="small"> · overdue</span></td>
                  <td class="text-right">{{ b.family_outstanding ? '₹' + (b.family_outstanding | number: '1.0-2') : '' }}</td>
                  <td class="text-right">{{ b.sponsor_outstanding ? '₹' + (b.sponsor_outstanding | number: '1.0-2') : '' }}</td>
                </tr>
                <tr *ngIf="due.bills.length" class="font-weight-bold"><td>Total</td>
                  <td class="text-right">₹{{ due.family_total | number: '1.0-2' }}</td><td class="text-right">₹{{ due.sponsor_total | number: '1.0-2' }}</td></tr>
              </tbody>
            </table>
            <a [routerLink]="['/admin/reb/students', due.student.id]" class="small">Her page and full ledger</a>
          </div>
        </div></div>
      </div>
      <div class="col-lg-7" *ngIf="due && canEdit">
        <div class="card"><div class="card-body">
          <h4 class="header-title">Take a payment</h4>
          <div class="row">
            <div class="col-md-6 form-group"><label for="p-payer">Paid by</label>
              <select id="p-payer" class="form-control" [(ngModel)]="pay.payer" (ngModelChange)="pay.amount = pay.payer === 'SPONSOR' ? due.sponsor_total : due.family_total">
                <option value="FAMILY">Family</option><option value="SPONSOR" [disabled]="!due.sponsor_total">Sponsor</option></select></div>
            <div class="col-md-6 form-group"><label for="p-amount">Amount (₹) *</label><input id="p-amount" type="number" min="1" step="0.01" class="form-control" [(ngModel)]="pay.amount"></div>
            <div class="col-md-6 form-group"><label for="p-date">Date</label><input id="p-date" type="date" class="form-control" [(ngModel)]="pay.paidOn"></div>
            <div class="col-md-6 form-group"><label for="p-method">Mode</label>
              <select id="p-method" class="form-control" [(ngModel)]="pay.method"><option *ngFor="let m of methodKeys" [value]="m">{{ methods[m] }}</option></select></div>
            <div class="col-md-6 form-group" *ngIf="pay.method !== 'CASH'"><label for="p-ref">{{ pay.method === 'CHEQUE' ? 'Cheque number' : 'Transaction reference' }} *</label>
              <input id="p-ref" class="form-control" [(ngModel)]="pay.reference"></div>
            <div class="col-12 form-group"><label for="p-note">Note</label><input id="p-note" class="form-control" [(ngModel)]="pay.note"></div>
          </div>
          <p class="small text-muted">The payment pays the oldest unpaid months first.</p>
          <button class="btn btn-success" (click)="collect()" [disabled]="busy || !pay.amount || (pay.method !== 'CASH' && !pay.reference)">{{ busy ? 'Saving…' : 'Record payment' }}</button>
          <div *ngIf="lastReceipt" class="alert alert-success mt-3 mb-0 d-flex justify-content-between align-items-center flex-wrap">
            <span>Receipt {{ lastReceipt.no }} recorded.</span>
            <button class="btn btn-sm btn-outline-success" (click)="receipt(lastReceipt.id)"><i class="mdi mdi-printer mr-1"></i>Receipt (PDF)</button>
          </div>
        </div></div>
      </div>
    </div>

    <!-- bills -->
    <ng-container *ngIf="current === 'bills'">
      <div class="card" *ngIf="canEdit"><div class="card-body">
        <h4 class="header-title">Make the month's bills</h4>
        <div class="d-flex flex-wrap align-items-end">
          <div class="form-group mr-2 mb-2"><label for="g-month">Month</label><input id="g-month" type="month" class="form-control" [(ngModel)]="genMonth" (ngModelChange)="plan = null"></div>
          <button class="btn btn-outline-primary mr-2 mb-2" (click)="previewBills()" [disabled]="busy || !genMonth">Preview</button>
          <button class="btn btn-primary mb-2" *ngIf="plan && plan.bills" (click)="makeBills()" [disabled]="busy">Make {{ plan.bills }} bills (₹{{ plan.total | number: '1.0-2' }})</button>
        </div>
        <div *ngIf="plan">
          <p class="mb-2">{{ plan.label }}: <strong>{{ plan.bills }}</strong> new bills, ₹{{ plan.total | number: '1.0-2' }}
            (families ₹{{ plan.family_total | number: '1.0-2' }}, sponsors ₹{{ plan.sponsor_total | number: '1.0-2' }}).
            <span *ngIf="skippedCount" class="text-muted">{{ skippedCount }} skipped.</span></p>
          <details><summary class="small">Show every girl</summary>
            <div class="table-responsive mt-2"><table class="table table-sm mb-0">
              <thead><tr><th>Girl</th><th>Class</th><th class="text-right">Bill</th><th class="text-right">Sponsor</th><th class="text-right">Family</th></tr></thead>
              <tbody><tr *ngFor="let r of plan.rows" [class.text-muted]="r.skipped">
                <td>{{ r.name }}</td><td>{{ r.section }}</td>
                <ng-container *ngIf="!r.skipped"><td class="text-right">₹{{ r.net | number: '1.0-2' }}</td><td class="text-right">{{ r.sponsor_share ? '₹' + (r.sponsor_share | number: '1.0-2') : '' }}</td><td class="text-right">₹{{ r.family_share | number: '1.0-2' }}</td></ng-container>
                <td *ngIf="r.skipped" colspan="3" class="text-right small">{{ r.skipped }}</td>
              </tr></tbody>
            </table></div>
          </details>
        </div>
      </div></div>

      <div class="card"><div class="card-body">
        <div class="row">
          <div class="col-6 col-md-3 form-group"><label for="b-month">Month</label><input id="b-month" type="month" class="form-control" [(ngModel)]="billMonth" (ngModelChange)="loadBills()"></div>
          <div class="col-6 col-md-3 form-group"><label for="b-section">Class</label>
            <select id="b-section" class="form-control" [(ngModel)]="billSection" (ngModelChange)="loadBills()"><option [ngValue]="null">All</option>
              <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select></div>
          <div class="col-6 col-md-3 form-group"><label for="b-status">Status</label>
            <select id="b-status" class="form-control" [(ngModel)]="billStatus" (ngModelChange)="loadBills()"><option value="">Any</option>
              <option value="OVERDUE">Overdue</option><option *ngFor="let k of statusKeys" [value]="k">{{ statuses[k] }}</option></select></div>
          <div class="col-6 col-md-3 form-group"><label for="b-q">Search</label><input id="b-q" class="form-control" [(ngModel)]="billQ" (ngModelChange)="pg.reset()" placeholder="Name, number"></div>
        </div>
        <div class="table-responsive"><table class="table table-sm table-centered table-hover mb-0">
          <thead><tr><th>Bill</th><th>Girl</th><th>Month</th><th class="text-right">Amount</th><th class="text-right">Still due</th><th>Status</th></tr></thead>
          <tbody>
            <tr *ngIf="!shownBills.length"><td colspan="6" class="text-center text-muted">No bills.</td></tr>
            <tr *ngFor="let b of shownBills | paginate: pg.page : pg.size" class="clickable" (click)="openBill(b.id)">
              <td class="small">{{ b.invoice_no }}</td><td><strong>{{ b.student_name }}</strong><div class="small text-muted">{{ b.section }}</div></td>
              <td>{{ b.period }}</td><td class="text-right">₹{{ b.net | number: '1.0-2' }}</td><td class="text-right">{{ b.outstanding ? '₹' + (b.outstanding | number: '1.0-2') : '' }}</td>
              <td><span class="badge" [ngClass]="badge(b)">{{ b.overdue ? 'Overdue' : statuses[b.status] }}</span></td>
            </tr>
          </tbody>
        </table></div>
        <app-pager [state]="pg" [total]="shownBills.length"></app-pager>
      </div></div>
    </ng-container>

    <!-- dues -->
    <div *ngIf="current === 'dues'" class="card"><div class="card-body">
      <div class="d-flex flex-wrap align-items-end mb-3">
        <div class="form-group mr-3 mb-2"><label for="d-section">Class</label>
          <select id="d-section" class="form-control" [(ngModel)]="duesSection" (ngModelChange)="loadDues()"><option [ngValue]="null">All</option>
            <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select></div>
        <div class="custom-control custom-checkbox mb-3"><input type="checkbox" class="custom-control-input" id="d-all" [(ngModel)]="duesAll" (ngModelChange)="loadDues()">
          <label class="custom-control-label" for="d-all">Include bills not yet past their due date</label></div>
      </div>
      <p class="small text-muted">{{ dues.length }} girls owe ₹{{ duesTotal | number: '1.0-2' }}.</p>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Girl</th><th>Unpaid months</th><th>Oldest</th><th class="text-right">Family</th><th class="text-right">Sponsor</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!dues.length"><td colspan="6" class="text-center text-muted">Nobody owes anything.</td></tr>
          <tr *ngFor="let d of dues">
            <td><a [routerLink]="['/admin/reb/students', d.student_id]"><strong>{{ d.name }}</strong></a><div class="small text-muted">{{ d.section }}</div></td>
            <td>{{ d.bills }}</td><td>{{ d.oldest_label }}</td>
            <td class="text-right">{{ d.family_outstanding ? '₹' + (d.family_outstanding | number: '1.0-2') : '' }}</td>
            <td class="text-right">{{ d.sponsor_outstanding ? '₹' + (d.sponsor_outstanding | number: '1.0-2') : '' }}</td>
            <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-outline-success" (click)="go('collect'); pickGirl(d.student_id)">Collect</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- day book -->
    <div *ngIf="current === 'daybook'" class="card"><div class="card-body">
      <div class="d-flex flex-wrap align-items-end mb-2">
        <div class="form-group mr-2 mb-2"><label for="db-from">From</label><input id="db-from" type="date" class="form-control" [(ngModel)]="dayFrom" (ngModelChange)="loadDayBook()"></div>
        <div class="form-group mr-2 mb-2"><label for="db-to">To</label><input id="db-to" type="date" class="form-control" [(ngModel)]="dayTo" (ngModelChange)="loadDayBook()"></div>
      </div>
      <p *ngIf="dayBook" class="mb-2"><strong>₹{{ dayBook.total | number: '1.0-2' }}</strong> received
        <span class="text-muted" *ngFor="let m of methodTotals"> · {{ methods[m.key] || m.key }} ₹{{ m.value | number: '1.0-2' }}</span></p>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Date</th><th>Receipt</th><th>Girl</th><th>Paid by</th><th>Mode</th><th class="text-right">Amount</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!dayBook?.payments?.length"><td colspan="7" class="text-center text-muted">No payments.</td></tr>
          <tr *ngFor="let p of dayBook?.payments" [class.text-muted]="p.status === 'CANCELLED'">
            <td>{{ p.paid_on | date: 'd MMM' }}</td><td class="small">{{ p.receipt_no }}<div *ngIf="p.status === 'CANCELLED'" class="text-danger">Cancelled</div></td>
            <td>{{ p.student_name }}</td><td>{{ p.payer === 'SPONSOR' ? 'Sponsor: ' + p.sponsor_name : 'Family' }}</td>
            <td>{{ methods[p.method] }}<div class="small text-muted">{{ p.reference }}</div></td>
            <td class="text-right">₹{{ p.amount | number: '1.0-2' }}</td>
            <td class="text-right text-nowrap">
              <button class="btn btn-xs btn-outline-secondary mr-1" (click)="receipt(p.id)" aria-label="Receipt PDF"><i class="mdi mdi-printer"></i></button>
              <button *ngIf="canApprove && p.status !== 'CANCELLED'" class="btn btn-xs btn-outline-danger" (click)="cancelReceipt(p)">Cancel</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- fee set-up -->
    <ng-container *ngIf="current === 'setup'">
      <div class="card"><div class="card-body">
        <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
          <div><h4 class="header-title mb-1">Fee heads</h4><p class="text-muted small mb-2">The kinds of fee, e.g. Tuition, Hostel & food, Exam, Admission.</p></div>
          <button *ngIf="canEdit" class="btn btn-sm btn-primary" (click)="head = { id: null, name: '', frequency: 'MONTHLY', residentialOnly: false, displayOrder: heads.length + 1, active: true }">
            <i class="mdi mdi-plus mr-1"></i>Add fee head</button>
        </div>
        <div class="table-responsive"><table class="table table-sm table-centered mb-0">
          <thead><tr><th>Fee head</th><th>Charged</th><th>Hostel only</th><th>Active</th><th></th></tr></thead>
          <tbody>
            <tr *ngIf="!heads.length"><td colspan="5" class="text-center text-muted">No fee heads yet.</td></tr>
            <tr *ngFor="let h of heads"><td><strong>{{ h.name }}</strong></td><td>{{ frequencies[h.frequency] }}</td><td>{{ h.residential_only ? 'Yes' : '' }}</td><td>{{ h.active ? 'Yes' : 'No' }}</td>
              <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary" (click)="head = { id: h.id, name: h.name, frequency: h.frequency, residentialOnly: h.residential_only, displayOrder: h.display_order, active: h.active }">Edit</button></td></tr>
          </tbody>
        </table></div>
      </div></div>

      <div class="card"><div class="card-body">
        <div class="d-flex flex-wrap align-items-end justify-content-between mb-2">
          <div><h4 class="header-title mb-1">Fees of each class</h4><p class="text-muted small mb-2">Monthly amount of each head for each class. Leave empty for heads a class doesn't pay. Once-a-year heads also need the month they're billed in.</p></div>
          <div class="form-group mb-2"><label for="s-session" class="small mb-0">Session</label>
            <select id="s-session" class="form-control form-control-sm" [(ngModel)]="sessionId" (ngModelChange)="loadGrid()"><option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option></select></div>
        </div>
        <p *ngIf="!grid.length" class="text-muted">Add classes in REB Setup and fee heads above first.</p>
        <div class="table-responsive" *ngIf="grid.length"><table class="table table-sm mb-2">
          <thead><tr><th>Class</th><th *ngFor="let h of gridHeads">{{ h.name }}<div class="small text-muted font-weight-normal">{{ frequencies[h.frequency] }}</div></th></tr></thead>
          <tbody>
            <tr *ngFor="let r of grid"><td class="text-nowrap"><strong>{{ r.class_name }}</strong></td>
              <td *ngFor="let c of r.cells; let j = index" style="min-width: 120px">
                <input type="number" min="0" class="form-control form-control-sm" [(ngModel)]="c.amount" [disabled]="!canEdit" [attr.aria-label]="gridHeads[j].name + ' for ' + r.class_name" placeholder="—">
                <select *ngIf="gridHeads[j].frequency === 'ANNUAL' && c.amount" class="form-control form-control-sm mt-1" [(ngModel)]="c.bill_month" [disabled]="!canEdit" [attr.aria-label]="'Month for ' + gridHeads[j].name">
                  <option [ngValue]="null">Month…</option><option *ngFor="let m of months; let k = index" [ngValue]="k + 1">{{ m }}</option></select>
              </td>
            </tr>
          </tbody>
        </table></div>
        <button *ngIf="canEdit && grid.length" class="btn btn-primary" (click)="saveGrid()" [disabled]="busy">Save fees</button>
      </div></div>
    </ng-container>

    <!-- bill detail -->
    <div *ngIf="bill" class="modal fade show d-block" tabindex="-1" (click)="bill = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Bill {{ bill['invoice_no'] }}: {{ bill['student_name'] }}, {{ bill['label'] }}</h5><button type="button" class="close" (click)="bill = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <table class="table table-sm">
              <thead><tr><th>Fee</th><th class="text-right">Amount</th><th class="text-right">Concession</th></tr></thead>
              <tbody>
                <tr *ngFor="let l of billLines"><td>{{ l['fee_head_name'] }}</td><td class="text-right">₹{{ $any(l['amount']) | number: '1.0-2' }}</td><td class="text-right">{{ $any(l['concession']) ? '₹' + ($any(l['concession']) | number: '1.0-2') : '' }}</td></tr>
                <tr class="font-weight-bold"><td>To pay</td><td class="text-right" colspan="2">₹{{ $any(bill['net']) | number: '1.0-2' }}
                  <span class="font-weight-normal small"> (sponsor ₹{{ $any(bill['sponsor_share']) | number: '1.0-2' }}, family ₹{{ $any(bill['family_share']) | number: '1.0-2' }})</span></td></tr>
              </tbody>
            </table>
            <p class="mb-1">Due {{ $any(bill['due_date']) | appDate }} · {{ statuses[$any(bill['status'])] }}<span *ngIf="bill['note']"> · {{ bill['note'] }}</span></p>
            <h6 class="mt-3">Payments</h6>
            <p *ngIf="!billPayments.length" class="text-muted small">None yet.</p>
            <ul class="list-unstyled small mb-0"><li *ngFor="let p of billPayments">{{ $any(p['paid_on']) | appDate }} · {{ p['receipt_no'] }} · {{ p['share'] === 'SPONSOR' ? 'sponsor' : 'family' }} · ₹{{ $any(p['amount']) | number: '1.0-2' }}</li></ul>
            <div *ngIf="canApprove && (bill['status'] === 'DUE' || bill['status'] === 'PARTIAL')" class="border-top mt-3 pt-3">
              <label for="bill-reason">Reason (for waiving or cancelling)</label>
              <input id="bill-reason" class="form-control mb-2" [(ngModel)]="billReason">
              <button class="btn btn-sm btn-outline-warning mr-2" (click)="billAction('waive')" [disabled]="!billReason.trim()">Waive the rest</button>
              <button class="btn btn-sm btn-outline-danger" (click)="billAction('cancel')" [disabled]="!billReason.trim()">Cancel the bill</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- fee head -->
    <div *ngIf="head" class="modal fade show d-block" tabindex="-1" (click)="head = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ head.id ? 'Edit fee head' : 'Add fee head' }}</h5><button type="button" class="close" (click)="head = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="h-name">Name *</label><input id="h-name" class="form-control" [(ngModel)]="head.name" placeholder="e.g. Tuition"></div>
            <div class="form-group"><label for="h-freq">Charged</label>
              <select id="h-freq" class="form-control" [(ngModel)]="head.frequency"><option *ngFor="let k of frequencyKeys" [value]="k">{{ frequencies[k] }}</option></select></div>
            <div class="form-group"><label for="h-order">Order</label><input id="h-order" type="number" class="form-control" [(ngModel)]="head.displayOrder"></div>
            <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="h-res" [(ngModel)]="head.residentialOnly"><label class="custom-control-label" for="h-res">Only for girls in the hostel</label></div>
            <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="h-active" [(ngModel)]="head.active"><label class="custom-control-label" for="h-active">Active</label></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="head = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveHead()" [disabled]="busy || !head.name.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebFeesComponent implements OnInit {
  /** From the query string: ?tab=collect&student=12 */
  @Input() tab?: string;
  @Input() student?: string;

  readonly pg = new PageState(25);
  readonly frequencies = FREQUENCIES;
  readonly frequencyKeys = Object.keys(FREQUENCIES);
  readonly statuses = BILL_STATUS;
  readonly statusKeys = Object.keys(BILL_STATUS);
  readonly methods = METHODS;
  readonly methodKeys = Object.keys(METHODS);
  readonly months = MONTHS;
  readonly badge = billBadge;

  current: Tab = 'collect';
  sessions: RebSession[] = [];
  sections: RebSectionRow[] = [];
  sessionId: number | null = null;

  girlQ = '';
  girlOptions: RebStudentRow[] = [];
  due: Due | null = null;
  pay = { payer: 'FAMILY', amount: 0 as number | null, paidOn: isoDate(new Date()), method: 'CASH', reference: '', note: '' };
  lastReceipt: { id: number; no: string } | null = null;

  genMonth = isoDate(new Date()).slice(0, 7);
  plan: Plan | null = null;
  billMonth = isoDate(new Date()).slice(0, 7);
  billSection: number | null = null;
  billStatus = '';
  billQ = '';
  bills: Bill[] = [];
  bill: Record<string, unknown> | null = null;
  billReason = '';

  dues: { student_id: number; name: string; section: string | null; bills: number; oldest_label: string; family_outstanding: number; sponsor_outstanding: number; total: number }[] = [];
  duesSection: number | null = null;
  duesAll = false;

  dayFrom = isoDate(new Date());
  dayTo = isoDate(new Date());
  dayBook: { total: number; by_method: Record<string, number>; payments: Record<string, any>[] } | null = null;

  heads: Head[] = [];
  head: { id: number | null; name: string; frequency: string; residentialOnly: boolean; displayOrder: number; active: boolean } | null = null;
  gridHeads: Head[] = [];
  grid: GridRow[] = [];

  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_FEES', 'EDIT'); }
  get canApprove(): boolean { return this.auth.can('REB_FEE_APPROVALS', 'EDIT'); }
  get tabs(): { id: Tab; label: string }[] {
    return [
      { id: 'collect' as Tab, label: 'Collect' }, { id: 'bills' as Tab, label: 'Bills' }, { id: 'dues' as Tab, label: 'Dues' },
      { id: 'daybook' as Tab, label: 'Day book' }, { id: 'setup' as Tab, label: 'Fee setup' }
    ];
  }
  get skippedCount(): number { return this.plan?.rows.filter((r) => r.skipped).length ?? 0; }
  get duesTotal(): number { return this.dues.reduce((s, d) => s + Number(d.total), 0); }
  get billLines(): Record<string, unknown>[] { return (this.bill?.['lines'] as Record<string, unknown>[]) ?? []; }
  get billPayments(): Record<string, unknown>[] { return (this.bill?.['payments'] as Record<string, unknown>[]) ?? []; }
  get methodTotals(): { key: string; value: number }[] { return Object.entries(this.dayBook?.by_method ?? {}).map(([key, value]) => ({ key, value })); }
  get shownBills(): Bill[] {
    const q = this.billQ.trim().toLowerCase();
    return this.bills.filter((b) => !q || [b.student_name, b.admission_no, b.invoice_no].some((v) => (v ?? '').toLowerCase().includes(q)));
  }

  ngOnInit(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        this.sessions = r;
        this.sessionId = (r.find((s) => s.is_current) ?? r[0])?.id ?? null;
        if (this.sessionId) this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: this.sessionId }).subscribe({ next: (s) => (this.sections = s) });
      },
      error: () => (this.sessions = [])
    });
    const tab = this.tabs.find((t) => t.id === this.tab);
    this.go(tab ? tab.id : 'collect');
    if (this.student) this.pickGirl(Number(this.student));
  }

  go(tab: Tab): void {
    this.current = tab;
    if (tab === 'bills') this.loadBills();
    if (tab === 'dues') this.loadDues();
    if (tab === 'daybook') this.loadDayBook();
    if (tab === 'setup') { this.loadHeads(); this.loadGrid(); }
  }

  // ---- collect
  searchGirls(): void {
    const q = this.girlQ.trim().toLowerCase();
    if (q.length < 2) { this.girlOptions = []; return; }
    this.api.get<PagedResult<RebStudentRow>>('/admin/reb/students', { status: 'ALL', q, page: 0, size: 12 }).subscribe({
      next: (r) => { if (this.girlQ.trim().toLowerCase() === q) this.girlOptions = r.rows; } });
  }

  pickGirl(id: number): void {
    this.lastReceipt = null;
    this.api.get<Due>(`/admin/reb/students/${id}/due`).subscribe({
      next: (d) => {
        this.due = d;
        this.girlOptions = [];
        this.pay = { payer: d.family_total ? 'FAMILY' : d.sponsor_total ? 'SPONSOR' : 'FAMILY', amount: d.family_total || d.sponsor_total || null,
          paidOn: isoDate(new Date()), method: 'CASH', reference: '', note: '' };
      },
      error: (e) => (this.error = errorText(e, 'Could not load what she owes.'))
    });
  }

  collect(): void {
    if (!this.due) return;
    this.busy = true;
    const body = { ...this.pay, studentId: this.due.student.id, sponsorUserId: this.pay.payer === 'SPONSOR' ? this.due.sponsorship?.sponsor_user_id : null };
    this.api.post<{ message: string; id: number; receipt_no: string }>('/admin/reb/payments', body).subscribe({
      next: (r) => {
        this.busy = false;
        this.message = r.message;
        const id = this.due!.student.id;
        this.pickGirl(id);
        setTimeout(() => (this.lastReceipt = { id: r.id, no: r.receipt_no }));
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not record the payment.'); }
    });
  }

  receipt(id: number): void {
    this.api.download(`/admin/reb/payments/${id}/receipt`, {}, 'receipt.pdf').subscribe({ error: (e) => (this.error = errorText(e, 'Could not create the receipt.')) });
  }

  // ---- bills
  previewBills(): void {
    this.busy = true;
    this.api.get<Plan>('/admin/reb/invoices/preview', { period: this.genMonth }).subscribe({
      next: (r) => { this.busy = false; this.plan = r; },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not prepare the bills.'); }
    });
  }

  makeBills(): void {
    this.busy = true;
    this.api.post<Plan>('/admin/reb/invoices/generate', { period: this.genMonth }).subscribe({
      next: (r) => { this.busy = false; this.plan = null; this.message = r.message ?? 'Bills made.'; this.billMonth = this.genMonth; this.loadBills(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not make the bills.'); }
    });
  }

  loadBills(): void {
    this.api.get<Bill[]>('/admin/reb/invoices', { period: this.billMonth, sectionId: this.billSection, status: this.billStatus }).subscribe({
      next: (r) => { this.bills = r; this.pg.reset(); },
      error: (e) => (this.error = errorText(e, 'Could not load the bills.'))
    });
  }

  openBill(id: number): void {
    this.billReason = '';
    this.api.get<Record<string, unknown>>(`/admin/reb/invoices/${id}`).subscribe({ next: (r) => (this.bill = r), error: (e) => (this.error = errorText(e, 'Could not open the bill.')) });
  }

  billAction(action: 'waive' | 'cancel'): void {
    const id = this.bill?.['id'];
    if (!id || !confirm(action === 'waive' ? 'Waive what is still due on this bill?' : 'Cancel this bill?')) return;
    this.api.post<{ message: string }>(`/admin/reb/invoices/${id}/${action}`, { reason: this.billReason }).subscribe({
      next: (r) => { this.bill = null; this.message = r.message; this.loadBills(); },
      error: (e) => (this.error = errorText(e, 'Could not change the bill.'))
    });
  }

  // ---- dues and day book
  loadDues(): void {
    this.api.get<typeof this.dues>('/admin/reb/dues', { sectionId: this.duesSection, all: this.duesAll }).subscribe({
      next: (r) => (this.dues = r), error: (e) => (this.error = errorText(e, 'Could not load the dues.'))
    });
  }

  loadDayBook(): void {
    this.api.get<NonNullable<typeof this.dayBook>>('/admin/reb/payments', { from: this.dayFrom, to: this.dayTo }).subscribe({
      next: (r) => (this.dayBook = r), error: (e) => (this.error = errorText(e, 'Could not load the payments.'))
    });
  }

  cancelReceipt(p: Record<string, any>): void {
    const reason = prompt(`Why cancel receipt ${p['receipt_no']}? The bills it paid become unpaid again.`);
    if (!reason?.trim()) return;
    this.api.post<{ message: string }>(`/admin/reb/payments/${p['id']}/cancel`, { reason }).subscribe({
      next: (r) => { this.message = r.message; this.loadDayBook(); },
      error: (e) => (this.error = errorText(e, 'Could not cancel the receipt.'))
    });
  }

  // ---- set-up
  loadHeads(): void {
    this.api.get<Head[]>('/admin/reb/fee-heads').subscribe({ next: (r) => (this.heads = r), error: (e) => (this.error = errorText(e, 'Could not load the fee heads.')) });
  }

  saveHead(): void {
    const h = this.head;
    if (!h) return;
    this.busy = true;
    const req = h.id ? this.api.put<{ message: string }>(`/admin/reb/fee-heads/${h.id}`, h) : this.api.post<{ message: string }>('/admin/reb/fee-heads', h);
    req.subscribe({
      next: (r) => { this.busy = false; this.head = null; this.message = r.message; this.loadHeads(); this.loadGrid(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the fee head.'); }
    });
  }

  loadGrid(): void {
    if (!this.sessionId) { this.grid = []; return; }
    this.api.get<{ heads: Head[]; rows: GridRow[] }>('/admin/reb/fee-structure', { sessionId: this.sessionId }).subscribe({
      next: (r) => { this.gridHeads = r.heads; this.grid = r.heads.length ? r.rows : []; },
      error: (e) => (this.error = errorText(e, 'Could not load the fees.'))
    });
  }

  saveGrid(): void {
    const cells = this.grid.flatMap((r) => r.cells.map((c) => ({ classId: r.class_id, feeHeadId: c.fee_head_id, amount: c.amount, billMonth: c.bill_month })));
    this.busy = true;
    this.api.put<{ message: string }>('/admin/reb/fee-structure', { sessionId: this.sessionId, cells }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.loadGrid(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the fees.'); }
    });
  }
}
