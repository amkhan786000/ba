import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { APPLICATION_STATUSES, ApplicationRow } from './admin-applications.component';

/** Port of templates/application_details.html */
@Component({
  selector: 'app-admin-application-details',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row justify-content-center mt-4">
      <div class="col-lg-8 col-md-10">
        <div class="text-center mb-4">
          <span style="display: inline-block; background-color: grey; color: white; font-weight: bold; padding: 10px; border-radius: 4px;">APPLICATION DETAILS</span>
        </div>
        <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
        <div class="card" *ngIf="app">
          <div class="card-body">
            <h4 class="header-title mb-3">Applicant Information</h4>
            <table class="table table-striped table-bordered">
              <tbody>
                <tr><th>Applicant Name</th><td>{{ app.name }}</td></tr>
                <tr><th>Father's Name</th><td>{{ app.father_name }}</td></tr>
                <tr><th>Mother's Name</th><td>{{ app.mother_name }}</td></tr>
                <tr><th>RCC Center</th><td>{{ app.rcc_name }}</td></tr>
                <tr><th>Course Applied</th><td>{{ app.course_applied }}</td></tr>
                <tr><th>Annual Income</th><td>{{ app.average_annual_salary != null ? '₹' + (app.average_annual_salary | number: '1.2-2') : 'N/A' }}</td></tr>
                <tr><th>Application Status</th><td><span class="badge" [ngClass]="badge(app.status)">{{ app.status }}</span></td></tr>
              </tbody>
            </table>

            <h4 class="header-title mt-4 mb-3">Update Status</h4>
            <form (ngSubmit)="save()">
              <div class="form-group">
                <label for="status">Change Status</label>
                <select name="status" id="status" class="form-control" [(ngModel)]="status" required>
                  <option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
                </select>
              </div>
              <div class="form-group">
                <label for="comments">Comments</label>
                <textarea name="comments" id="comments" class="form-control" rows="3" [(ngModel)]="comments"></textarea>
              </div>
              <button type="submit" class="btn btn-success btn-block" [disabled]="saving || !status">Update Status</button>
            </form>
            <a routerLink="/admin/applications" class="btn btn-secondary btn-block mt-3">Back to Applications</a>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminApplicationDetailsComponent implements OnInit {
  @Input() id = '';
  readonly statuses = APPLICATION_STATUSES;
  app: ApplicationRow | null = null;
  status = '';
  comments = '';
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<ApplicationRow[]>('/admin/applications').subscribe({
      next: (list) => {
        this.app = list.find((a) => String(a.grantee_detail_id) === this.id) ?? null;
        if (!this.app) { this.error = 'Application not found!'; return; }
        this.status = this.app.status ?? '';
        this.comments = this.app.comments ?? '';
      },
      error: (e) => (this.error = errorText(e, 'Could not load the application.'))
    });
  }

  badge(status: string | null): string {
    const s = (status ?? '').toLowerCase();
    if (s.includes('accepted') || s.includes('admitted')) return 'badge-success';
    if (s.includes('rejected')) return 'badge-danger';
    if (s.includes('submitted') || s.includes('interviewing')) return 'badge-primary';
    if (s.includes('on hold')) return 'badge-warning';
    return 'badge-info';
  }

  save(): void {
    this.saving = true;
    this.api.post<{ message: string }>(`/admin/applications/${this.id}/status`, { status: this.status, comments: this.comments }).subscribe({
      next: () => { this.saving = false; this.message = 'Application status updated successfully!'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Error updating application.'); }
    });
  }
}
