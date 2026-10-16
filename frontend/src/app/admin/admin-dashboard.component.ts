import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { Section } from '../core/models/user.model';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';
import { ColumnChartComponent, SeriesData } from '../shared/charts/column-chart.component';
import { DonutChartComponent } from '../shared/charts/donut-chart.component';
import { FunnelChartComponent } from '../shared/charts/funnel-chart.component';
import { StatTileComponent } from '../shared/charts/stat-tile.component';
import { inr } from '../shared/charts/money';
import { FundBalance, FundSummaryComponent } from '../shared/charts/fund-summary.component';

interface AttentionItem { key: string; label: string; count: number; amount?: number; link: string }

interface Overview {
  year: number;
  years: number[];
  scope: 'all' | 'chapter' | 'rcc';
  kpis: Record<string, number | null>;
  attention: AttentionItem[];
  charts: {
    studyStatus: BarDatum[];
    collectedByMonth?: SeriesData;
    overdueAgeing?: BarDatum[];
    applicationFunnel?: BarDatum[];
    studentsByChapter: BarDatum[];
    finance?: SeriesData;
  };
  reb?: { activeGirls: number; openInvoices: number; openAmount: number; attendanceMarked: number; attendanceToday: number | null };
  applicationPeriod?: { is_active?: boolean | number; start_date?: string; end_date?: string } | null;
  recentActivity?: { user_name: string; action: string; created_at: string }[];
  fund?: FundBalance;
}

/** The module dashboards linked from the overview (each shown only with its permission). */
export const MODULE_DASHBOARDS: { path: string; label: string; icon: string; section: Section }[] = [
  { path: '/admin/students-dashboard', label: 'Students', icon: 'mdi-school', section: 'STUDENTS' },
  { path: '/admin/sponsors-dashboard', label: 'Sponsors & Payments', icon: 'mdi-account-heart', section: 'SPONSORSHIPS' },
  { path: '/admin/applications-dashboard', label: 'Applications', icon: 'mdi-file-document', section: 'APPLICATIONS' },
  { path: '/admin/chapter-dashboard', label: 'Chapters', icon: 'mdi-view-quilt', section: 'CHAPTER_DASHBOARD' },
  { path: '/admin/reb/dashboard', label: 'REB', icon: 'mdi-view-dashboard-outline', section: 'REB_DASHBOARD' },
  { path: '/admin/finance', label: 'Income & Expenses', icon: 'mdi-cash-multiple', section: 'FINANCE' }
];

