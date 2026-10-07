import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';
import { PagerComponent, pageOf } from '../shared/pager/pager.component';

interface Grantee {
  user: { id: number; user_id: string; name: string } | null;
  paymentStatus: string;
}

interface SponsorDashboard {
  sponsor: { name: string | null; email: string | null; phone: string | null; region: string | null };
  grantees: Grantee[];
  performanceByYear: BarDatum[];
}

/** Port of templates/sponsor/dashboard.html (charts use real data instead of the template's sample numbers). */
@Component({
  selector: 'app-sponsor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, BarChartComponent, PagerComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Sponsor Overview</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row" *ngIf="data">
      <div class="col-12 col-lg-4">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title card-header-title">Master Profile</h4>
            <div class="profile-img-container"><img src="assets/theme/images/users/avatar-1.jpg" alt="profile"></div>
            <div class="text-center">
              <h5 class="mb-1">{{ data.sponsor.name || 'Name Not Found' }}</h5>
              <p class="text-muted mb-3">{{ data.sponsor.email || 'N/A' }}</p>
            </div>
            <hr>
            <p class="mb-1"><strong>Phone:</strong> {{ data.sponsor.phone || 'N/A' }}</p>
            <p class="mb-1"><strong>Location:</strong> {{ data.sponsor.region || 'N/A' }}</p>
            <div class="alert alert-light border mt-3 mb-0 small">All students sponsored by this account are listed in the beneficiaries table.</div>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title card-header-title">Assigned Beneficiaries</h4>
            <div class="row mb-3">
              <div class="col-12">
                <input type="text" class="form-control" placeholder="Search by student name or ID..." [(ngModel)]="search" (ngModelChange)="page = 1" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-hover table-centered mb-0">
                <thead><tr><th>Student Details</th><th>Status</th><th class="text-right">Action</th></tr></thead>
                <tbody>
                  <tr *ngIf="!data.grantees.length"><td colspan="3" class="text-center py-4 text-muted">No students assigned yet.</td></tr>
                  <tr *ngFor="let g of rows">
                    <td><span class="font-weight-bold d-block text-dark">{{ g.user?.name }}</span><small class="text-muted">ID: {{ g.user?.user_id }}</small></td>
                    <td><span class="badge px-2 py-1" [ngClass]="badge(g.paymentStatus)">{{ g.paymentStatus }}</span></td>
                    <td class="text-right">
                      <a routerLink="/sponsor/payments" [queryParams]="{ granteeId: g.user?.id }" class="btn btn-xs btn-outline-success font-weight-bold">Pay Now</a>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager *ngIf="filtered.length > 5" [total]="filtered.length" [page]="page" [pageSize]="5" (pageChange)="page = $event"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <div class="row mt-2" *ngIf="data">
      <div class="col-12 col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-3">Student Performance Trend</h4>
          <app-bar-chart [data]="data.performanceByYear"></app-bar-chart>
          <p class="text-muted mb-0 mt-3 small">Average marks of your students, by academic year</p>
        </div>
      </div>
      <div class="col-12 col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-3">Sponsorship Distribution</h4>
          <app-bar-chart [data]="distribution"></app-bar-chart>
          <p class="text-muted mb-0 mt-3 small">Your students by payment status</p>
        </div>
      </div>
    </div>
  `
})
export class SponsorDashboardComponent implements OnInit {
  data: SponsorDashboard | null = null;
  distribution: BarDatum[] = [];
  search = '';
  page = 1;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<SponsorDashboard>('/sponsor/dashboard').subscribe({
      next: (d) => {
        this.data = d;
        const counts = new Map<string, number>();
        for (const g of d.grantees) counts.set(g.paymentStatus, (counts.get(g.paymentStatus) ?? 0) + 1);
        this.distribution = [...counts.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
      },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }

  get filtered(): Grantee[] {
    const t = this.search.toLowerCase();
    return (this.data?.grantees ?? []).filter((g) =>
      [g.user?.name, g.user?.user_id, g.paymentStatus].some((v) => String(v ?? '').toLowerCase().includes(t)));
  }
  get rows(): Grantee[] { return pageOf(this.filtered, this.page, 5); }

  badge(status: string): string {
    if (status === 'Overdue') return 'badge-danger';
    if (status === 'On Schedule' || status === 'Completed') return 'badge-success';
    return 'badge-warning';
  }
}
