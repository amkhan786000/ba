import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { asDate } from '../shared/format';
import { CardTableDirective } from '../shared/card-table.directive';

interface ReportDef { key: string; category: string; title: string; description: string; dateRange: boolean }
interface ReportData { key: string; title: string; columns: string[]; rows: Record<string, unknown>[] }
type Format = 'csv' | 'excel' | 'pdf';

const CATEGORY_ICONS: Record<string, string> = {
  Applications: 'mdi-file-document-outline',
  Students: 'mdi-school',
  'Student breakdowns': 'mdi-chart-pie',
  Sponsors: 'mdi-hand-heart',
  Payments: 'mdi-cash-multiple',
  Setup: 'mdi-cog-outline',
  System: 'mdi-history'
};

/**
 * Admin > Reports: every report as a card. "View" shows the report on screen (search, sort, pages);
 * CSV / Excel / PDF download it. The optional date range applies to both.
 */
@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  styles: [`
    .report-actions { display: flex; flex-wrap: wrap; gap: .35rem; align-items: center; }
    .report-table th { white-space: nowrap; cursor: pointer; user-select: none; }
    .report-table td { white-space: nowrap; max-width: 320px; overflow: hidden; text-overflow: ellipsis; }
    .modal-body { min-height: 300px; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Reports</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="card report-toolbar">
      <div class="card-body d-flex flex-column flex-lg-row align-items-lg-end">
        <div class="form-group mb-lg-0 mr-lg-3 flex-fill">
          <label for="q">Find a report</label>
          <input id="q" class="form-control" [(ngModel)]="q" placeholder="e.g. overdue, sponsor, progress">
        </div>
        <div class="form-group mb-lg-0 mr-lg-3">
          <label for="from">From</label>
          <input id="from" type="date" class="form-control" [(ngModel)]="from">
        </div>
        <div class="form-group mb-0">
          <label for="to">To</label>
          <input id="to" type="date" class="form-control" [(ngModel)]="to">
        </div>
      </div>
      <div class="card-body pt-0 small text-muted">
        The date range applies to reports marked <span class="badge badge-info">Date range</span>; leave it empty for everything.
      </div>
    </div>

    <ng-container *ngFor="let group of groups">
      <h5 class="report-group"><i class="mdi" [ngClass]="icon(group.category)"></i>{{ group.category }}</h5>
      <div class="row">
        <div class="col-12 col-md-6 col-xl-4" *ngFor="let r of group.items">
          <div class="card report-card">
            <div class="card-body">
              <div class="d-flex justify-content-between align-items-start">
                <h6 class="report-title">{{ r.title }}</h6>
                <span *ngIf="r.dateRange" class="badge badge-info">Date range</span>
              </div>
              <p class="text-muted small mb-3">{{ r.description }}</p>
              <div class="report-actions">
                <button class="btn btn-sm btn-primary" (click)="view(r)" [disabled]="loading === r.key">
                  <span *ngIf="loading === r.key" class="spinner-border spinner-border-sm mr-1"></span>
                  <i *ngIf="loading !== r.key" class="mdi mdi-eye-outline"></i> View
                </button>
                <span class="text-muted small ml-1">Download:</span>
                <button *ngFor="let f of formats" class="btn btn-sm btn-outline-secondary" (click)="download(r, f.value)"
                        [disabled]="busy === r.key + f.value" [title]="'Download as ' + f.label">
                  <span *ngIf="busy === r.key + f.value" class="spinner-border spinner-border-sm"></span>
                  <i *ngIf="busy !== r.key + f.value" class="mdi" [ngClass]="f.icon"></i> {{ f.label }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </ng-container>
    <p *ngIf="!groups.length && reports.length" class="text-muted">No report matches "{{ q }}".</p>

    <!-- On-screen view of one report -->
    <ng-container *ngIf="viewing && data">
      <div class="modal fade show d-block" tabindex="-1" role="dialog" (click)="close()">
        <div class="modal-dialog modal-xl modal-dialog-scrollable" role="document" (click)="$event.stopPropagation()">
          <div class="modal-content">
            <div class="modal-header">
              <div>
                <h5 class="modal-title mb-0">{{ data.title }}</h5>
                <small class="text-muted">
                  {{ data.rows.length }} row(s)<ng-container *ngIf="viewing.dateRange && (from || to)"> · {{ from || 'start' }} to {{ to || 'today' }}</ng-container>
                </small>
              </div>
              <button type="button" class="close" (click)="close()"><span>&times;</span></button>
            </div>
            <div class="modal-body">
              <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
                <input class="form-control mb-2 mb-md-0 mr-md-3" style="max-width: 320px" [(ngModel)]="filter" (ngModelChange)="pg.reset()"
                       placeholder="Search in this report" [disabled]="!data.rows.length">
                <div class="report-actions">
                  <span class="text-muted small">Download:</span>
                  <button *ngFor="let f of formats" class="btn btn-sm btn-outline-primary" (click)="download(viewing, f.value)"
                          [disabled]="!data.rows.length || busy === viewing.key + f.value">
                    <span *ngIf="busy === viewing.key + f.value" class="spinner-border spinner-border-sm"></span>
                    <i *ngIf="busy !== viewing.key + f.value" class="mdi" [ngClass]="f.icon"></i> {{ f.label }}
                  </button>
                </div>
              </div>

              <p *ngIf="!data.rows.length" class="text-center text-muted my-5">No data for this report{{ viewing.dateRange && (from || to) ? ' in the selected dates' : '' }}.</p>

              <ng-container *ngIf="data.rows.length">
                <div class="table-responsive">
                  <table class="table table-sm table-striped table-hover report-table mb-0">
                    <thead class="thead-light">
                      <tr>
                        <th *ngFor="let c of data.columns" (click)="sortBy(c)" [title]="'Sort by ' + header(c)">
                          {{ header(c) }}
                          <i *ngIf="sort === c" class="mdi" [ngClass]="desc ? 'mdi-arrow-down' : 'mdi-arrow-up'"></i>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr *ngIf="!shown.length"><td [attr.colspan]="data.columns.length" class="text-center text-muted">No row matches "{{ filter }}".</td></tr>
                      <tr *ngFor="let row of shown | paginate: pg.page : pg.size">
                        <td *ngFor="let c of data.columns" [title]="cell(row[c])">{{ cell(row[c]) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <app-pager [state]="pg" [total]="shown.length"></app-pager>
              </ng-container>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-backdrop fade show"></div>
    </ng-container>
  `
})
export class AdminReportsComponent implements OnInit {
  readonly formats: { value: Format; label: string; icon: string }[] = [
    { value: 'csv', label: 'CSV', icon: 'mdi-file-delimited-outline' },
    { value: 'excel', label: 'Excel', icon: 'mdi-file-excel-outline' },
    { value: 'pdf', label: 'PDF', icon: 'mdi-file-pdf-outline' }
  ];
  readonly pg = new PageState(25);
  reports: ReportDef[] = [];
  q = '';
  from = '';
  to = '';
  /** Report key + format being downloaded, e.g. "paymentscsv". */
  busy = '';
  /** Report key being loaded for viewing. */
  loading = '';
  error = '';

