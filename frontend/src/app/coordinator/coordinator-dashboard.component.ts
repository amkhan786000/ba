import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';

interface CoordinatorDashboard {
  coordinator: { name: string; email: string | null; phone: string | null };
  availableYears: number[];
  selectedYear: number;
  applicationsCount: number;
  sponsorsCount: number;
  granteesCount: number;
  applicationsByStatus: BarDatum[];
  sponsorsByRegion: BarDatum[];
}

/** Port of templates/coordinator/dashboard.html (charts now use real data instead of the template's sample numbers). */
@Component({
  selector: 'app-coordinator-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, BarChartComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coordinator Dashboard</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-md-6">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Coordinator Details</h4>
            <div class="mt-3">
              <p><strong>Name:</strong> {{ data?.coordinator?.name }}</p>
              <p><strong>Email:</strong> {{ data?.coordinator?.email }}</p>
              <p><strong>Phone:</strong> {{ data?.coordinator?.phone }}</p>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-6">
        <div class="card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center">
              <h4 class="header-title">Quick Stats</h4>
              <select class="form-control form-control-sm" style="display: inline-block; width: auto;" [(ngModel)]="year" (ngModelChange)="load()">
                <option *ngIf="!data?.availableYears?.length" [ngValue]="null">No Data</option>
                <option *ngFor="let y of data?.availableYears" [ngValue]="y">{{ y }}</option>
              </select>
            </div>
            <div class="mt-3">
              <p><strong>Total Applications:</strong> {{ data?.applicationsCount ?? '–' }}</p>
              <p><strong>Total Sponsors:</strong> {{ data?.sponsorsCount ?? '–' }}</p>
              <p><strong>Total Student:</strong> {{ data?.granteesCount ?? '–' }}</p>
              <p class="text-muted" *ngIf="data?.selectedYear"><small>Showing stats for the year: {{ data?.selectedYear }}</small></p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-4">Applications by Status</h4>
          <app-bar-chart [data]="data?.applicationsByStatus ?? []"></app-bar-chart>
        </div>
      </div>
      <div class="col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-4">Sponsors by Region</h4>
          <app-bar-chart [data]="data?.sponsorsByRegion ?? []"></app-bar-chart>
          <p class="text-muted mb-0 mt-3 text-truncate">Sponsors registered in {{ data?.selectedYear }}, by region</p>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorDashboardComponent implements OnInit {
  data: CoordinatorDashboard | null = null;
  year: number | null = null;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<CoordinatorDashboard>('/coordinator/dashboard', this.year ? { year: this.year } : undefined).subscribe({
      next: (d) => { this.data = d; this.year = d.selectedYear; },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }
}
