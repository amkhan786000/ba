import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';

interface ConvenorDashboard {
  convenor: { name: string; email: string | null; phone: string | null; region: string | null };
  grantees: { user_id: string; name: string; email: string | null; phone: string | null; paymentStatus: 'paid' | 'unpaid' }[];
  applicationsByStatus: BarDatum[];
  sponsorsByRegion: BarDatum[];
}

export const REGION_NOT_SET = 'Your region is not set';

/** Port of templates/convenor/dashboard.html (charts use real data; per-student payment status). */
@Component({
  selector: 'app-convenor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, BarChartComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Convenor Dashboard</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <!-- Flask redirected to a profile page that didn't exist when the region was missing; this sets it in place. -->
    <div class="row" *ngIf="needsRegion">
      <div class="col-md-6">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Set Your Region</h4>
            <p class="text-muted">Your region is not set. Choose the region (chapter) you are responsible for.</p>
            <form (ngSubmit)="saveRegion()">
              <div class="form-group">
                <input type="text" class="form-control" name="region" [(ngModel)]="region" placeholder="e.g. Jeddah" required>
              </div>
              <button type="submit" class="btn btn-primary" [disabled]="!region.trim()">Save Region</button>
            </form>
          </div>
        </div>
      </div>
    </div>

    <ng-container *ngIf="data">
      <div class="row">
        <div class="col-md-6">
          <div class="card">
            <div class="card-body">
              <h4 class="header-title">Convenor Details</h4>
              <div class="mt-3">
                <p><strong>Name:</strong> {{ data.convenor.name }}</p>
                <p><strong>Email:</strong> {{ data.convenor.email }}</p>
                <p><strong>Phone:</strong> {{ data.convenor.phone }}</p>
                <p><strong>Region:</strong> {{ data.convenor.region }}</p>
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="card">
            <div class="card-body">
              <h4 class="header-title">Assigned Student</h4>
              <div class="mt-3">
                <ul class="list-group">
                  <li *ngIf="!data.grantees.length" class="list-group-item text-muted">No students assigned.</li>
                  <li *ngFor="let g of data.grantees" class="list-group-item">
                    <strong>{{ g.name }}</strong> - {{ g.email }} - {{ g.phone }} -
                    <span [ngClass]="g.paymentStatus === 'paid' ? 'text-success' : 'text-danger'">{{ g.paymentStatus }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="row">
        <div class="col-xl-6">
          <div class="card-box">
            <h4 class="header-title mb-4">Applications by Status</h4>
            <app-bar-chart [data]="data.applicationsByStatus"></app-bar-chart>
            <p class="text-muted mb-0 mt-3 text-truncate">Applications from {{ data.convenor.region }}</p>
          </div>
        </div>
        <div class="col-xl-6">
          <div class="card-box">
            <h4 class="header-title mb-4">Sponsors by Region</h4>
            <app-bar-chart [data]="data.sponsorsByRegion"></app-bar-chart>
          </div>
        </div>
      </div>
    </ng-container>
  `
})
export class ConvenorDashboardComponent implements OnInit {
  data: ConvenorDashboard | null = null;
  needsRegion = false;
  region = '';
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<ConvenorDashboard>('/convenor/dashboard').subscribe({
      next: (d) => { this.data = d; this.needsRegion = false; },
      error: (e) => {
        const text = errorText(e, 'Could not load the dashboard.');
        if (text.startsWith(REGION_NOT_SET)) this.needsRegion = true; else this.error = text;
      }
    });
  }

  saveRegion(): void {
    this.api.post<{ message: string }>('/convenor/profile', { region: this.region.trim() }).subscribe({
      next: (r) => { this.message = r.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not save your region.'))
    });
  }
}
