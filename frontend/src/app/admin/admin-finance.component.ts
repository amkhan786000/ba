import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';
import { isoDate } from '../shared/format';
import { AppDatePipe } from '../shared/app-date';
import { DateInputComponent } from '../shared/date-input.component';
import { FundBalance, FundSummaryComponent } from '../shared/charts/fund-summary.component';

interface Category { id: number; name: string; kind: string; active: boolean }
/** Someone on the book's "Paid to" list. */
interface Payee { id: number; name: string; phone: string | null; note: string | null; active: boolean }
interface ChapterOption { id: number; name: string; active: boolean }
interface Entry {
  id: number; entry_date: string; kind: string; category_id: number; category_name: string; project_id: number | null; project_name: string | null;
  amount: number; party: string | null; description: string | null; method: string; reference: string | null; entered_by: string | null;
  chapter_id: number | null; payee_id: number | null;
}
interface Line { name: string; amount: number }
interface Summary {
  year: number; income_by_category: Line[]; expense_by_category: Line[]; expense_by_project: Line[]; income: number; expense: number;
  income_by_chapter?: Line[]; expense_by_payee?: Line[];
  reb_fees_collected?: number; published: boolean; note?: string;
}

const METHODS: Record<string, string> = { CASH: 'Cash', UPI: 'UPI', BANK: 'Bank transfer', CHEQUE: 'Cheque' };

/**
 * Rahbar-e-Banaat > REB Income & Expenses: record money in and out by category (and project), see a year's summary
 * (with the REB fees collected), and choose whether that summary is shown on biharanjuman.com. (Rahbar's own book of
 * income & expenses was removed; the tables keep a book column, so this page names its book.)
 */
