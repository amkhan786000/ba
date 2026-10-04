import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

/** Port of templates/admin/generate_reports.html */
@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Generate Reports</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Select Report Type</h4>
            <form (ngSubmit)="generate()">
              <div class="form-group">
                <label for="reportType">Report Type</label>
                <select class="form-control" id="reportType" name="reportType" [(ngModel)]="reportType" required>
                  <option value="applications">Applications</option>
                  <option value="payments">Payments</option>
                  <option value="sponsors_convenors">Sponsors &amp; Convenors</option>
                  <option value="grantees">Student</option>
                </select>
              </div>
              <div class="form-group">
                <label for="format">Format</label>
                <select class="form-control" id="format" name="format" [(ngModel)]="format" required>
                  <option value="csv">CSV</option>
                  <option value="excel">Excel</option>
                  <option value="pdf">PDF</option>
                </select>
              </div>
              <div class="text-right">
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="busy">
                  {{ busy ? 'Generating…' : 'Generate Report' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminReportsComponent {
  reportType = 'applications';
  format = 'csv';
  busy = false;
  error = '';

  constructor(private api: ApiService) {}

  generate(): void {
    this.busy = true;
    this.error = '';
    this.api.download(`/admin/reports/${this.reportType}`, { format: this.format }, `${this.reportType}_report`).subscribe({
      next: () => (this.busy = false),
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not generate the report.'); }
    });
  }
}
