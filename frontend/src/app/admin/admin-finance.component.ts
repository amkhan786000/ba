import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { isoDate } from '../shared/format';

interface Category { id: number; name: string; kind: string; active: boolean }
interface Entry {
  id: number; entry_date: string; kind: string; category_id: number; category_name: string; project_id: number | null; project_name: string | null;
  amount: number; party: string | null; description: string | null; method: string; reference: string | null; entered_by: string | null;
}
interface Line { name: string; amount: number }
interface Summary {
  year: number; income_by_category: Line[]; expense_by_category: Line[]; expense_by_project: Line[]; income: number; expense: number;
  scholarships_paid: number; reb_fees_collected: number; published: boolean; note?: string;
}

const METHODS: Record<string, string> = { CASH: 'Cash', UPI: 'UPI', BANK: 'Bank transfer', CHEQUE: 'Cheque' };

/**
 * Administration > Income & Expenses: record money in and out by category (and project), see a year's summary
 * (which also counts sponsors' scholarship payments and REB fees recorded elsewhere in Rahbar), and choose whether
 * that summary is shown on biharanjuman.com.
 */
@Component({
  selector: 'app-admin-finance',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective, PagerComponent, PaginatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Income &amp; Expenses</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'entries'" (click)="$event.preventDefault(); tab = 'entries'">Entries</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'summary'" (click)="$event.preventDefault(); tab = 'summary'; loadSummary()">Yearly summary</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'categories'" (click)="$event.preventDefault(); tab = 'categories'">Categories</a></li>
    </ul>

    <!-- entries -->
    <div class="card" *ngIf="tab === 'entries'"><div class="card-body">
      <div class="row align-items-end">
        <div class="col-6 col-md-2 form-group"><label for="f-from">From</label><input id="f-from" type="date" class="form-control" [(ngModel)]="from" (ngModelChange)="load()"></div>
        <div class="col-6 col-md-2 form-group"><label for="f-to">To</label><input id="f-to" type="date" class="form-control" [(ngModel)]="to" (ngModelChange)="load()"></div>
        <div class="col-6 col-md-2 form-group"><label for="f-kind">Kind</label>
          <select id="f-kind" class="form-control" [(ngModel)]="kind" (ngModelChange)="load()"><option value="">Both</option><option value="INCOME">Income</option><option value="EXPENSE">Expenses</option></select></div>
        <div class="col-6 col-md-3 form-group"><label for="f-cat">Category</label>
          <select id="f-cat" class="form-control" [(ngModel)]="categoryId" (ngModelChange)="load()"><option [ngValue]="null">All</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }} ({{ c.kind === 'INCOME' ? 'in' : 'out' }})</option></select></div>
        <div class="col-12 col-md-3 form-group text-md-right"><button *ngIf="canEdit" class="btn btn-primary" (click)="openEntry()"><i class="mdi mdi-plus mr-1"></i>Add entry</button></div>
      </div>
      <p class="mb-2">Income <strong class="text-success">₹{{ totals.income | number: '1.0-2' }}</strong> · Expenses <strong class="text-danger">₹{{ totals.expense | number: '1.0-2' }}</strong>
        · Balance <strong>₹{{ totals.income - totals.expense | number: '1.0-2' }}</strong></p>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Date</th><th>Category</th><th>Project</th><th>Paid to / from</th><th class="text-right">Amount</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="6" class="text-center text-muted">No entries.</td></tr>
          <tr *ngFor="let e of rows | paginate: pg.page : pg.size">
            <td class="text-nowrap">{{ e.entry_date | date: 'd MMM yyyy' }}</td>
            <td>{{ e.category_name }}<div class="small text-muted">{{ e.description }}</div></td>
            <td>{{ e.project_name || '--' }}</td>
            <td>{{ e.party || '--' }}<div class="small text-muted">{{ methods[e.method] }}{{ e.reference ? ' · ' + e.reference : '' }}</div></td>
            <td class="text-right text-nowrap" [class.text-success]="e.kind === 'INCOME'" [class.text-danger]="e.kind === 'EXPENSE'">{{ e.kind === 'INCOME' ? '+' : '−' }}₹{{ e.amount | number: '1.0-2' }}</td>
            <td class="text-right text-nowrap"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="openEntry(e)">Edit</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="deleteEntry(e)">Delete</button></td>
          </tr>
        </tbody>
      </table></div>
      <app-pager [state]="pg" [total]="rows.length"></app-pager>
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
              <tr><td>REB fees collected <span class="small text-muted">(REB Fees)</span></td><td class="text-right">₹{{ s.reb_fees_collected | number: '1.0-2' }}</td></tr>
            </tbody></table>
          </div></div>
        </div>
        <div class="col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Money out</h4>
            <table class="table table-sm mb-0"><tbody>
              <tr *ngFor="let l of s.expense_by_category"><td>{{ l.name }}</td><td class="text-right">₹{{ l.amount | number: '1.0-2' }}</td></tr>
              <tr class="font-weight-bold"><td>Total recorded here</td><td class="text-right">₹{{ s.expense | number: '1.0-2' }}</td></tr>
              <tr><td>Scholarships paid by sponsors to students <span class="small text-muted">(Payment Records)</span></td><td class="text-right">₹{{ s.scholarships_paid | number: '1.0-2' }}</td></tr>
            </tbody></table>
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

    <div *ngIf="entry" class="modal fade show d-block" tabindex="-1" (click)="entry = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ entry.id ? 'Edit entry' : 'Add entry' }}</h5><button type="button" class="close" (click)="entry = null" aria-label="Close">&times;</button></div>
          <div class="modal-body"><div class="row">
            <div class="col-6 form-group"><label for="e-date">Date *</label><input id="e-date" type="date" class="form-control" [max]="today" [(ngModel)]="entry.entryDate"></div>
            <div class="col-6 form-group"><label for="e-amount">Amount (₹) *</label><input id="e-amount" type="number" min="0" step="0.01" class="form-control" [(ngModel)]="entry.amount"></div>
            <div class="col-12 form-group"><label for="e-cat">Category *</label>
              <select id="e-cat" class="form-control" [(ngModel)]="entry.categoryId"><option [ngValue]="null">-- Choose --</option>
                <optgroup label="Income"><option *ngFor="let c of activeOf('INCOME')" [ngValue]="c.id">{{ c.name }}</option></optgroup>
                <optgroup label="Expenses"><option *ngFor="let c of activeOf('EXPENSE')" [ngValue]="c.id">{{ c.name }}</option></optgroup></select></div>
            <div class="col-12 form-group"><label for="e-project">Project</label>
              <select id="e-project" class="form-control" [(ngModel)]="entry.projectId"><option [ngValue]="null">General (the organisation)</option>
                <option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}</option></select></div>
            <div class="col-12 form-group"><label for="e-party">{{ isIncome ? 'Received from' : 'Paid to' }}</label><input id="e-party" class="form-control" [(ngModel)]="entry.party"></div>
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
export class AdminFinanceComponent implements OnInit {
  readonly pg = new PageState(25);
  readonly methods = METHODS;
  readonly methodKeys = Object.keys(METHODS);
  readonly today = isoDate(new Date());
  readonly years = Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i);
  tab: 'entries' | 'summary' | 'categories' = 'entries';
  categories: Category[] = [];
  projects: { id: number; name: string }[] = [];
  rows: Entry[] = [];
  totals = { income: 0, expense: 0 };
  from = `${new Date().getFullYear()}-01-01`;
  to = `${new Date().getFullYear()}-12-31`;
  kind = '';
  categoryId: number | null = null;
  year = new Date().getFullYear();
  summary: Summary | null = null;
  entry: { id: number | null; entryDate: string; categoryId: number | null; projectId: number | null; amount: number | null; party: string;
    description: string; method: string; reference: string } | null = null;
  cat: { id: number | null; name: string; kind: string; active: boolean } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('FINANCE', 'EDIT'); }
  get isIncome(): boolean { return this.categories.find((c) => c.id === this.entry?.categoryId)?.kind === 'INCOME'; }
  categoriesOf(kind: string): Category[] { return this.categories.filter((c) => c.kind === kind); }
  activeOf(kind: string): Category[] { return this.categories.filter((c) => c.kind === kind && (c.active || c.id === this.entry?.categoryId)); }
  share(v: number, total: number): number { return total ? Math.round((Number(v) / Number(total)) * 100) : 0; }

  ngOnInit(): void {
    this.loadCategories();
    this.load();
    this.api.get<{ id: number; name: string }[]>('/admin/finance/projects').subscribe({ next: (r) => (this.projects = r), error: () => (this.projects = []) });
  }

  loadCategories(): void {
    this.api.get<Category[]>('/admin/finance/categories').subscribe({ next: (r) => (this.categories = r), error: (e) => (this.error = errorText(e, 'Could not load the categories.')) });
  }

  load(): void {
    this.api.get<{ entries: Entry[]; income: number; expense: number }>('/admin/finance/entries',
      { from: this.from, to: this.to, kind: this.kind, categoryId: this.categoryId }).subscribe({
      next: (r) => { this.rows = r.entries; this.totals = { income: Number(r.income), expense: Number(r.expense) }; this.pg.reset(); },
      error: (e) => (this.error = errorText(e, 'Could not load the entries.'))
    });
  }

  loadSummary(): void {
    this.api.get<Summary>('/admin/finance/summary', { year: this.year }).subscribe({ next: (r) => (this.summary = r), error: (e) => (this.error = errorText(e, 'Could not load the summary.')) });
  }

  openEntry(e?: Entry): void {
    this.entry = { id: e?.id ?? null, entryDate: e?.entry_date ?? this.today, categoryId: e?.category_id ?? null, projectId: e?.project_id ?? null,
      amount: e?.amount ?? null, party: e?.party ?? '', description: e?.description ?? '', method: e?.method ?? 'BANK', reference: e?.reference ?? '' };
  }

  saveEntry(): void {
    const e = this.entry;
    if (!e) return;
    this.busy = true;
    const req = e.id ? this.api.put<{ message: string }>(`/admin/finance/entries/${e.id}`, e) : this.api.post<{ message: string }>('/admin/finance/entries', e);
    req.subscribe({
      next: (r) => { this.busy = false; this.entry = null; this.message = r.message; this.load(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save the entry.'); }
    });
  }

  deleteEntry(e: Entry): void {
    if (!confirm(`Delete this entry of ₹${e.amount}?`)) return;
    this.api.delete<{ message: string }>(`/admin/finance/entries/${e.id}`).subscribe({ next: (r) => { this.message = r.message; this.load(); }, error: (err) => (this.error = errorText(err, 'Could not delete it.')) });
  }

  saveCategory(): void {
    const c = this.cat;
    if (!c) return;
    this.busy = true;
    const req = c.id ? this.api.put<{ message: string }>(`/admin/finance/categories/${c.id}`, c) : this.api.post<{ message: string }>('/admin/finance/categories', c);
    req.subscribe({
      next: (r) => { this.busy = false; this.cat = null; this.message = r.message; this.loadCategories(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the category.'); }
    });
  }

  deleteCategory(c: Category): void {
    if (!confirm(`Delete ${c.name}?`)) return;
    this.api.delete<{ message: string }>(`/admin/finance/categories/${c.id}`).subscribe({ next: (r) => { this.message = r.message; this.loadCategories(); }, error: (e) => (this.error = errorText(e, 'Could not delete it.')) });
  }

  publish(): void {
    const note = prompt(`Show the ${this.year} summary on the website. A short note for visitors (optional):`, '');
    if (note === null) return;
    this.api.post<{ message: string }>('/admin/finance/published', { year: this.year, note }).subscribe({
      next: (r) => { this.message = r.message; this.loadSummary(); }, error: (e) => (this.error = errorText(e, 'Could not publish it.'))
    });
  }

  unpublish(): void {
    this.api.delete<{ message: string }>(`/admin/finance/published/${this.year}`).subscribe({
      next: (r) => { this.message = r.message; this.loadSummary(); }, error: (e) => (this.error = errorText(e, 'Could not take it off.'))
    });
  }
}
