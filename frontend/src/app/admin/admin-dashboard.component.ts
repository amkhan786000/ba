import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';

interface AdminDashboard {
  usersCount: number;
  applicationsCount: number;
  paymentsCount: number;
  sponsorsConvenorsCount: number;
  availableYears: number[];
  selectedYear: number | null;
  applicationPeriod: { is_active?: boolean | number; start_date?: string; end_date?: string } | null;
}

/** Port of templates/admin/dashboard.html */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="row mt-2">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-center">
          <h4 class="page-title mb-2 mb-md-0">Admin Dashboard <span *ngIf="data?.selectedYear">({{ data?.selectedYear }})</span></h4>
          <form class="form-inline" (submit)="$event.preventDefault()">
            <div class="form-group mb-0">
              <label for="yearSelect" class="mr-2">Year:</label>
              <select class="form-control form-control-sm" id="yearSelect" name="year" [(ngModel)]="year" (ngModelChange)="load()">
                <option [ngValue]="null">All Years</option>
                <option *ngFor="let yr of data?.availableYears" [ngValue]="yr">{{ yr }}</option>
              </select>
            </div>
          </form>
        </div>
      </div>
    </div>

    <div *ngIf="error" class="alert alert-danger mt-2">{{ error }}</div>

    <div class="row">
      <div class="col-12 col-sm-6 col-xl-3">
        <div class="card-box">
          <h4 class="header-title mt-0 mb-3">Total Users</h4>
          <h2 class="text-primary">{{ data?.usersCount ?? '–' }}</h2>
        </div>
      </div>
      <div class="col-12 col-sm-6 col-xl-3">
        <div class="card-box">
          <h4 class="header-title mt-0 mb-3">Applications</h4>
          <h2 class="text-success">{{ data?.applicationsCount ?? '–' }}</h2>
        </div>
      </div>
      <div class="col-12 col-sm-6 col-xl-3">
        <div class="card-box">
          <h4 class="header-title mt-0 mb-3">Total Payments</h4>
          <h2 class="text-info">{{ data?.paymentsCount ?? '–' }}</h2>
        </div>
      </div>
      <div class="col-12 col-sm-6 col-xl-3">
        <div class="card-box">
          <h4 class="header-title mt-0 mb-3">Active Sponsors</h4>
          <h2 class="text-warning">{{ data?.sponsorsConvenorsCount ?? '–' }}</h2>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Application Period Status</h4>
            <p class="mb-0">
              <ng-container *ngIf="data?.applicationPeriod?.is_active; else inactive">
                <span class="badge badge-success">Active</span>
                <small class="ml-1">({{ data?.applicationPeriod?.start_date }} to {{ data?.applicationPeriod?.end_date }})</small>
              </ng-container>
              <ng-template #inactive><span class="badge badge-danger">Inactive</span></ng-template>
            </p>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  data: AdminDashboard | null = null;
  year: number | null = null;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.error = '';
    const params = this.year ? { year: this.year } : undefined;
    this.api.get<AdminDashboard>('/admin/dashboard', params).subscribe({
      next: (d) => (this.data = d),
      error: (err) => (this.error = err?.error?.error ?? 'Could not load dashboard.')
    });
  }
}
