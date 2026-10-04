import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

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
  { value: 'on hold', label: 'On Hold' },
  { value: 'provisional admission letter', label: 'Provisional Admission Letter Issued' },
  { value: 'admitted', label: 'Admitted' },
  { value: 'rejected', label: 'Rejected' }
];

/** Port of templates/applications.html */
@Component({
  selector: 'app-admin-applications',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Applications</h4></div></div></div>
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
                  <tr *ngIf="!shown.length">
                    <td colspan="5" class="text-center">{{ apps.length ? 'No matching applications found for the current filters.' : 'No applications found.' }}</td>
                  </tr>
                  <tr *ngFor="let a of shown">
                    <td>{{ a.grantee_detail_id }}</td>
                    <td>{{ a.name }}</td>
                    <td>{{ a.rcc_name }}</td>
                    <td>{{ a.status }}</td>
                    <td><a [routerLink]="['/admin/applications', a.grantee_detail_id]" class="btn btn-primary btn-sm">View Details</a></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminApplicationsComponent implements OnInit {
  readonly statuses = APPLICATION_STATUSES;
  apps: ApplicationRow[] = [];
  shown: ApplicationRow[] = [];
  fName = '';
  fStatus = '';
  fRcc = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<ApplicationRow[]>('/admin/applications').subscribe({
      next: (a) => { this.apps = a; this.shown = a; },
      error: (e) => (this.error = errorText(e, 'Could not load applications.'))
    });
  }

  filter(): void {
    const n = this.fName.toLowerCase(), st = this.fStatus.toLowerCase(), r = this.fRcc.toLowerCase();
    this.shown = this.apps.filter((a) =>
      (a.name ?? '').toLowerCase().includes(n) &&
      (!st || (a.status ?? '').toLowerCase() === st) &&
      (a.rcc_name ?? '').toLowerCase().includes(r));
  }

  reset(): void { this.fName = ''; this.fStatus = ''; this.fRcc = ''; this.shown = this.apps; }

  downloadCsv(): void {
    if (!this.shown.length) { alert('No data to download based on current filters.'); return; }
    const q = (v: unknown) => '"' + String(v ?? '').trim().replace(/"/g, '""') + '"';
    const csv = 'ID,Applicant Name,RCC Center,Status\n' +
      this.shown.map((a) => [a.grantee_detail_id, a.name, a.rcc_name, a.status].map(q).join(',')).join('\n') + '\n';
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'applications.csv';
    link.click();
    URL.revokeObjectURL(url);
  }
}