  viewing: ReportDef | null = null;
  data: ReportData | null = null;
  filter = '';
  sort = '';
  desc = false;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<ReportDef[]>('/admin/reports').subscribe({
      next: (r) => (this.reports = r),
      error: (e) => (this.error = errorText(e, 'Could not load the list of reports.'))
    });
  }

  get groups(): { category: string; items: ReportDef[] }[] {
    const q = this.q.trim().toLowerCase();
    const out: { category: string; items: ReportDef[] }[] = [];
    for (const r of this.reports) {
      if (q && !(r.title + ' ' + r.description + ' ' + r.category).toLowerCase().includes(q)) continue;
      let g = out.find((x) => x.category === r.category);
      if (!g) out.push((g = { category: r.category, items: [] }));
      g.items.push(r);
    }
    return out;
  }

  icon(category: string): string { return CATEGORY_ICONS[category] ?? 'mdi-file-chart'; }

  private params(r: ReportDef): Record<string, string> {
    return r.dateRange ? { from: this.from, to: this.to } : {};
  }

  view(r: ReportDef): void {
    this.loading = r.key;
    this.error = '';
    this.api.get<ReportData>(`/admin/reports/${r.key}/data`, this.params(r)).subscribe({
      next: (d) => {
        this.loading = '';
        this.viewing = r;
        this.data = d;
        this.filter = '';
        this.sort = '';
        this.desc = false;
        this.pg.reset();
      },
      error: (e) => { this.loading = ''; this.error = errorText(e, 'Could not load the report.'); }
    });
  }

  close(): void { this.viewing = null; this.data = null; }

  download(r: ReportDef, format: Format): void {
    this.busy = r.key + format;
    this.error = '';
    this.api.download(`/admin/reports/${r.key}`, { format, ...this.params(r) }, `${r.key}_report`).subscribe({
      next: () => (this.busy = ''),
      error: (e) => { this.busy = ''; this.error = errorText(e, 'Could not generate the report.'); }
    });
  }

  /** Rows matching the search, in the chosen sort order. */
  get shown(): Record<string, unknown>[] {
    if (!this.data) return [];
    const f = this.filter.trim().toLowerCase();
    let rows = f
      ? this.data.rows.filter((row) => this.data!.columns.some((c) => this.cell(row[c]).toLowerCase().includes(f)))
      : this.data.rows;
    if (this.sort) {
      const c = this.sort, dir = this.desc ? -1 : 1;
      rows = [...rows].sort((a, b) => dir * compare(a[c], b[c]));
    }
    return rows;
  }

  sortBy(column: string): void {
    if (this.sort === column) this.desc = !this.desc;
    else { this.sort = column; this.desc = false; }
    this.pg.reset();
  }

  /** "assigned_sponsor_name" -> "Assigned sponsor name". */
  header(column: string): string {
    const text = column.replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  /** Dates and times in the local format; empty values blank. */
  cell(v: unknown): string {
    if (v === null || v === undefined) return '';
    if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(v)) {
      const d = asDate(v);
      return d ? d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : v;
    }
    if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v)) return asDate(v)?.toLocaleDateString() ?? v;
    return String(v);
  }
}

/** Numbers by value, everything else as text (empty values first). */
function compare(a: unknown, b: unknown): number {
  if (a === b) return 0;
  if (a === null || a === undefined || a === '') return -1;
  if (b === null || b === undefined || b === '') return 1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
}