/**
 * Admin > Dashboard: everything at a glance. What is waiting for someone (with a link to do it), the headline
 * numbers for the year, and charts for money, students and applications. Parts the user may not open are left out.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, AppDatePipe, BarChartComponent, ColumnChartComponent,
    DonutChartComponent, FunnelChartComponent, StatTileComponent, FundSummaryComponent],
  styles: [`
    .modules { display: flex; flex-wrap: wrap; gap: 8px; }
    .modules a { border: 1px solid #e5e9e4; border-radius: 20px; padding: 4px 12px; font-size: 13px; color: #3d4a43; background: #fff; }
    .modules a:hover { border-color: #4f7a00; color: #4f7a00; text-decoration: none; }
    .attention { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; }
    .att { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: var(--radius-sm, 10px); background: var(--surface, #fff);
           box-shadow: var(--shadow-sm); border: 1px solid var(--line, #e5e9e4); color: inherit; border-left: 4px solid var(--warning, #b45309); }
    .att:hover { text-decoration: none; background: #fdf3e3; }
    .att .n { font-size: 22px; font-weight: 700; min-width: 42px; text-align: center; color: #17231c; }
    .att .t { font-size: 13px; color: #3d4a43; line-height: 1.3; }
    .att .t small { display: block; color: #17231c; font-weight: 600; }
    .clear { font-size: 13px; color: #15803d; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .card .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
    .mini { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
    .mini div { background: var(--bg, #f4f6f2); border-radius: var(--radius-sm, 10px); padding: 10px; }
    .mini strong { display: block; font-size: 20px; }
    .mini span { font-size: 12px; color: #3d4a43; }
    .activity li { padding: 6px 0; border-bottom: 1px solid #e5e9e4; font-size: 13px; }
    .activity li:last-child { border-bottom: 0; }
  `],
  template: `
    <div class="row mt-2"><div class="col-12">
      <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
        <h4 class="page-title mb-2 mb-md-0">Dashboard</h4>
        <div class="d-flex align-items-center">
          <label for="yearSelect" class="mr-2 mb-0">Year</label>
          <select class="form-control form-control-sm" id="yearSelect" [(ngModel)]="year" (ngModelChange)="load()" style="width: 110px">
            <option *ngFor="let y of d?.years" [ngValue]="y">{{ y }}</option>
          </select>
        </div>
      </div>
    </div></div>

    <app-alerts [(error)]="error"></app-alerts>

    <div class="modules mb-3" *ngIf="modules.length">
      <span class="text-muted small align-self-center mr-1">Dashboards:</span>
      <a *ngFor="let m of modules" [routerLink]="m.path"><i class="mdi" [ngClass]="m.icon"></i> {{ m.label }}</a>
    </div>

    <ng-container *ngIf="d">
      <p *ngIf="d.scope !== 'all'" class="alert alert-info py-2">Showing the students of your {{ d.scope === 'rcc' ? 'RCC center' : 'chapter' }} only.</p>

      <p *ngIf="d.applicationPeriod !== undefined" class="mb-3">
        Application period:
        <ng-container *ngIf="d.applicationPeriod?.is_active; else closed">
          <span class="badge badge-success">Open</span>
          <small class="ml-1 text-muted">{{ d.applicationPeriod?.start_date | appDate }} to {{ d.applicationPeriod?.end_date | appDate }}</small>
        </ng-container>
        <ng-template #closed><span class="badge badge-secondary">Closed</span></ng-template>
      </p>

      <!-- Needs attention -->
      <h5 class="mb-2">Needs attention</h5>
      <div class="attention mb-2">
        <a class="att" *ngFor="let a of waiting" [routerLink]="a.link">
          <span class="n">{{ a.count }}</span>
          <span class="t">{{ a.label }}<small *ngIf="a.amount">{{ money(a.amount) }}</small></span>
        </a>
      </div>
      <p class="clear mb-4" *ngIf="cleared.length">
        <i class="mdi mdi-check-circle"></i> All clear: {{ clearedLabels }}
      </p>

      <!-- Headline numbers -->
      <h5 class="mb-2">{{ d.year }} at a glance</h5>
      <div class="tiles">
        <app-stat-tile label="Studying" icon="mdi-school" [value]="k('studying')" tone="primary"
                       [sub]="k('onHold') + ' on hold · ' + k('graduated') + ' graduated'"></app-stat-tile>
        <app-stat-tile *ngIf="has('activeSponsors')" label="Active sponsors" icon="mdi-account-heart" tone="purple" [value]="k('activeSponsors')"
                       [sub]="k('sponsorsWithStudents') + ' with a student now'"></app-stat-tile>
        <app-stat-tile *ngIf="has('paidThisYear')" label="Installments paid" icon="mdi-cash" tone="success" [value]="k('paidThisYear')"
                       [sub]="growth('paidThisYear', 'paidLastYear')" [trend]="trend('paidThisYear', 'paidLastYear')"></app-stat-tile>
        <app-stat-tile *ngIf="has('dueSoFar')" label="Collection rate" icon="mdi-percent" [tone]="rateTone(k('collectionRate'))"
                       [value]="k('collectionRate') === null ? '–' : k('collectionRate') + '%'"
                       [sub]="k('paidOfDue') + ' of ' + k('dueSoFar') + ' installments due so far are paid'"></app-stat-tile>
        <app-stat-tile *ngIf="has('overdueInstallments')" label="Overdue installments" icon="mdi-alarm" [tone]="+(k('overdueInstallments') || 0) ? 'danger' : 'success'"
                       [value]="k('overdueInstallments')" [sub]="k('overdueStudents') + ' students affected'"></app-stat-tile>
        <app-stat-tile *ngIf="has('donations')" label="Online donations" icon="mdi-hand-heart" tone="info" [value]="money(k('donations'))"
                       [sub]="k('donationsCount') + ' donations'"></app-stat-tile>
        <app-stat-tile *ngIf="has('alumni')" label="Alumni" icon="mdi-account-star" tone="purple" [value]="k('alumni')" link="/admin/alumni"></app-stat-tile>
        <app-stat-tile *ngIf="has('activeMentorships')" label="Mentoring pairs" icon="mdi-account-tie-voice" tone="info" [value]="k('activeMentorships')"
                       link="/admin/mentoring"></app-stat-tile>
      </div>

      <!-- Rahbar's own money -->
      <ng-container *ngIf="d.fund">
        <h5 class="mb-2">Rahbar funds <small class="text-muted">office and work: received, spent, in hand</small></h5>
        <app-fund-summary [fund]="d.fund" [chart]="false" [showLast]="false"></app-fund-summary>
      </ng-container>

      <!-- Charts -->
      <div class="row">
        <div class="col-xl-8" *ngIf="d.charts.collectedByMonth">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Installments paid per month <small>{{ d.year }} next to {{ d.year - 1 }}</small></h4>
            <app-column-chart [data]="d.charts.collectedByMonth" [colours]="['#cfe0a8', '#4f7a00']"></app-column-chart>
          </div></div>
        </div>
        <div [class]="d.charts.collectedByMonth ? 'col-xl-4' : 'col-xl-6'">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Students by study status</h4>
            <app-donut-chart [data]="d.charts.studyStatus" caption="students"></app-donut-chart>
          </div></div>
        </div>

        <div class="col-xl-6" *ngIf="d.charts.applicationFunnel">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Applications {{ d.year }} <small>how many reach each step</small></h4>
            <app-funnel-chart [data]="d.charts.applicationFunnel"></app-funnel-chart>
          </div></div>
        </div>
        <div class="col-xl-6" *ngIf="d.charts.overdueAgeing">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Overdue by how late <small>unpaid installments</small></h4>
            <app-bar-chart [data]="d.charts.overdueAgeing" colour="#c0262d" emptyText="Nothing overdue."></app-bar-chart>
          </div></div>
        </div>

        <div class="col-xl-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Students in the programme by chapter</h4>
            <app-bar-chart [data]="d.charts.studentsByChapter" emptyText="No students yet."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-6" *ngIf="d.charts.finance">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Rahbar income and expenses {{ d.year }} <small><a routerLink="/admin/finance">books</a></small></h4>
            <app-column-chart [data]="d.charts.finance" [money]="true" [colours]="['#15803d', '#c0262d']"
                              emptyText="Nothing entered in the books for this year."></app-column-chart>
          </div></div>
        </div>

        <div class="col-xl-6" *ngIf="d.reb as r">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Rahbar-e-Banaat <small><a routerLink="/admin/reb/dashboard">REB dashboard</a></small></h4>
            <div class="mini">
              <div><strong>{{ r.activeGirls }}</strong><span>girls studying</span></div>
              <div><strong>{{ r.attendanceToday === null ? '–' : r.attendanceToday + '%' }}</strong>
                <span>present today{{ r.attendanceMarked ? '' : ' (not marked yet)' }}</span></div>
              <div><strong>{{ money(r.openAmount) }}</strong><span>fees still open</span></div>
              <div><strong>{{ r.openInvoices }}</strong><span>bills not fully paid</span></div>
            </div>
          </div></div>
        </div>
        <div class="col-xl-6" *ngIf="d.recentActivity">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Latest activity <small><a routerLink="/admin/activity">all</a></small></h4>
            <ul class="list-unstyled mb-0 activity">
              <li *ngIf="!d.recentActivity.length" class="text-muted">Nothing yet.</li>
              <li *ngFor="let a of d.recentActivity" class="d-flex justify-content-between">
                <span><strong>{{ a.user_name || 'System' }}</strong> {{ a.action }}</span>
                <small class="text-muted text-nowrap ml-2">{{ a.created_at | appDate: 'datetime' }}</small>
              </li>
            </ul>
          </div></div>
        </div>
      </div>
    </ng-container>
  `
})
export class AdminDashboardComponent implements OnInit {
  d: Overview | null = null;
  year: number | null = null;
  error = '';
  modules: typeof MODULE_DASHBOARDS = [];

  readonly money = inr;

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.modules = MODULE_DASHBOARDS.filter((m) => this.auth.can(m.section));
    this.load();
  }

  load(): void {
    this.error = '';
    this.api.get<Overview>('/admin/dashboard', this.year ? { year: this.year } : undefined).subscribe({
      next: (d) => { this.d = d; this.year = d.year; },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }

  get waiting(): AttentionItem[] { return (this.d?.attention ?? []).filter((a) => a.count > 0); }
  get cleared(): AttentionItem[] { return (this.d?.attention ?? []).filter((a) => !a.count); }
  get clearedLabels(): string { return this.cleared.map((a) => a.label.toLowerCase()).join(', ') + '.'; }

  k(key: string): number | null { return this.d?.kpis[key] ?? null; }
  has(key: string): boolean { return !!this.d && key in this.d.kpis; }

  growth(now: string, before: string): string {
    const a = Number(this.k(now)) || 0, b = Number(this.k(before)) || 0;
    if (!b) return a ? 'nothing paid by now last year' : 'nothing yet';
    const pct = Math.round(((a - b) / b) * 100);
    return (pct >= 0 ? '▲ ' : '▼ ') + Math.abs(pct) + '% vs same time last year (' + b + ')';
  }
  trend(now: string, before: string): 'up' | 'down' | null {
    const a = Number(this.k(now)) || 0, b = Number(this.k(before)) || 0;
    return !b ? null : a >= b ? 'up' : 'down';
  }
  rateTone(rate: number | null): 'success' | 'warning' | 'danger' | 'info' {
    return rate === null ? 'info' : rate >= 90 ? 'success' : rate >= 70 ? 'warning' : 'danger';
  }
}
