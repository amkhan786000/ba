import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';
import { ColumnChartComponent, SeriesData } from '../shared/charts/column-chart.component';
import { DonutChartComponent } from '../shared/charts/donut-chart.component';
import { StatTileComponent } from '../shared/charts/stat-tile.component';

interface StudentsDashboard {
  scope: 'all' | 'chapter' | 'rcc';
  kpis: Record<string, number | null>;
  progress: { year: number; students: number; reports: number; pending: number; expected: number };
  charts: {
    studyStatus: BarDatum[]; bySessionYear: BarDatum[]; bySex: BarDatum[]; byChapter: BarDatum[];
    byInstitution: BarDatum[]; byCourse: BarDatum[]; intakeAndExits: SeriesData; progressReviews: BarDatum[];
  };
}

/** Students > Students Dashboard: who is in the programme, where they study, how they progress and what is missing. */
@Component({
  selector: 'app-admin-students-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, BarChartComponent, ColumnChartComponent, DonutChartComponent, StatTileComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .card .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
    .progress { height: 10px; }
  `],
  template: `
    <div class="row mt-2"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Students Dashboard</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <ng-container *ngIf="d">
      <p *ngIf="d.scope !== 'all'" class="alert alert-info py-2">Showing the students of your {{ d.scope === 'rcc' ? 'RCC center' : 'chapter' }} only.</p>

      <div class="tiles">
        <app-stat-tile label="Studying" icon="mdi-school" [value]="k('studying')" [sub]="k('onHold') + ' on hold'" link="/admin/students" linkText="Directory"></app-stat-tile>
        <app-stat-tile label="Graduated" icon="mdi-account-star" tone="success" [value]="k('graduated')" [sub]="k('droppedOut') + ' dropped out'"></app-stat-tile>
        <app-stat-tile label="Completion rate" icon="mdi-flag-checkered" [tone]="rateTone(k('completionRate'))"
                       [value]="pct(k('completionRate'))" sub="graduated of those who finished or left"></app-stat-tile>
        <app-stat-tile label="Without a sponsor" icon="mdi-account-alert" [tone]="k('withoutSponsor') ? 'warning' : 'success'"
                       [value]="k('withoutSponsor')" sub="studying now" link="/admin/sponsorships" linkText="Map sponsors"></app-stat-tile>
        <app-stat-tile label="Without a course" icon="mdi-book-alert" [tone]="k('withoutCourse') ? 'warning' : 'success'"
                       [value]="k('withoutCourse')" sub="no college / course set" link="/admin/manage-students" linkText="Assign"></app-stat-tile>
        <app-stat-tile label="Without bank details" icon="mdi-bank-remove" [tone]="k('withoutBank') ? 'warning' : 'success'"
                       [value]="k('withoutBank')" sub="can't be paid yet" link="/admin/students"></app-stat-tile>
        <app-stat-tile label="Have a mentor" icon="mdi-account-tie-voice" tone="info" [value]="k('mentored')"
                       [sub]="pct(k('mentoredRate')) + ' of students in the programme'" link="/admin/mentoring"></app-stat-tile>
      </div>

      <div class="row">
        <div class="col-xl-4">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Study status <small>all students</small></h4>
            <app-donut-chart [data]="d.charts.studyStatus" caption="students"></app-donut-chart>
          </div></div>
        </div>
        <div class="col-xl-8">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Joined, graduated and dropped out per year <small>joined = session year</small></h4>
            <app-column-chart [data]="d.charts.intakeAndExits" [colours]="['#4f7a00', '#0e7490', '#c0262d']"
                              emptyText="No students yet."></app-column-chart>
          </div></div>
        </div>

        <div class="col-xl-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Progress reports {{ d.progress.year }}</h4>
            <p class="mb-1"><strong>{{ d.progress.students }}</strong> of {{ d.progress.expected }} studying students sent a report
              ({{ d.progress.reports }} reports, <strong>{{ d.progress.pending }}</strong> waiting for review).</p>
            <div class="progress mb-3"><div class="progress-bar bg-success" [style.width.%]="share(d.progress.students, d.progress.expected)"></div></div>
            <app-bar-chart [data]="d.charts.progressReviews" emptyText="No reports this year yet."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-3 col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Session year</h4>
            <app-bar-chart [data]="d.charts.bySessionYear" emptyText="No students yet."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-3 col-md-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Girls and boys</h4>
            <app-donut-chart [data]="d.charts.bySex" caption="students" [colours]="['#db2777', '#0e7490', '#6b776f']"></app-donut-chart>
          </div></div>
        </div>

        <div class="col-xl-4">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">By chapter</h4>
            <app-bar-chart [data]="d.charts.byChapter" emptyText="No students yet."></app-bar-chart>
          </div></div>
        </div>
      </div>

      <!-- Students per college and per course, for a chosen session year -->
      <div class="d-flex flex-wrap align-items-center mb-2">
        <h5 class="mb-0 mr-3">Colleges and courses</h5>
        <label for="enrolment-period" class="mb-0 mr-2 small text-muted">Students:</label>
        <select id="enrolment-period" class="form-control form-control-sm" style="width: auto" [(ngModel)]="period" (ngModelChange)="loadEnrolment()">
          <option value="now">Studying now</option>
          <option value="all">All years (everyone)</option>
          <option *ngFor="let y of enrolment?.years" [value]="'' + y">Session year {{ y }}</option>
        </select>
        <small class="text-muted ml-2">{{ periodNote }}</small>
      </div>
      <div class="row">
        <div class="col-xl-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Students by college</h4>
            <app-bar-chart [data]="enrolment?.byInstitution ?? d.charts.byInstitution" colour="#6d28d9" emptyText="No students for this choice."></app-bar-chart>
          </div></div>
        </div>
        <div class="col-xl-6">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Students by course</h4>
            <app-bar-chart [data]="enrolment?.byCourse ?? d.charts.byCourse" colour="#0e7490" emptyText="No students for this choice."></app-bar-chart>
          </div></div>
        </div>
      </div>
      <p class="small text-muted">For an Excel table with one column per session year, see Reports → "Students by institution and session year"
        and "Students by course and session year".</p>
      <p class="text-muted small">Charts count students in the programme (studying or on hold) unless they say otherwise.
        Detailed lists: <a routerLink="/admin/reports">Reports</a>.</p>
    </ng-container>
  `
})
export class AdminStudentsDashboardComponent implements OnInit {
  d: StudentsDashboard | null = null;
  error = '';
  /** "now" (studying or on hold), "all", or a session year, for the college / course charts. */
  period = 'now';
  enrolment: { years: number[]; byInstitution: BarDatum[]; byCourse: BarDatum[] } | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<StudentsDashboard>('/admin/dashboards/students').subscribe({
      next: (d) => (this.d = d),
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
    this.loadEnrolment();
  }

  loadEnrolment(): void {
    this.api.get<{ years: number[]; byInstitution: BarDatum[]; byCourse: BarDatum[] }>('/admin/dashboards/students/enrolment', { period: this.period })
      .subscribe({ next: (r) => (this.enrolment = r), error: (e) => (this.error = errorText(e, 'Could not load colleges and courses.')) });
  }

  get periodNote(): string {
    return this.period === 'now' ? 'students studying or on hold' : this.period === 'all' ? 'every student, any study status'
      : 'all students of that session year, whatever their study status now';
  }

  k(key: string): number | null { return this.d?.kpis[key] ?? null; }
  pct(v: number | null): string { return v === null ? '–' : v + '%'; }
  share(part: number, whole: number): number { return whole ? Math.min(100, (part / whole) * 100) : 0; }
  rateTone(rate: number | null): 'success' | 'warning' | 'danger' | 'info' {
    return rate === null ? 'info' : rate >= 85 ? 'success' : rate >= 65 ? 'warning' : 'danger';
  }
}
