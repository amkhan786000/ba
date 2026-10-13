import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';
import { ColumnChartComponent, SeriesData } from '../shared/charts/column-chart.component';
import { DonutChartComponent } from '../shared/charts/donut-chart.component';
import { FunnelChartComponent } from '../shared/charts/funnel-chart.component';
import { StatTileComponent } from '../shared/charts/stat-tile.component';

interface ApplicationsDashboard {
  year: number;
  years: number[];
  scope: 'all' | 'chapter' | 'rcc';
  period: { is_active?: boolean | number; start_date?: string; end_date?: string } | null;
  kpis: Record<string, number | null>;
  charts: {
    funnel: BarDatum[]; byStatus: BarDatum[]; byMonth: SeriesData; byCourse: BarDatum[]; byRcc: BarDatum[];
    familyIncome: BarDatum[]; recommendations: BarDatum[]; bySex: BarDatum[];
  };
  upcomingInterviews: { id: number; name: string; interview_at: string; interview_venue: string; status: string }[];
}

/** Students > Applications Dashboard: demand, the selection pipeline, who applies and the interviews ahead. */
@Component({
  selector: 'app-admin-applications-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, AppDatePipe, BarChartComponent, ColumnChartComponent,
    DonutChartComponent, FunnelChartComponent, StatTileComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .card .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
  `],
  template: `
    <div class="row mt-2"><div class="col-12">
      <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
        <h4 class="page-title mb-2 mb-md-0">Applications Dashboard</h4>
        <div class="d-flex align-items-center">
          <label for="yearSelect" class="mr-2 mb-0">Applied in</label>
          <select class="form-control form-control-sm" id="yearSelect" [(ngModel)]="year" (ngModelChange)="load()" style="width: 110px">
            <option *ngFor="let y of d?.years" [ngValue]="y">{{ y }}</option>
          </select>
        </div>
      </div>
    </div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <ng-container *ngIf="d">
      <p class="mb-3">
        Application period:
        <ng-container *ngIf="d.period?.is_active; else closed">
          <span class="badge badge-success">Open</span>
          <small class="ml-1 text-muted">{{ d.period?.start_date | appDate }} to {{ d.period?.end_date | appDate }}</small>
        </ng-container>
        <ng-template #closed><span class="badge badge-secondary">Closed</span></ng-template>
        <a class="ml-2 small" routerLink="/admin/application-period">change</a>
      </p>
      <p *ngIf="d.scope !== 'all'" class="alert alert-info py-2">Applications of your {{ d.scope === 'rcc' ? 'RCC center' : 'chapter' }} only.</p>

      <div class="tiles">
        <app-stat-tile label="Received" icon="mdi-file-document" [value]="k('received')" [sub]="k('applied') + ' not looked at yet (Applied)'"
                       link="/admin/applications" linkText="Applications"></app-stat-tile>
        <app-stat-tile label="Waiting for a decision" icon="mdi-timer-sand" [tone]="k('open') ? 'warning' : 'success'" [value]="k('open')"
                       sub="applied, in process or interview called" link="/admin/applications" linkText="Review"></app-stat-tile>
        <app-stat-tile label="Accepted" icon="mdi-check-decagram" tone="success" [value]="k('accepted')" [sub]="k('rejected') + ' rejected · students created'"></app-stat-tile>
        <app-stat-tile label="Acceptance rate" icon="mdi-percent" tone="info" [value]="pct(k('acceptanceRate'))" sub="of decided applications"></app-stat-tile>
        <app-stat-tile label="Interviews ahead" icon="mdi-calendar-account" tone="purple" [value]="k('interviewsUpcoming')"
                       link="/admin/applications/ranking" linkText="Ranking"></app-stat-tile>
        <app-stat-tile label="Interviewed, no review" icon="mdi-comment-alert" [tone]="k('interviewsWithoutReview') ? 'warning' : 'success'"
                       [value]="k('interviewsWithoutReview')" sub="interview date passed"></app-stat-tile>
        <app-stat-tile label="Days to a decision" icon="mdi-calendar-range" tone="info" [value]="k('daysToDecision') ?? '–'"
                       sub="on average, applied to accepted / rejected"></app-stat-tile>
      </div>

      <div class="row">
        <div class="col-xl-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Selection pipeline <small>% = how many go on from the step before</small></h4>
            <app-funnel-chart [data]="d.charts.funnel"></app-funnel-chart>
          </div></div>
        </div>
        <div class="col-xl-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">By current step</h4>
            <app-bar-chart [data]="d.charts.byStatus" emptyText="No applications this year."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-8">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Applications received per month</h4>
            <app-column-chart [data]="d.charts.byMonth" emptyText="No applications this year."></app-column-chart>
          </div></div>
        </div>
        <div class="col-xl-4">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Interview recommendations</h4>
            <app-donut-chart [data]="d.charts.recommendations" caption="reviews" emptyText="No interview reviews yet."></app-donut-chart>
          </div></div>
        </div>
        <div class="col-xl-4">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Course applied for <small>top 10</small></h4>
            <app-bar-chart [data]="d.charts.byCourse" colour="#0e7490" emptyText="No applications this year."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-4">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">RCC center <small>top 10</small></h4>
            <app-bar-chart [data]="d.charts.byRcc" colour="#6d28d9" emptyText="No applications this year."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-4 col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Girls and boys</h4>
            <app-donut-chart [data]="d.charts.bySex" caption="applicants" [colours]="['#db2777', '#0e7490', '#6b776f']"
                             emptyText="No applications this year."></app-donut-chart>
          </div></div>
        </div>
        <div class="col-xl-4 col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Family income per year</h4>
            <app-bar-chart [data]="d.charts.familyIncome" colour="#15803d" emptyText="No applications this year."></app-bar-chart>
          </div></div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <h4 class="header-title mb-3">Next interviews</h4>
          <div class="table-responsive"><table class="table table-sm table-centered mb-0">
            <thead><tr><th>When</th><th>Applicant</th><th>Venue</th><th>Status</th></tr></thead>
            <tbody>
              <tr *ngIf="!d.upcomingInterviews.length"><td colspan="4" class="text-center text-muted">No interviews scheduled.</td></tr>
              <tr *ngFor="let i of d.upcomingInterviews">
                <td class="text-nowrap">{{ i.interview_at | appDate: 'datetime' }}</td>
                <td><a [routerLink]="['/admin/applications', i.id]">{{ i.name }}</a></td>
                <td>{{ i.interview_venue || '--' }}</td>
                <td>{{ i.status }}</td>
              </tr>
            </tbody>
          </table></div>
        </div>
      </div>
    </ng-container>
  `
})
export class AdminApplicationsDashboardComponent implements OnInit {
  d: ApplicationsDashboard | null = null;
  year: number | null = null;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.error = '';
    this.api.get<ApplicationsDashboard>('/admin/dashboards/applications', this.year ? { year: this.year } : undefined).subscribe({
      next: (d) => { this.d = d; this.year = d.year; },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }

  k(key: string): number | null { return this.d?.kpis[key] ?? null; }
  pct(v: number | null): string { return v === null ? '–' : v + '%'; }
}
