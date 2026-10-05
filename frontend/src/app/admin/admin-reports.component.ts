import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

interface ReportDef { key: string; category: string; title: string; description: string; dateRange: boolean }

const CATEGORY_ICONS: Record<string, string> = {
  Applications: 'mdi-file-document-outline',
  Students: 'mdi-school',
  'Student breakdowns': 'mdi-chart-pie',
  Sponsors: 'mdi-hand-heart',
  Payments: 'mdi-cash-multiple',
  Setup: 'mdi-cog-outline',
  System: 'mdi-history'
};

/** Admin > Reports: every report as a card, downloadable as CSV, Excel or PDF, with an optional date range. */
@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
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
        <div class="form-group mb-lg-0 mr-lg-3">
          <label for="to">To</label>
          <input id="to" type="date" class="form-control" [(ngModel)]="to">
        </div>
        <div class="form-group mb-0">
          <label class="d-block">Format</label>
          <div class="segmented mb-0 seg-3">
            <button type="button" *ngFor="let f of formats" [class.active]="format === f.value" (click)="format = f.value">
              <i class="mdi" [ngClass]="f.icon"></i>{{ f.label }}
            </button>
          </div>
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
              <button class="btn btn-sm btn-primary" (click)="download(r)" [disabled]="busy === r.key">
                <span *ngIf="busy === r.key" class="spinner-border spinner-border-sm mr-1"></span>
                <i *ngIf="busy !== r.key" class="mdi mdi-download"></i> Download {{ formatLabel }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </ng-container>
    <p *ngIf="!groups.length && reports.length" class="text-muted">No report matches "{{ q }}".</p>
  `
})
export class AdminReportsComponent implements OnInit {
  readonly formats = [
    { value: 'csv', label: 'CSV', icon: 'mdi-file-delimited-outline' },
    { value: 'excel', label: 'Excel', icon: 'mdi-file-excel-outline' },
    { value: 'pdf', label: 'PDF', icon: 'mdi-file-pdf-outline' }
  ];
  reports: ReportDef[] = [];
  q = '';
  from = '';
  to = '';
  format = 'csv';
  busy = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<ReportDef[]>('/admin/reports').subscribe({
      next: (r) => (this.reports = r),
      error: (e) => (this.error = errorText(e, 'Could not load the list of reports.'))
    });
  }

  get formatLabel(): string { return this.formats.find((f) => f.value === this.format)?.label ?? ''; }

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

  download(r: ReportDef): void {
    this.busy = r.key;
    this.error = '';
    const params = r.dateRange ? { format: this.format, from: this.from, to: this.to } : { format: this.format };
    this.api.download(`/admin/reports/${r.key}`, params, `${r.key}_report`).subscribe({
      next: () => (this.busy = ''),
      error: (e) => { this.busy = ''; this.error = errorText(e, 'Could not generate the report.'); }
    });
  }
}
