import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AlertsComponent, errorText } from '../alerts/alerts.component';
import { AppDatePipe } from '../app-date';

type Row = Record<string, any>;

/** Student and sponsor portals > Volunteer Hours: log the hours you gave (mentoring, tutoring, events...) and get a certificate. */
@Component({
  selector: 'app-portal-volunteer-hours',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex justify-content-between align-items-center">
      <h4 class="page-title">My Volunteer Hours</h4>
      <button class="btn btn-sm btn-outline-primary" (click)="certificate()" [disabled]="!approved"><i class="mdi mdi-certificate"></i> Certificate</button>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p class="text-muted">Mentoring or tutoring a student, helping at an event or a coaching centre? Log your hours here; the office approves
      them and you can download a certificate. <strong>{{ approved }}</strong> approved hour(s) so far.</p>

    <div class="card"><div class="card-body">
      <h4 class="header-title">Log hours</h4>
      <div class="row">
        <div class="col-md-4 form-group"><label>Activity</label><select class="form-control" [(ngModel)]="form['activity']">
          <option *ngFor="let a of activities" [value]="a.key">{{ a.label }}</option></select></div>
        <div class="col-md-3 form-group"><label>Date</label><input type="date" class="form-control" [max]="today" [(ngModel)]="form['workedOn']"></div>
        <div class="col-md-2 form-group"><label>Hours</label><input type="number" min="0.5" max="24" step="0.5" class="form-control" [(ngModel)]="form['hours']"></div>
        <div class="col-md-12 form-group"><label>What you did</label><input class="form-control" maxlength="500" [(ngModel)]="form['description']" placeholder="e.g. Maths tutoring for two Class 10 students"></div>
      </div>
      <button class="btn btn-primary" (click)="save()" [disabled]="busy || !form['hours']">Send</button>
    </div></div>

    <div class="card" *ngIf="rows.length"><div class="card-body">
      <table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Date</th><th>Activity</th><th class="text-right">Hours</th><th>What</th><th>Status</th></tr></thead>
        <tbody><tr *ngFor="let h of rows">
          <td>{{ h['worked_on'] | appDate }}</td><td>{{ h['activity_label'] }}</td><td class="text-right">{{ +h['hours'] }}</td><td class="small">{{ h['description'] }}</td>
          <td><span class="badge" [ngClass]="h['status'] === 'APPROVED' ? 'badge-success' : h['status'] === 'PENDING' ? 'badge-warning' : 'badge-light border'">
            {{ h['status'] === 'APPROVED' ? 'Approved' : h['status'] === 'PENDING' ? 'Waiting for the office' : 'Not approved' }}</span></td>
        </tr></tbody>
      </table>
    </div></div>
  `
})
export class PortalVolunteerHoursComponent implements OnInit {
  readonly today = new Date().toISOString().slice(0, 10);
  rows: Row[] = [];
  approved = 0;
  activities: { key: string; label: string }[] = [];
  form: Row = { activity: 'MENTORING', workedOn: this.today, hours: 1, description: '' };
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Row>('/portal/volunteer-hours').subscribe({
      next: (d) => {
        this.rows = d['rows'];
        this.approved = Number(d['approved_hours']) || 0;
        this.activities = Object.entries(d['activities'] as Record<string, string>).map(([key, label]) => ({ key, label }));
      },
      error: (e) => (this.error = errorText(e, 'Could not load.'))
    });
  }

  save(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/portal/volunteer-hours', this.form).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.form = { ...this.form, description: '' }; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send.'); }
    });
  }

  certificate(): void {
    this.api.download('/portal/volunteer-hours/certificate', {}, 'My-volunteer-certificate.pdf').subscribe({ error: (e) => (this.error = errorText(e, 'Could not make the certificate.')) });
  }
}
