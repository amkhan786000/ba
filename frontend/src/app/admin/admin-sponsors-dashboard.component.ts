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
import { StatTileComponent } from '../shared/charts/stat-tile.component';
import { inr } from '../shared/charts/money';

interface SponsorsDashboard {
  year: number;
  years: number[];
  scope: 'all' | 'chapter' | 'rcc';
  kpis: Record<string, number | null>;
  charts: {
    studentsPerSponsor: BarDatum[]; sponsorsByChapter: BarDatum[]; mappings: SeriesData; sponsorshipLength: BarDatum[];
    collectedByMonth?: SeriesData; dueVsPaid?: SeriesData; overdueAgeing?: BarDatum[]; upcoming?: BarDatum[];
    methods?: BarDatum[]; onlineByPurpose?: BarDatum[];
  };
  mostOverdue?: { user_id: string; name: string; students: number; installments: number; amount: number; oldest_due: string }[];
}

/** Sponsors & Payments > Sponsors Dashboard: the sponsors, what they pay, how punctually, and who is behind. */
@Component({
  selector: 'app-admin-sponsors-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, AppDatePipe, BarChartComponent, ColumnChartComponent,
    DonutChartComponent, StatTileComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .card .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
  `],
  template: `
    <div class="row mt-2"><div class="col-12">
      <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
        <h4 class="page-title mb-2 mb-md-0">Sponsors &amp; Payments Dashboard</h4>
        <div class="d-flex align-items-center">
          <label for="yearSelect" class="mr-2 mb-0">Year</label>
          <select class="form-control form-control-sm" id="yearSelect" [(ngModel)]="year" (ngModelChange)="load()" style="width: 110px">
            <option *ngFor="let y of d?.years" [ngValue]="y">{{ y }}</option>
          </select>
        </div>
      </div>
    </div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <ng-container *ngIf="d">
      <p *ngIf="d.scope !== 'all'" class="alert alert-info py-2">Money and students of your {{ d.scope === 'rcc' ? 'RCC center' : 'chapter' }} only.</p>

      <h5 class="mb-2">Sponsors</h5>
      <div class="tiles">
        <app-stat-tile label="Active sponsors" icon="mdi-account-heart" tone="purple" [value]="k('activeSponsors')"
                       [sub]="k('sponsorsWithStudents') + ' with a student · ' + k('sponsorsWithoutStudents') + ' without'"
                       link="/admin/sponsor-followup" linkText="Follow up"></app-stat-tile>
        <app-stat-tile label="Students sponsored" icon="mdi-school" [value]="k('sponsoredStudents')"
                       [sub]="k('studentsPerSponsor') + ' per sponsor on average'"></app-stat-tile>
        <app-stat-tile label="Waiting for a sponsor" icon="mdi-account-alert" [tone]="k('withoutSponsor') ? 'warning' : 'success'"
                       [value]="k('withoutSponsor')" sub="studying students" link="/admin/sponsorships" linkText="Map"></app-stat-tile>
        <app-stat-tile label="Behind over 30 days" icon="mdi-account-clock" [tone]="k('lapsedSponsors') ? 'danger' : 'success'"
                       [value]="k('lapsedSponsors')" sub="sponsors" link="/admin/sponsor-followup" linkText="Follow up"></app-stat-tile>
        <app-stat-tile label="Pay through Rahbar" icon="mdi-credit-card-outline" tone="info" [value]="k('payThroughRahbar')"
                       sub="sponsors paying online"></app-stat-tile>
      </div>

      <ng-container *ngIf="has('paidThisYear')">
        <h5 class="mb-2">Money {{ d.year }}</h5>
        <div class="tiles">
          <app-stat-tile label="Paid to students" icon="mdi-cash" tone="success" [value]="money(k('paidThisYear'))"
                         [sub]="growth()" [trend]="trend()"></app-stat-tile>
          <app-stat-tile label="Collection rate" icon="mdi-percent" [tone]="rateTone(k('collectionRate'), 90, 70)"
                         [value]="pct(k('collectionRate'))" [sub]="money(k('paidOfDue')) + ' of ' + money(k('dueSoFar')) + ' due so far'"></app-stat-tile>
          <app-stat-tile label="Paid on time" icon="mdi-clock-check" [tone]="rateTone(k('onTimeRate'), 80, 60)" [value]="pct(k('onTimeRate'))"
                         [sub]="k('avgDaysLate') === null ? 'of installments paid' : 'late ones: ' + k('avgDaysLate') + ' days late on average'"></app-stat-tile>
          <app-stat-tile label="Overdue now" icon="mdi-alarm" [tone]="+(k('overdueAmount') || 0) ? 'danger' : 'success'" [value]="money(k('overdueAmount'))"
                         [sub]="k('overdueInstallments') + ' installments · ' + k('overdueSponsors') + ' sponsors'" link="/admin/payment-dues" linkText="Dues"></app-stat-tile>
        </div>

        <div class="row">
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Paid per month <small>{{ d.year }} next to {{ d.year - 1 }}</small></h4>
              <app-column-chart [data]="d.charts.collectedByMonth" [money]="true" [colours]="['#cfe0a8', '#4f7a00']"></app-column-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Coming due <small>not paid yet</small></h4>
              <app-bar-chart [data]="d.charts.upcoming || []" [money]="true" colour="#d97706" emptyText="Nothing due in the next 90 days."></app-bar-chart>
            </div></div>
          </div>
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Due and paid by due month <small>installments falling due in {{ d.year }}</small></h4>
              <app-column-chart [data]="d.charts.dueVsPaid" [money]="true" [colours]="['#d97706', '#15803d']"></app-column-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Overdue by how late</h4>
              <app-bar-chart [data]="d.charts.overdueAgeing || []" [money]="true" colour="#c0262d" emptyText="Nothing overdue."></app-bar-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">How sponsors paid <small>{{ d.year }}</small></h4>
              <app-donut-chart [data]="d.charts.methods || []" [money]="true" caption="paid" emptyText="No payments this year."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6" *ngIf="d.charts.onlineByPurpose">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Received online <small>Razorpay, {{ d.year }}</small></h4>
              <app-donut-chart [data]="d.charts.onlineByPurpose" [money]="true" caption="online" emptyText="No online payments this year."></app-donut-chart>
            </div></div>
          </div>
        </div>

        <div class="card" *ngIf="d.mostOverdue">
          <div class="card-body">
            <h4 class="header-title mb-3">Most overdue sponsors <small><a routerLink="/admin/sponsor-followup">follow up</a></small></h4>
            <div class="table-responsive"><table class="table table-sm table-centered mb-0">
              <thead><tr><th>Sponsor</th><th class="text-right">Students</th><th class="text-right">Installments</th><th class="text-right">Amount</th><th>Oldest due</th></tr></thead>
              <tbody>
                <tr *ngIf="!d.mostOverdue.length"><td colspan="5" class="text-center text-muted">No sponsor is behind. </td></tr>
                <tr *ngFor="let s of d.mostOverdue">
                  <td>{{ s.name }} <small class="text-muted">{{ s.user_id }}</small></td>
                  <td class="text-right">{{ s.students }}</td><td class="text-right">{{ s.installments }}</td>
                  <td class="text-right text-danger">{{ money(s.amount) }}</td><td>{{ s.oldest_due | appDate }}</td>
                </tr>
              </tbody>
            </table></div>
          </div>
        </div>
      </ng-container>

      <h5 class="mb-2">Sponsorships</h5>
      <div class="row">
        <div class="col-xl-4 col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Students per sponsor</h4>
            <app-bar-chart [data]="d.charts.studentsPerSponsor" colour="#6d28d9"></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-4 col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">How long pairs last <small>current sponsorships</small></h4>
            <app-bar-chart [data]="d.charts.sponsorshipLength" colour="#15803d" emptyText="No sponsorships yet."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-4">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Active sponsors by chapter</h4>
            <app-bar-chart [data]="d.charts.sponsorsByChapter" emptyText="No sponsors yet."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-12">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Sponsorships started and ended per month <small>{{ d.year }}</small></h4>
            <app-column-chart [data]="d.charts.mappings" [colours]="['#15803d', '#c0262d']" emptyText="No changes this year."></app-column-chart>
          </div></div>
        </div>
      </div>
    </ng-container>
  `
})
export class AdminSponsorsDashboardComponent implements OnInit {
  d: SponsorsDashboard | null = null;
  year: number | null = null;
  error = '';
  readonly money = inr;

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.error = '';
    this.api.get<SponsorsDashboard>('/admin/dashboards/sponsors', this.year ? { year: this.year } : undefined).subscribe({
      next: (d) => { this.d = d; this.year = d.year; },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }

  k(key: string): number | null { return this.d?.kpis[key] ?? null; }
  has(key: string): boolean { return !!this.d && key in this.d.kpis; }
  pct(v: number | null): string { return v === null ? '–' : v + '%'; }

  growth(): string {
    const a = Number(this.k('paidThisYear')) || 0, b = Number(this.k('paidLastYear')) || 0;
    if (!b) return a ? 'nothing paid by now last year' : 'nothing yet';
    const p = Math.round(((a - b) / b) * 100);
    return (p >= 0 ? '▲ ' : '▼ ') + Math.abs(p) + '% vs same time last year (' + inr(b) + ')';
  }
  trend(): 'up' | 'down' | null {
    const a = Number(this.k('paidThisYear')) || 0, b = Number(this.k('paidLastYear')) || 0;
    return !b ? null : a >= b ? 'up' : 'down';
  }
  rateTone(rate: number | null, good: number, ok: number): 'success' | 'warning' | 'danger' | 'info' {
    return rate === null ? 'info' : rate >= good ? 'success' : rate >= ok ? 'warning' : 'danger';
  }
}
