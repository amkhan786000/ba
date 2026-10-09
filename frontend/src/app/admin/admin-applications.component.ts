import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

export interface ApplicationRow {
  grantee_detail_id: number;
  name: string;
  father_name: string | null;
  mother_name: string | null;
  rcc_name: string | null;
  course_applied: string | null;
  average_annual_salary?: number | null;
  status: string | null;
  comments: string | null;
}

export const APPLICATION_STATUSES = [
  { value: 'draft', label: 'Draft' },
  { value: 'submitted', label: 'Submitted' },
  { value: 'interviewing', label: 'Interviewing' },
  { value: 'accepted', label: 'Accepted' },
  { value: 'waitlisted', label: 'Waitlisted' },
  { value: 'on hold', label: 'On Hold' },
  { value: 'provisional admission letter', label: 'Provisional Admission Letter Issued' },
  { value: 'admitted', label: 'Admitted' },
  { value: 'rejected', label: 'Rejected' }
];

/** Port of templates/applications.html */
@Component({
  selector: 'app-admin-applications',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
          <h4 class="page-title mb-2 mb-md-0">Applications</h4>
          <a *ngIf="section === 'admin'" routerLink="/admin/applications/ranking" class="btn btn-outline-primary"><i class="mdi mdi-trophy-outline mr-1"></i>Interview ranking</a>
        </div>
      </div>
    </div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row justify-content-center mt-4">
      <div class="col-md-10">
        <div class="text-center mb-4">
          <span style="display: inline-block; background-color: grey; color: white; font-weight: bold; padding: 10px; border-radius: 4px;">
            RAHBAR SCHOLARSHIP APPLICATIONS
          </span>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Filter Applications</h4>
            <div class="row">
              <div class="col-md-4 mb-2"><input type="text" class="form-control" placeholder="Enter Applicant Name" [(ngModel)]="fName"></div>
              <div class="col-md-4 mb-2">
                <select class="form-control" [(ngModel)]="fStatus">
                  <option value="">Select Status</option>
                  <option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
                </select>
              </div>
              <div class="col-md-4 mb-2"><input type="text" class="form-control" placeholder="Type RCC Center" [(ngModel)]="fRcc"></div>
            </div>
            <div class="row mt-3">
              <div class="col-md-12 text-center">
                <button type="button" class="btn btn-primary mr-1" (click)="filter()">Apply Filters</button>
                <button type="button" class="btn btn-secondary mr-1" (click)="reset()">Clear</button>
                <button type="button" class="btn btn-success" (click)="downloadCsv()">Download CSV</button>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Application List</h4>
            <div class="table-responsive">
              <table class="table table-bordered table-hover">
                <thead class="thead-dark"><tr><th>ID</th><th>Applicant Name</th><th>RCC Center</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>
                  <tr *ngIf="loading && !shown.length"><td colspan="5" class="text-center text-muted">Loading…</td></tr>
                  <tr *ngIf="!loading && !shown.length">
                    <td colspan="5" class="text-center">{{ filtered ? 'No matching applications found for the current filters.' : 'No applications found.' }}</td>
                  </tr>
                  <tr *ngFor="let a of shown">
                    <td>{{ a.grantee_detail_id }}</td>
                    <td>{{ a.name }}</td>
                    <td>{{ a.rcc_name }}</td>
                    <td>{{ a.status }}</td>
                    <td><a [routerLink]="['/' + section, 'applications', a.grantee_detail_id]" class="btn btn-primary btn-sm">View Details</a></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="total" (pageChange)="load()"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminApplicationsComponent implements OnInit {
  /** Which area this page is shown in ('admin' or 'coordinator'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';
  readonly statuses = APPLICATION_STATUSES;
  /** Current page of applications (the server pages and filters them, newest first). */
  shown: ApplicationRow[] = [];
  total = 0;
  readonly pg = new PageState(10);
  loading = false;
  fName = '';
  fStatus = '';
  fRcc = '';
  /** Filters as of the last "Apply Filters" (typing alone doesn't change the list). */
  private applied = { name: '', status: '', rcc: '' };
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  get filtered(): boolean { return !!(this.applied.name || this.applied.status || this.applied.rcc); }

  private params(page: number, size: number): Record<string, unknown> {
    return { page, size, ...this.applied };
  }

  load(): void {
    this.loading = true;
    this.api.get<{ data: ApplicationRow[]; total: number }>('/admin/applications', this.params(this.pg.page, this.pg.size)).subscribe({
      next: (r) => { this.loading = false; this.shown = r.data; this.total = r.total; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load applications.'); }
    });
  }

  filter(): void {
    this.applied = { name: this.fName.trim(), status: this.fStatus, rcc: this.fRcc.trim() };
    this.pg.reset();
    this.load();
  }

  reset(): void { this.fName = ''; this.fStatus = ''; this.fRcc = ''; this.filter(); }

  /** Downloads every application matching the filters (not just the current page). */
  downloadCsv(): void {
    const size = 1000;
    const all: ApplicationRow[] = [];
    const fetchPage = (page: number): void => {
      this.api.get<{ data: ApplicationRow[]; total: number }>('/admin/applications', this.params(page, size)).subscribe({
        next: (r) => {
          all.push(...r.data);
          if (all.length < r.total && r.data.length === size) fetchPage(page + 1);
          else this.saveCsv(all);
        },
        error: (e) => (this.error = errorText(e, 'Could not download applications.'))
      });
    };
    fetchPage(1);
  }

  private saveCsv(rows: ApplicationRow[]): void {
    if (!rows.length) { this.error = 'No data to download based on current filters.'; return; }
    const q = (v: unknown) => '"' + String(v ?? '').trim().replace(/"/g, '""') + '"';
    const csv = 'ID,Applicant Name,RCC Center,Status\n' +
      rows.map((a) => [a.grantee_detail_id, a.name, a.rcc_name, a.status].map(q).join(',')).join('\n') + '\n';
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'applications.csv';
    link.click();
    URL.revokeObjectURL(url);
  }
}
