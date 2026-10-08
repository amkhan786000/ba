import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface AppPreview { grantee_detail_id: number; name: string; student_mobile: string | null; status: string | null }

/** Port of templates/coordinator/generate_reports.html (report download + searchable applications preview). */
@Component({
  selector: 'app-coordinator-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Generate Reports</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-md-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Select Report Type</h4>
            <form (ngSubmit)="generate()">
              <div class="form-group">
                <label for="reportType">Report Type</label>
                <select class="form-control" id="reportType" name="reportType" [(ngModel)]="reportType" required>
                  <option value="applications">Applications Report</option>
                  <option value="sponsors_convenors">Sponsors &amp; Convenors Report</option>
                  <option value="grantees">Student Report</option>
                </select>
              </div>
              <div class="form-group">
                <label for="format">Download Format</label>
                <select class="form-control" id="format" name="format" [(ngModel)]="format" required>
                  <option value="csv">CSV</option>
                  <option value="excel">Excel</option>
                  <option value="pdf">PDF</option>
                </select>
              </div>
              <button type="submit" class="btn btn-primary" [disabled]="busy">{{ busy ? 'Generating…' : 'Generate Report' }}</button>
            </form>
          </div>
        </div>
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-md-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Applications Report Preview</h4>
            <div class="form-group">
              <label for="applicationSearchInput">Search Applications:</label>
              <input type="text" class="form-control" id="applicationSearchInput" placeholder="Search by Name, Contact, or Status..." [(ngModel)]="search">
            </div>
            <div class="table-responsive">
              <table class="table table-bordered">
                <thead><tr><th>ID</th><th>Name</th><th>Contact</th><th>Status</th></tr></thead>
                <tbody>
                  <tr *ngIf="!apps.length"><td colspan="4" class="text-center">No applications to display. Select a report type and generate.</td></tr>
                  <tr *ngIf="apps.length && !shown.length"><td colspan="4" class="text-center">No matching applications found.</td></tr>
                  <tr *ngFor="let a of shown | paginate: pg.page : pg.size">
                    <td>{{ a.grantee_detail_id }}</td><td>{{ a.name }}</td><td>{{ a.student_mobile }}</td><td>{{ a.status }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="shown.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorReportsComponent implements OnInit {
  readonly pg = new PageState();
  reportType = 'applications';
  format = 'csv';
  busy = false;
  error = '';
  apps: AppPreview[] = [];
  search = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<AppPreview[]>('/coordinator/applications').subscribe({ next: (a) => (this.apps = a) });
  }

  get shown(): AppPreview[] {
    const t = this.search.toLowerCase().trim();
    if (!t) return this.apps;
    return this.apps.filter((a) => [a.grantee_detail_id, a.name, a.student_mobile, a.status].some((v) => String(v ?? '').toLowerCase().includes(t)));
  }

  generate(): void {
    this.busy = true;
    this.error = '';
    this.api.download('/coordinator/reports', { reportType: this.reportType, format: this.format }, `${this.reportType}_report`).subscribe({
      next: () => (this.busy = false),
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not generate the report.'); }
    });
  }
}