@Component({
  selector: 'app-admin-finance',
  standalone: true,
  imports: [FundSummaryComponent, DateInputComponent, AppDatePipe, CommonModule, FormsModule, AlertsComponent, CardTableDirective, PagerComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">REB Income &amp; Expenses</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p class="text-muted">Rahbar-e-Banaat's own book: money received for the school and what it spends.
      <span *ngIf="!canEdit" class="badge badge-light ml-1"><i class="mdi mdi-eye mr-1"></i>View only</span></p>

    <app-fund-summary [fund]="fund" [link]="false"></app-fund-summary>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'entries'" (click)="$event.preventDefault(); tab = 'entries'">Entries</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'summary'" (click)="$event.preventDefault(); tab = 'summary'; loadSummary()">Yearly summary</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'categories'" (click)="$event.preventDefault(); tab = 'categories'">Categories</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'payees'" (click)="$event.preventDefault(); tab = 'payees'">Paid to</a></li>
    </ul>

    <!-- entries -->
    <div class="card" *ngIf="tab === 'entries'"><div class="card-body">
      <div class="row align-items-end">
        <div class="col-6 col-md-2 form-group"><label for="f-from">From</label><app-date-input id="f-from" [(ngModel)]="from" (ngModelChange)="table.filter()"></app-date-input></div>
        <div class="col-6 col-md-2 form-group"><label for="f-to">To</label><app-date-input id="f-to" [(ngModel)]="to" (ngModelChange)="table.filter()"></app-date-input></div>
        <div class="col-6 col-md-2 form-group"><label for="f-kind">Kind</label>
          <select id="f-kind" class="form-control" [(ngModel)]="kind" (ngModelChange)="table.filter()"><option value="">Both</option><option value="INCOME">Income</option><option value="EXPENSE">Expenses</option></select></div>
        <div class="col-6 col-md-3 form-group"><label for="f-cat">Category</label>
          <select id="f-cat" class="form-control" [(ngModel)]="categoryId" (ngModelChange)="table.filter()"><option [ngValue]="null">All</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }} ({{ c.kind === 'INCOME' ? 'in' : 'out' }})</option></select></div>
        <div class="col-12 col-md-3 form-group"><label for="f-q">Search</label><input id="f-q" class="form-control" placeholder="Party, description, reference" [(ngModel)]="q" (ngModelChange)="table.search()"></div>
        <div class="col-12 form-group text-md-right"><button *ngIf="canEdit" class="btn btn-primary" (click)="openEntry()"><i class="mdi mdi-plus mr-1"></i>Add entry</button></div>
      </div>
      <p class="mb-2">Income <strong class="text-success">₹{{ totals.income | number: '1.0-2' }}</strong> · Expenses <strong class="text-danger">₹{{ totals.expense | number: '1.0-2' }}</strong>
        · Balance <strong>₹{{ totals.income - totals.expense | number: '1.0-2' }}</strong></p>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Date</th><th>Category</th><th>Project</th><th>Paid to / from</th><th class="text-right">Amount</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!table.loading && !table.rows.length"><td colspan="6" class="text-center text-muted">No entries.</td></tr>
          <tr *ngFor="let e of table.rows">
            <td class="text-nowrap">{{ e.entry_date | appDate }}</td>
            <td>{{ e.category_name }}<div class="small text-muted">{{ e.description }}</div></td>
            <td>{{ e.project_name || '--' }}</td>
            <td>{{ e.party || '--' }}<div class="small text-muted">{{ methods[e.method] }}{{ e.reference ? ' · ' + e.reference : '' }}</div></td>
            <td class="text-right text-nowrap" [class.text-success]="e.kind === 'INCOME'" [class.text-danger]="e.kind === 'EXPENSE'">{{ e.kind === 'INCOME' ? '+' : '−' }}₹{{ e.amount | number: '1.0-2' }}</td>
            <td class="text-right text-nowrap"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="openEntry(e)">Edit</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="deleteEntry(e)">Delete</button></td>
          </tr>
        </tbody>
      </table></div>
      <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
    </div></div>

    <!-- summary -->
    <ng-container *ngIf="tab === 'summary'">
      <div class="card"><div class="card-body d-flex flex-wrap align-items-end justify-content-between">
        <div class="form-group mb-2"><label for="s-year">Year</label>
          <select id="s-year" class="form-control" [(ngModel)]="year" (ngModelChange)="loadSummary()"><option *ngFor="let y of years" [ngValue]="y">{{ y }}</option></select></div>
        <div *ngIf="summary && canEdit" class="mb-2">
          <span *ngIf="summary.published" class="badge badge-success mr-2">Shown on the website</span>
          <button *ngIf="!summary.published" class="btn btn-outline-success btn-sm" (click)="publish()">Show this year on the website</button>
          <button *ngIf="summary.published" class="btn btn-outline-secondary btn-sm" (click)="unpublish()">Take it off the website</button>
        </div>
      </div></div>
      <div class="row" *ngIf="summary as s">
        <div class="col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Money in</h4>
            <table class="table table-sm mb-0"><tbody>
              <tr *ngFor="let l of s.income_by_category"><td>{{ l.name }}</td><td class="text-right">₹{{ l.amount | number: '1.0-2' }}</td></tr>
              <tr class="font-weight-bold"><td>Total recorded here</td><td class="text-right">₹{{ s.income | number: '1.0-2' }}</td></tr>
              <tr *ngIf="s.reb_fees_collected != null"><td>REB fees collected <span class="small text-muted">(REB Fees)</span></td><td class="text-right">₹{{ s.reb_fees_collected | number: '1.0-2' }}</td></tr>
            </tbody></table>
          </div></div>
        </div>
        <div class="col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Money out</h4>
            <table class="table table-sm mb-0"><tbody>
              <tr *ngFor="let l of s.expense_by_category"><td>{{ l.name }}</td><td class="text-right">₹{{ l.amount | number: '1.0-2' }}</td></tr>
              <tr class="font-weight-bold"><td>Total recorded here</td><td class="text-right">₹{{ s.expense | number: '1.0-2' }}</td></tr>
            </tbody></table>
          </div></div>
        </div>
        <div class="col-md-6" *ngIf="s.income_by_chapter?.length">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Received from (by chapter)</h4>
            <table class="table table-sm mb-0"><tbody>
              <tr *ngFor="let l of s.income_by_chapter"><td>{{ l.name }}</td><td class="text-right">₹{{ l.amount | number: '1.0-2' }}</td></tr>
            </tbody></table>
          </div></div>
        </div>
        <div class="col-md-6" *ngIf="s.expense_by_payee?.length">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Paid to</h4>
            <table class="table table-sm mb-0"><tbody>
              <tr *ngFor="let l of s.expense_by_payee"><td>{{ l.name }}</td><td class="text-right">₹{{ l.amount | number: '1.0-2' }}</td></tr>
            </tbody></table>
            <p class="small text-muted mb-0 mt-2">Names stay in Rahbar: they are never shown on the website.</p>
          </div></div>
        </div>
        <div class="col-12">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Where the money went (by project)</h4>
            <p *ngIf="!s.expense_by_project.length" class="text-muted mb-0">No expenses recorded for {{ s.year }}.</p>
            <div *ngFor="let l of s.expense_by_project" class="mb-2">
              <div class="d-flex justify-content-between small"><span>{{ l.name }}</span><span>₹{{ l.amount | number: '1.0-0' }}</span></div>
              <div class="progress" style="height: 8px"><div class="progress-bar bg-success" [style.width.%]="share(l.amount, s.expense)"></div></div>
            </div>
            <p class="small text-muted mb-0 mt-2">The website shows these totals only: no names, payees or references.</p>
          </div></div>
        </div>
      </div>
    </ng-container>

    <!-- categories -->
    <div class="card" *ngIf="tab === 'categories'"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <p class="text-muted mb-2 mr-3">Kinds of income and expense used for entries and the summary.</p>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm" (click)="cat = { id: null, name: '', kind: 'EXPENSE', active: true }"><i class="mdi mdi-plus mr-1"></i>Add category</button>
      </div>
      <div class="row">
        <div class="col-md-6" *ngFor="let k of ['INCOME', 'EXPENSE']">
          <h5>{{ k === 'INCOME' ? 'Income' : 'Expenses' }}</h5>
          <table class="table table-sm"><tbody>
            <tr *ngFor="let c of categoriesOf(k)" [class.text-muted]="!c.active"><td>{{ c.name }}<span *ngIf="!c.active"> (hidden)</span></td>
              <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="cat = { id: c.id, name: c.name, kind: c.kind, active: c.active }">Edit</button>
                <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="deleteCategory(c)">Delete</button></td></tr>
          </tbody></table>
        </div>
      </div>
    </div></div>

    <!-- paid to -->
    <div class="card" *ngIf="tab === 'payees'"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <p class="text-muted mb-2 mr-3">People and businesses REB pays (landlord, staff, shops...): choose them in "Paid to" when
          recording an expense. Income can be "received from" a chapter.</p>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm" (click)="payee = { id: null, name: '', phone: '', note: '', active: true }"><i class="mdi mdi-plus mr-1"></i>Add</button>
      </div>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead><tr><th>Name</th><th>Phone</th><th>Note</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!payees.length"><td colspan="4" class="text-center text-muted">Nobody on the list yet.</td></tr>
          <tr *ngFor="let p of payees" [class.text-muted]="!p.active">
            <td>{{ p.name }}<span *ngIf="!p.active"> (hidden)</span></td><td>{{ p.phone || '--' }}</td><td>{{ p.note || '' }}</td>
            <td class="text-right text-nowrap"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="payee = { id: p.id, name: p.name, phone: p.phone || '', note: p.note || '', active: p.active }">Edit</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="deletePayee(p)">Delete</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="payee" class="modal fade show d-block" tabindex="-1" (click)="payee = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ payee.id ? 'Edit' : 'Add to Paid to' }}</h5><button type="button" class="close" (click)="payee = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="p-name">Name *</label><input id="p-name" class="form-control" maxlength="150" [(ngModel)]="payee.name" placeholder="e.g. Office landlord, Patna"></div>
            <div class="form-group"><label for="p-phone">Phone</label><input id="p-phone" class="form-control" maxlength="30" [(ngModel)]="payee.phone"></div>
            <div class="form-group"><label for="p-note">Note</label><input id="p-note" class="form-control" maxlength="300" [(ngModel)]="payee.note" placeholder="e.g. bank account, what for"></div>
            <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="p-active" [(ngModel)]="payee.active"><label class="custom-control-label" for="p-active">Active (shown in "Paid to")</label></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="payee = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="savePayee()" [disabled]="busy || !payee.name.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="entry" class="modal fade show d-block" tabindex="-1" (click)="entry = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ entry.id ? 'Edit entry' : 'Add entry' }}</h5><button type="button" class="close" (click)="entry = null" aria-label="Close">&times;</button></div>
          <div class="modal-body"><div class="row">
            <div class="col-6 form-group"><label for="e-date">Date *</label><app-date-input id="e-date" [max]="today" [(ngModel)]="entry.entryDate"></app-date-input></div>
            <div class="col-6 form-group"><label for="e-amount">Amount (₹) *</label><input id="e-amount" type="number" min="0" step="0.01" class="form-control" [(ngModel)]="entry.amount"></div>
            <div class="col-12 form-group"><label for="e-cat">Category *</label>
              <select id="e-cat" class="form-control" [ngModel]="entry.categoryId" (ngModelChange)="pickCategory($event)"><option [ngValue]="null">-- Choose --</option>
                <optgroup label="Income"><option *ngFor="let c of activeOf('INCOME')" [ngValue]="c.id">{{ c.name }}</option></optgroup>
                <optgroup label="Expenses"><option *ngFor="let c of activeOf('EXPENSE')" [ngValue]="c.id">{{ c.name }}</option></optgroup></select></div>
            <div class="col-12 form-group"><label for="e-project">Project</label>
              <select id="e-project" class="form-control" [(ngModel)]="entry.projectId"><option [ngValue]="null">General (the organisation)</option>
                <option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}</option></select></div>
            <div class="col-12 form-group" *ngIf="isIncome">
              <label for="e-from">Received from</label>
              <select id="e-from" class="form-control" [(ngModel)]="entry.who">
                <option [ngValue]="null">-- Choose --</option>
                <optgroup label="Chapters"><option *ngFor="let c of activeChapters" [ngValue]="c.id">{{ c.name }}</option></optgroup>
                <option [ngValue]="'other'">Someone else (type the name)</option>
              </select>
              <input *ngIf="entry.who === 'other'" class="form-control mt-2" maxlength="150" [(ngModel)]="entry.party" placeholder="Name">
            </div>
            <div class="col-12 form-group" *ngIf="!isIncome">
              <label for="e-to">Paid to</label>
              <select id="e-to" class="form-control" [(ngModel)]="entry.who">
                <option [ngValue]="null">-- Choose --</option>
                <option *ngFor="let p of activePayees" [ngValue]="p.id">{{ p.name }}</option>
                <option [ngValue]="'other'">Someone else (type the name)</option>
              </select>
              <input *ngIf="entry.who === 'other'" class="form-control mt-2" maxlength="150" [(ngModel)]="entry.party" placeholder="Name">
              <small class="form-text text-muted">Not on the list? Add them in the <a href="" (click)="$event.preventDefault(); entry = null; tab = 'payees'">Paid to</a> tab.</small>
            </div>
            <div class="col-6 form-group"><label for="e-method">Mode</label>
              <select id="e-method" class="form-control" [(ngModel)]="entry.method"><option *ngFor="let m of methodKeys" [value]="m">{{ methods[m] }}</option></select></div>
            <div class="col-6 form-group"><label for="e-ref">Reference</label><input id="e-ref" class="form-control" [(ngModel)]="entry.reference"></div>
            <div class="col-12 form-group mb-0"><label for="e-desc">Description</label><input id="e-desc" class="form-control" [(ngModel)]="entry.description"></div>
          </div></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="entry = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveEntry()" [disabled]="busy || !entry.entryDate || !entry.categoryId || !entry.amount">Save</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="cat" class="modal fade show d-block" tabindex="-1" (click)="cat = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ cat.id ? 'Edit category' : 'Add category' }}</h5><button type="button" class="close" (click)="cat = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group" *ngIf="!cat.id"><label for="c-kind">Kind</label>
              <select id="c-kind" class="form-control" [(ngModel)]="cat.kind"><option value="INCOME">Income</option><option value="EXPENSE">Expense</option></select></div>
            <div class="form-group"><label for="c-name">Name *</label><input id="c-name" class="form-control" maxlength="100" [(ngModel)]="cat.name"></div>
            <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="c-active" [(ngModel)]="cat.active"><label class="custom-control-label" for="c-active">Active</label></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="cat = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveCategory()" [disabled]="busy || !cat.name.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminFinanceComponent implements OnInit, OnDestroy {
  readonly methods = METHODS;
  readonly methodKeys = Object.keys(METHODS);
  readonly today = isoDate(new Date());
  readonly years = Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i);
  tab: 'entries' | 'summary' | 'categories' | 'payees' = 'entries';
  categories: Category[] = [];
  projects: { id: number; name: string }[] = [];
  from = `${new Date().getFullYear()}-01-01`;
  to = `${new Date().getFullYear()}-12-31`;
  kind = '';
  categoryId: number | null = null;
  q = '';
  readonly table = new ServerTable<Entry, PagedResult<Entry> & { income: number; expense: number }>(
    (p) => this.api.get(`${this.base}/entries`, { from: this.from, to: this.to, kind: this.kind, categoryId: this.categoryId, q: this.q, ...p }),
    (e) => (this.error = errorText(e, 'Could not load the entries.')));
  year = new Date().getFullYear();
  summary: Summary | null = null;
  entry: { id: number | null; entryDate: string; categoryId: number | null; projectId: number | null; amount: number | null; party: string;
    description: string; method: string; reference: string;
    /** Received from (a chapter id) or paid to (a payee id), or 'other' with the name typed in party. */
    who: number | 'other' | null } | null = null;
  payees: Payee[] = [];
  chapters: ChapterOption[] = [];
  payee: { id: number | null; name: string; phone: string; note: string; active: boolean } | null = null;
  cat: { id: number | null; name: string; kind: string; active: boolean } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  fund: FundBalance | null = null;

  /** The book this page shows: REB's (the only one; set from the route). */
  @Input() book: 'reb' = 'reb';
  get base(): string { return `/admin/books/${this.book}`; }

  get canEdit(): boolean { return this.auth.can('REB_FINANCE', 'EDIT'); }

  loadBalance(): void {
    this.api.get<FundBalance>(`${this.base}/balance`).subscribe({ next: (r) => (this.fund = r), error: () => (this.fund = null) });
  }
  get isIncome(): boolean { return this.categories.find((c) => c.id === this.entry?.categoryId)?.kind === 'INCOME'; }
  categoriesOf(kind: string): Category[] { return this.categories.filter((c) => c.kind === kind); }
  activeOf(kind: string): Category[] { return this.categories.filter((c) => c.kind === kind && (c.active || c.id === this.entry?.categoryId)); }
  share(v: number, total: number): number { return total ? Math.round((Number(v) / Number(total)) * 100) : 0; }

  ngOnInit(): void {
    this.loadCategories();
    this.loadPayees();
    this.api.get<ChapterOption[]>(`${this.base}/chapters`).subscribe({ next: (r) => (this.chapters = r), error: () => (this.chapters = []) });
    this.load();
    this.loadBalance();
    this.api.get<{ id: number; name: string }[]>(`${this.base}/projects`).subscribe({ next: (r) => (this.projects = r), error: () => (this.projects = []) });
  }

  loadCategories(): void {
    this.api.get<Category[]>(`${this.base}/categories`).subscribe({ next: (r) => (this.categories = r), error: (e) => (this.error = errorText(e, 'Could not load the categories.')) });
  }

  ngOnDestroy(): void { this.table.destroy(); }

  /** Income and expense of every entry that matches the filters (all pages). */
  get totals(): { income: number; expense: number } {
    return { income: Number(this.table.last?.income ?? 0), expense: Number(this.table.last?.expense ?? 0) };
  }

  load(): void { this.table.load(); }

  loadSummary(): void {
    this.api.get<Summary>(`${this.base}/summary`, { year: this.year }).subscribe({ next: (r) => (this.summary = r), error: (e) => (this.error = errorText(e, 'Could not load the summary.')) });
  }

  openEntry(e?: Entry): void {
    this.entry = { id: e?.id ?? null, entryDate: e?.entry_date ?? this.today, categoryId: e?.category_id ?? null, projectId: e?.project_id ?? null,
      amount: e?.amount ?? null, party: e?.party ?? '', description: e?.description ?? '', method: e?.method ?? 'BANK', reference: e?.reference ?? '',
      who: e ? (e.chapter_id ?? e.payee_id ?? (e.party ? 'other' : null)) : null };
  }

  saveEntry(): void {
    const e = this.entry;
    if (!e) return;
    this.busy = true;
    // A chapter (income) or someone on the list (expense); otherwise the name typed in.
    const picked = typeof e.who === 'number' ? e.who : null;
    const body = { ...e, chapterId: this.isIncome ? picked : null, payeeId: this.isIncome ? null : picked, party: e.who === 'other' ? e.party : '' };
    const req = e.id ? this.api.put<{ message: string }>(`${this.base}/entries/${e.id}`, body) : this.api.post<{ message: string }>(`${this.base}/entries`, body);
    req.subscribe({
      next: (r) => { this.busy = false; this.entry = null; this.message = r.message; this.load(); this.loadBalance(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save the entry.'); }
    });
  }

  /** A different kind (income <-> expense): the chapter / payee chosen no longer fits. */
  pickCategory(id: number | null): void {
    if (!this.entry) return;
    const kindOf = (c: number | null) => this.categories.find((x) => x.id === c)?.kind;
    if (kindOf(id) !== kindOf(this.entry.categoryId) && this.entry.who !== 'other') this.entry.who = null;
    this.entry.categoryId = id;
  }

  get activePayees(): Payee[] {
    return this.payees.filter((p) => p.active || (this.entry && p.id === this.entry.who));
  }
  get activeChapters(): ChapterOption[] {
    return this.chapters.filter((c) => c.active || (this.entry && c.id === this.entry.who));
  }

  loadPayees(): void {
    this.api.get<Payee[]>(`${this.base}/payees`).subscribe({ next: (r) => (this.payees = r), error: () => (this.payees = []) });
  }

  savePayee(): void {
    const p = this.payee;
    if (!p) return;
    this.busy = true;
    const req = p.id ? this.api.put<{ message: string }>(`${this.base}/payees/${p.id}`, p) : this.api.post<{ message: string }>(`${this.base}/payees`, p);
    req.subscribe({
      next: (r) => { this.busy = false; this.payee = null; this.message = r.message; this.loadPayees(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save it.'); }
    });
  }

  deletePayee(p: Payee): void {
    if (!confirm(`Remove ${p.name} from the Paid to list?`)) return;
    this.api.delete<{ message: string }>(`${this.base}/payees/${p.id}`).subscribe({
      next: (r) => { this.message = r.message; this.loadPayees(); },
      error: (err) => (this.error = errorText(err, 'Could not remove it.'))
    });
  }

  deleteEntry(e: Entry): void {
    if (!confirm(`Delete this entry of ₹${e.amount}?`)) return;
    this.api.delete<{ message: string }>(`${this.base}/entries/${e.id}`).subscribe({ next: (r) => { this.message = r.message; this.load(); this.loadBalance(); }, error: (err) => (this.error = errorText(err, 'Could not delete it.')) });
  }

  saveCategory(): void {
    const c = this.cat;
    if (!c) return;
    this.busy = true;
    const req = c.id ? this.api.put<{ message: string }>(`${this.base}/categories/${c.id}`, c) : this.api.post<{ message: string }>(`${this.base}/categories`, c);
    req.subscribe({
      next: (r) => { this.busy = false; this.cat = null; this.message = r.message; this.loadCategories(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the category.'); }
    });
  }

  deleteCategory(c: Category): void {
    if (!confirm(`Delete ${c.name}?`)) return;
    this.api.delete<{ message: string }>(`${this.base}/categories/${c.id}`).subscribe({ next: (r) => { this.message = r.message; this.loadCategories(); }, error: (e) => (this.error = errorText(e, 'Could not delete it.')) });
  }

  publish(): void {
    const note = prompt(`Show the ${this.year} summary on the website. A short note for visitors (optional):`, '');
    if (note === null) return;
    this.api.post<{ message: string }>(`${this.base}/published`, { year: this.year, note }).subscribe({
      next: (r) => { this.message = r.message; this.loadSummary(); }, error: (e) => (this.error = errorText(e, 'Could not publish it.'))
    });
  }

  unpublish(): void {
    this.api.delete<{ message: string }>(`${this.base}/published/${this.year}`).subscribe({
      next: (r) => { this.message = r.message; this.loadSummary(); }, error: (e) => (this.error = errorText(e, 'Could not take it off.'))
    });
  }
}
