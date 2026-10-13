import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { AppDatePipe } from '../../shared/app-date';
import { BarChartComponent, BarDatum } from '../../shared/bar-chart/bar-chart.component';
import { ColumnChartComponent, SeriesData } from '../../shared/charts/column-chart.component';
import { DonutChartComponent } from '../../shared/charts/donut-chart.component';
import { FunnelChartComponent } from '../../shared/charts/funnel-chart.component';
import { StatTileComponent } from '../../shared/charts/stat-tile.component';
import { inr } from '../../shared/charts/money';

interface Insights {
  from: string; to: string;
  girls?: {
    byClass?: BarDatum[]; age: BarDatum[]; districts: BarDatum[]; residence: BarDatum[]; feePlans: BarDatum[];
    categories?: BarDatum[]; joinedLeft: SeriesData; leavingReasons: BarDatum[];
  };
  attendance?: {
    today: number | null; todayMarked: number; session: number | null; schoolDays: number;
    last30: SeriesData; byMonth: SeriesData; byClassThisMonth: BarDatum[]; byStatus: BarDatum[];
    lowCount: number; lowThreshold: number; low: { id: number; name: string; class_name: string; percent: number; days: number }[];
  };
  exams?: {
    status: Record<string, number>;
    upcoming: { id: number; name: string; kind: string; start_date: string; end_date: string | null; status: string }[];
    marksEntry: { id: number; name: string; entered: number; expected: number }[];
    trend: SeriesData;
    latest?: {
      id: number; name: string; kind: string; published_at: string | null; girls: number; passed: number; failed: number;
      passRate: number | null; average: number | null; top: number | null; madrasaAverage: number | null; schoolAverage: number | null;
      absentPapers: number; grades: BarDatum[]; byClass: BarDatum[]; bySubject: BarDatum[];
      toppers: { id: number; name: string; class_name: string; percent: number; grade: string }[];
    };
  };
  quran?: {
    lessons30: number; girls30: number; parasCompleted: number; noLesson14: number;
    byKind: BarDatum[]; quality: BarDatum[]; parasReached: BarDatum[];
  };
  fees?: {
    byMonth: SeriesData; billed: number; paid: number; concessions: number; collectionRate: number | null;
    shares: BarDatum[]; byPayer: BarDatum[]; methods: BarDatum[]; overdueAgeing: BarDatum[]; openByClass?: BarDatum[];
  };
  enquiries?: { waiting: number; funnel: BarDatum[]; bySource: BarDatum[]; byClass: BarDatum[]; byMonth: SeriesData };
  hostel?: { outNow: number; lateBack: number; thisMonth: number };
  staff?: { byRole: BarDatum[]; subjectsWithoutTeacher?: number; girlsPerTeacher?: { girls: number; teachers: number } };
}

interface Dashboard {
  session: { name: string; start_date: string; end_date: string } | null;
  active: number; residential: number; fee_plans: Record<string, number>;
  admitted_this_session?: number; left_this_session?: number; not_enrolled?: number; sections_without_teacher?: number;
  without_guardian_phone: number; without_birth_certificate?: number;
  sections?: { id: number; label: string; girls: number; capacity: number; class_teacher: string }[];
  fees?: { label: string; billed: number; bills_made: boolean; collected: number; overdue: number; girls_overdue: number } | null;
  insights?: Insights | null;
}

const EXAM_KINDS: Record<string, string> = { UNIT_TEST: 'Unit test', TERM: 'Term', ANNUAL: 'Annual', ORAL: 'Oral' };
const EXAM_STATUS: Record<string, string> = { DRAFT: 'Draft', MARKS_ENTRY: 'Marks entry', PUBLISHED: 'Published' };

/**
 * Rahbar-e-Banaat > REB Dashboard: the current session at a glance. What needs attention, then girls, attendance,
 * exams, Quran / Hifz, fees, admissions, hostel and staff in figures and charts (each part only with its permission).
 */
@Component({
  selector: 'app-reb-dashboard',
  standalone: true,
  imports: [AppDatePipe, CommonModule, RouterLink, AlertsComponent, CardTableDirective, BarChartComponent, ColumnChartComponent,
    DonutChartComponent, FunnelChartComponent, StatTileComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .card .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
    h5.section { margin: 8px 0 12px; display: flex; align-items: center; gap: 8px; }
    h5.section a { font-size: 13px; font-weight: normal; }
    .attention { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; }
    .att { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: var(--radius-sm, 10px); background: var(--surface, #fff);
           box-shadow: var(--shadow-sm); border: 1px solid var(--line, #e5e9e4); border-left: 4px solid var(--warning, #b45309); color: inherit; }
    a.att:hover { text-decoration: none; background: #fdf3e3; }
    .att .n { font-size: 22px; font-weight: 700; min-width: 42px; text-align: center; color: #17231c; }
    .att .t { font-size: 13px; color: #3d4a43; line-height: 1.3; }
    .clear { font-size: 13px; color: #15803d; }
    .progress { height: 8px; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Rahbar-e-Banaat</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <ng-container *ngIf="d">
      <div *ngIf="!d.session" class="alert alert-info">
        No session is set up yet. <a *ngIf="canSetup" routerLink="/admin/reb/setup">Open REB Setup</a> to add the session, classes, sections and subjects.
      </div>
      <p *ngIf="d.session" class="text-muted">Session <strong>{{ d.session.name }}</strong>: {{ d.session.start_date | appDate }} to {{ d.session.end_date | appDate }}</p>

      <!-- Needs attention -->
      <h5 class="section">Needs attention</h5>
      <div class="attention mb-2">
        <ng-container *ngFor="let a of waiting">
          <a *ngIf="a.link; else plainItem" class="att" [routerLink]="a.link" [queryParams]="a.query || null">
            <span class="n">{{ a.count }}</span><span class="t">{{ a.label }}</span>
          </a>
          <ng-template #plainItem><div class="att"><span class="n">{{ a.count }}</span><span class="t">{{ a.label }}</span></div></ng-template>
        </ng-container>
      </div>
      <p class="clear mb-4" *ngIf="cleared.length"><i class="mdi mdi-check-circle"></i> All clear: {{ clearedLabels }}</p>

      <!-- Headline numbers -->
      <div class="tiles">
        <app-stat-tile label="Girls studying" icon="mdi-human-female" [value]="d.active"
                       [sub]="d.residential + ' in the hostel · ' + (d.active - d.residential) + ' day scholars'"
                       [link]="canStudents ? '/admin/reb/students' : null" linkText="Girls"></app-stat-tile>
        <app-stat-tile *ngIf="d.session" label="This session" icon="mdi-swap-vertical" tone="info" [value]="'+' + (d.admitted_this_session ?? 0)"
                       [sub]="(d.admitted_this_session ?? 0) + ' admitted · ' + (d.left_this_session ?? 0) + ' left'"></app-stat-tile>
        <app-stat-tile *ngIf="ins?.attendance as a" label="Attendance today" icon="mdi-calendar-check" [tone]="rateTone(a.today, 90, 80)"
                       [value]="a.todayMarked ? pct(a.today) : 'Not marked'" [sub]="'Session: ' + pct(a.session) + ' over ' + a.schoolDays + ' school days'"
                       link="/admin/reb/attendance" linkText="Attendance"></app-stat-tile>
        <app-stat-tile *ngIf="ins?.exams?.latest as l" label="Last exam: pass rate" icon="mdi-school" [tone]="rateTone(l.passRate, 90, 75)"
                       [value]="pct(l.passRate)" [sub]="l.name + ' · average ' + pct(l.average)" link="/admin/reb/exams" linkText="Exams"></app-stat-tile>
        <app-stat-tile *ngIf="ins?.quran as q" label="Paras completed" icon="mdi-book-open-variant" tone="purple" [value]="q.parasCompleted"
                       [sub]="q.lessons30 + ' lessons by ' + q.girls30 + ' girls in 30 days'" link="/admin/reb/quran" linkText="Quran / Hifz"></app-stat-tile>
        <ng-container *ngIf="d.fees as f">
          <app-stat-tile [label]="'Billed for ' + f.label" icon="mdi-file-document-outline" [value]="money(f.billed)"
                         [sub]="f.bills_made ? 'collected this month: ' + money(f.collected) : 'bills not made yet'"
                         link="/admin/reb/fees" linkText="Fees"></app-stat-tile>
          <app-stat-tile label="Fees overdue" icon="mdi-alarm" [tone]="f.overdue > 0 ? 'danger' : 'success'" [value]="money(f.overdue)"
                         [sub]="f.girls_overdue + ' girls'" link="/admin/reb/fees" linkText="Dues"></app-stat-tile>
        </ng-container>
        <app-stat-tile *ngIf="ins?.fees as f" label="Session collection" icon="mdi-percent" [tone]="rateTone(f.collectionRate, 90, 70)"
                       [value]="pct(f.collectionRate)" [sub]="money(f.paid) + ' of ' + money(f.billed) + ' billed'"></app-stat-tile>
        <app-stat-tile *ngIf="ins?.staff?.girlsPerTeacher as g" label="Girls per teacher" icon="mdi-account-tie" tone="info"
                       [value]="g.teachers ? (g.girls / g.teachers | number: '1.0-1') : '–'" [sub]="g.teachers + ' teachers'"
                       link="/admin/reb/staff" linkText="Staff"></app-stat-tile>
      </div>

      <!-- Classes and girls -->
      <div class="row">
        <div class="col-xl-7">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Classes</h4>
            <div class="table-responsive"><table class="table table-sm table-centered mb-0">
              <thead><tr><th>Class</th><th>Girls</th><th style="width: 30%">Seats</th><th>Class teacher</th></tr></thead>
              <tbody>
                <tr *ngIf="!d.sections?.length"><td colspan="4" class="text-center text-muted">No sections yet.</td></tr>
                <tr *ngFor="let s of d.sections">
                  <td><a *ngIf="canStudents; else plain" routerLink="/admin/reb/students">{{ s.label }}</a><ng-template #plain>{{ s.label }}</ng-template></td>
                  <td>{{ s.girls }}<span *ngIf="s.capacity" class="text-muted"> / {{ s.capacity }}</span></td>
                  <td><div *ngIf="s.capacity" class="progress" [title]="fill(s) + '% full'">
                    <div class="progress-bar" [class.bg-danger]="fill(s) > 100" [class.bg-warning]="fill(s) > 90 && fill(s) <= 100" [style.width.%]="min(fill(s), 100)"></div>
                  </div></td>
                  <td>{{ s.class_teacher || '--' }}</td>
                </tr>
              </tbody>
            </table></div>
          </div></div>
        </div>
        <div class="col-xl-5" *ngIf="ins?.girls as g">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Who pays the fees</h4>
            <app-donut-chart [data]="g.feePlans" caption="girls" [colours]="['#4f7a00', '#6d28d9', '#0e7490']"></app-donut-chart>
          </div></div>
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Hostel and day scholars</h4>
            <app-donut-chart [data]="g.residence" caption="girls" [colours]="['#0e7490', '#d97706']"></app-donut-chart>
          </div></div>
        </div>
      </div>

      <ng-container *ngIf="ins?.girls as g">
        <h5 class="section">Girls <a *ngIf="canStudents" routerLink="/admin/reb/students">all girls →</a></h5>
        <div class="row">
          <div class="col-xl-4" *ngIf="g.byClass">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Girls per class</h4>
              <app-bar-chart [data]="g.byClass" emptyText="No girls in a class yet."></app-bar-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Age</h4>
              <app-bar-chart [data]="g.age" colour="#0e7490" emptyText="No girls yet."></app-bar-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Home district <small>top 10</small></h4>
              <app-bar-chart [data]="g.districts" colour="#6d28d9" emptyText="No girls yet."></app-bar-chart>
            </div></div>
          </div>
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Admitted and left per month <small>this session</small></h4>
              <app-column-chart [data]="g.joinedLeft" [colours]="['#4f7a00', '#c0262d']" emptyText="No admissions or leavers this session."></app-column-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card" *ngIf="g.categories"><div class="card-body">
              <h4 class="header-title mb-3">Social category</h4>
              <app-donut-chart [data]="g.categories" caption="girls"></app-donut-chart>
            </div></div>
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Why girls left <small>this session</small></h4>
              <app-bar-chart [data]="g.leavingReasons" colour="#c0262d" emptyText="Nobody left this session."></app-bar-chart>
            </div></div>
          </div>
        </div>
      </ng-container>

      <!-- Attendance -->
      <ng-container *ngIf="ins?.attendance as a">
        <h5 class="section">Attendance <a routerLink="/admin/reb/attendance">mark attendance →</a></h5>
        <div class="row">
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Present each day <small>last 30 days, days marked</small></h4>
              <app-column-chart [data]="a.last30" unit="%" [max100]="true" emptyText="No attendance marked in the last 30 days."></app-column-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">This session <small>all marks</small></h4>
              <app-donut-chart [data]="a.byStatus" caption="marks" [colours]="['#15803d', '#c0262d', '#d97706', '#0e7490']"
                               emptyText="No attendance marked yet."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Present per month <small>this session</small></h4>
              <app-column-chart [data]="a.byMonth" unit="%" [max100]="true" [colours]="['#0e7490']" emptyText="No attendance marked yet."></app-column-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Present per class <small>this month</small></h4>
              <app-bar-chart [data]="a.byClassThisMonth" unit="%" [max100]="true" emptyText="Nothing marked this month."></app-bar-chart>
            </div></div>
          </div>
          <div class="col-12">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Girls below {{ a.lowThreshold }}% attendance <small>this session, lowest first</small>
                <span class="badge ml-1" [ngClass]="a.lowCount ? 'badge-warning' : 'badge-success'">{{ a.lowCount }}</span></h4>
              <p *ngIf="!a.low.length" class="text-muted mb-0">Every girl is at {{ a.lowThreshold }}% or above. </p>
              <div class="table-responsive" *ngIf="a.low.length"><table class="table table-sm table-centered mb-0">
                <thead><tr><th>Girl</th><th>Class</th><th style="width: 40%">Attendance</th><th class="text-right">Days marked</th></tr></thead>
                <tbody>
                  <tr *ngFor="let g of a.low">
                    <td><a *ngIf="canStudents; else nm" [routerLink]="['/admin/reb/students', g.id]">{{ g.name }}</a><ng-template #nm>{{ g.name }}</ng-template></td>
                    <td>{{ g.class_name || '--' }}</td>
                    <td><div class="d-flex align-items-center"><div class="progress flex-grow-1 mr-2"><div class="progress-bar bg-danger" [style.width.%]="g.percent"></div></div>{{ g.percent }}%</div></td>
                    <td class="text-right">{{ g.days }}</td>
                  </tr>
                </tbody>
              </table></div>
              <p *ngIf="a.lowCount > a.low.length" class="small text-muted mt-2 mb-0">and {{ a.lowCount - a.low.length }} more.</p>
            </div></div>
          </div>
        </div>
      </ng-container>

      <!-- Exams -->
      <ng-container *ngIf="ins?.exams as e">
        <h5 class="section">Exams &amp; results <a routerLink="/admin/reb/exams">exams →</a></h5>
        <div class="tiles">
          <app-stat-tile *ngFor="let s of examStatusKeys" [label]="examStatus[s]" icon="mdi-clipboard-text" [tone]="s === 'MARKS_ENTRY' ? 'warning' : s === 'PUBLISHED' ? 'success' : 'info'"
                         [value]="e.status[s] || 0" sub="exams this session"></app-stat-tile>
          <ng-container *ngIf="e.latest as l">
            <app-stat-tile label="Last exam: average" icon="mdi-chart-line" tone="primary" [value]="pct(l.average)" [sub]="'top score ' + pct(l.top)"></app-stat-tile>
            <app-stat-tile label="Failed" icon="mdi-alert-circle" [tone]="l.failed ? 'danger' : 'success'" [value]="l.failed"
                           [sub]="'of ' + l.girls + ' girls · ' + l.absentPapers + ' papers missed'"></app-stat-tile>
          </ng-container>
        </div>

        <div class="row">
          <div class="col-xl-6" *ngIf="e.marksEntry.length || e.upcoming.length">
            <div class="card"><div class="card-body">
              <ng-container *ngIf="e.marksEntry.length">
                <h4 class="header-title mb-3">Marks entered so far</h4>
                <div *ngFor="let m of e.marksEntry" class="mb-3">
                  <div class="d-flex justify-content-between small"><span>{{ m.name }}</span><span>{{ m.entered }} of {{ m.expected }} ({{ share(m.entered, m.expected) }}%)</span></div>
                  <div class="progress"><div class="progress-bar" [style.width.%]="share(m.entered, m.expected)"></div></div>
                </div>
              </ng-container>
              <ng-container *ngIf="e.upcoming.length">
                <h4 class="header-title mb-2 mt-2">Coming up</h4>
                <ul class="list-unstyled mb-0">
                  <li *ngFor="let x of e.upcoming" class="d-flex justify-content-between py-1 border-bottom">
                    <span>{{ x.name }} <small class="text-muted">{{ examKinds[x.kind] || x.kind }}</small></span>
                    <span class="text-nowrap">{{ x.start_date | appDate }} <span class="badge badge-light ml-1">{{ examStatus[x.status] || x.status }}</span></span>
                  </li>
                </ul>
              </ng-container>
            </div></div>
          </div>
          <div [class]="e.marksEntry.length || e.upcoming.length ? 'col-xl-6' : 'col-12'">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Results across the session's exams</h4>
              <app-column-chart [data]="e.trend" unit="%" [max100]="true" [colours]="['#4f7a00', '#0e7490']"
                                emptyText="No exam results published this session yet."></app-column-chart>
            </div></div>
          </div>
        </div>

        <ng-container *ngIf="e.latest as l">
          <div class="row">
            <div class="col-xl-4 col-md-6">
              <div class="card"><div class="card-body">
                <h4 class="header-title mb-3">Grades <small>{{ l.name }}</small></h4>
                <app-donut-chart [data]="l.grades" caption="girls"></app-donut-chart>
              </div></div>
            </div>
            <div class="col-xl-4 col-md-6">
              <div class="card"><div class="card-body">
                <h4 class="header-title mb-3">Average per class</h4>
                <app-bar-chart [data]="l.byClass" unit="%" [max100]="true"></app-bar-chart>
                <p class="small text-muted mt-3 mb-0" *ngIf="l.madrasaAverage !== null || l.schoolAverage !== null">
                  Madrasa subjects: <strong>{{ pct(l.madrasaAverage) }}</strong> · School subjects: <strong>{{ pct(l.schoolAverage) }}</strong></p>
              </div></div>
            </div>
            <div class="col-xl-4">
              <div class="card"><div class="card-body">
                <h4 class="header-title mb-3">Average per subject <small>weakest at the bottom</small></h4>
                <app-bar-chart [data]="l.bySubject" unit="%" [max100]="true" colour="#6d28d9"></app-bar-chart>
              </div></div>
            </div>
            <div class="col-12">
              <div class="card"><div class="card-body">
                <h4 class="header-title mb-3">Top 5 <small>{{ l.name }}</small></h4>
                <div class="table-responsive"><table class="table table-sm table-centered mb-0">
                  <thead><tr><th>#</th><th>Girl</th><th>Class</th><th class="text-right">Percent</th><th>Grade</th></tr></thead>
                  <tbody>
                    <tr *ngFor="let t of l.toppers; let i = index">
                      <td>{{ i + 1 }}</td>
                      <td><a *ngIf="canStudents; else tn" [routerLink]="['/admin/reb/students', t.id]">{{ t.name }}</a><ng-template #tn>{{ t.name }}</ng-template></td>
                      <td>{{ t.class_name || '--' }}</td><td class="text-right">{{ t.percent | number: '1.0-1' }}%</td><td>{{ t.grade }}</td>
                    </tr>
                  </tbody>
                </table></div>
              </div></div>
            </div>
          </div>
        </ng-container>
      </ng-container>

      <!-- Quran -->
      <ng-container *ngIf="ins?.quran as q">
        <h5 class="section">Quran / Hifz <a routerLink="/admin/reb/quran">progress →</a></h5>
        <div class="row">
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Lessons <small>last 30 days</small></h4>
              <app-donut-chart [data]="q.byKind" caption="lessons" emptyText="No lessons recorded in 30 days."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">How well they recited <small>last 30 days</small></h4>
              <app-donut-chart [data]="q.quality" caption="lessons" [colours]="['#15803d', '#0e7490', '#d97706']"
                               emptyText="No lessons recorded in 30 days."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">How far the girls have reached</h4>
              <app-bar-chart [data]="q.parasReached" colour="#6d28d9" emptyText="No lessons recorded yet."></app-bar-chart>
            </div></div>
          </div>
        </div>
      </ng-container>

      <!-- Fees -->
      <ng-container *ngIf="ins?.fees as f">
        <h5 class="section">Fees <a routerLink="/admin/reb/fees">fees →</a></h5>
        <div class="row">
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Billed and collected per month <small>this session</small></h4>
              <app-column-chart [data]="f.byMonth" [money]="true" [colours]="['#d97706', '#15803d']" emptyText="No bills or payments this session."></app-column-chart>
              <p class="small text-muted mt-2 mb-0" *ngIf="+f.concessions > 0">Concessions given this session: <strong>{{ money(f.concessions) }}</strong></p>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Who the bills are for</h4>
              <app-donut-chart [data]="f.shares" [money]="true" caption="billed" [colours]="['#6d28d9', '#4f7a00']" emptyText="No bills this session."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Paid by</h4>
              <app-donut-chart [data]="f.byPayer" [money]="true" caption="paid" [colours]="['#4f7a00', '#6d28d9']" emptyText="No payments this session."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">How they paid</h4>
              <app-donut-chart [data]="f.methods" [money]="true" caption="paid" emptyText="No payments this session."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Overdue by how late</h4>
              <app-bar-chart [data]="f.overdueAgeing" [money]="true" colour="#c0262d" emptyText="Nothing overdue."></app-bar-chart>
              <ng-container *ngIf="f.openByClass?.length">
                <h4 class="header-title mb-3 mt-4">Unpaid per class</h4>
                <app-bar-chart [data]="f.openByClass!" [money]="true" colour="#d97706"></app-bar-chart>
              </ng-container>
            </div></div>
          </div>
        </div>
      </ng-container>

      <!-- Admissions, hostel, staff -->
      <div class="row">
        <ng-container *ngIf="ins?.enquiries as q">
          <div class="col-12"><h5 class="section">Admission enquiries <small class="text-muted">last 12 months</small> <a routerLink="/admin/reb/enquiries">enquiries →</a></h5></div>
          <div class="col-xl-4">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">From enquiry to admission</h4>
              <app-funnel-chart [data]="q.funnel" emptyText="No enquiries in the last 12 months."></app-funnel-chart>
            </div></div>
          </div>
          <div class="col-xl-8">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Enquiries per month</h4>
              <app-column-chart [data]="q.byMonth" [colours]="['#0e7490']" emptyText="No enquiries in the last 12 months."></app-column-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">How they heard of us</h4>
              <app-donut-chart [data]="q.bySource" caption="enquiries" emptyText="No enquiries yet."></app-donut-chart>
            </div></div>
          </div>
          <div class="col-xl-4 col-md-6">
            <div class="card"><div class="card-body">
              <h4 class="header-title mb-3">Class asked for</h4>
              <app-bar-chart [data]="q.byClass" colour="#6d28d9" emptyText="No enquiries yet."></app-bar-chart>
            </div></div>
          </div>
        </ng-container>
        <div class="col-xl-4" *ngIf="ins?.staff as s">
          <div class="card"><div class="card-body">
            <h4 class="header-title mb-3">Staff <small><a routerLink="/admin/reb/staff">staff →</a></small></h4>
            <app-bar-chart [data]="s.byRole" colour="#0e7490" emptyText="No staff added yet."></app-bar-chart>
          </div></div>
        </div>
      </div>
    </ng-container>
  `
})
export class RebDashboardComponent implements OnInit {
  readonly examKinds = EXAM_KINDS;
  readonly examStatus = EXAM_STATUS;
  readonly examStatusKeys = Object.keys(EXAM_STATUS);
  readonly money = inr;
  readonly min = Math.min;
  d: Dashboard | null = null;
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get ins(): Insights | null | undefined { return this.d?.insights; }
  get canSetup(): boolean { return this.auth.can('REB_SETUP'); }
  get canStudents(): boolean { return this.auth.can('REB_STUDENTS'); }

  /** Everything waiting for someone, with a link to the screen where it is done. */
  get attention(): { label: string; count: number; link?: string; query?: Record<string, string> }[] {
    const d = this.d!;
    const i = d.insights;
    const out: { label: string; count: number; link?: string; query?: Record<string, string> }[] = [
      { label: 'Girls not in a class this session', count: d.not_enrolled ?? 0, link: this.canStudents ? '/admin/reb/students' : undefined },
      { label: 'Girls without a guardian phone number', count: d.without_guardian_phone, link: this.canStudents ? '/admin/reb/students' : undefined },
      { label: 'Classes without a class teacher', count: d.sections_without_teacher ?? 0, link: this.canSetup ? '/admin/reb/setup' : undefined }
    ];
    if (d.without_birth_certificate != null) out.push({ label: 'Girls without a birth certificate uploaded', count: d.without_birth_certificate });
    if (i?.attendance) {
      out.push({ label: `Girls below ${i.attendance.lowThreshold}% attendance`, count: i.attendance.lowCount, link: '/admin/reb/attendance' });
    }
    if (i?.exams) out.push({ label: 'Exams waiting for marks', count: i.exams.status['MARKS_ENTRY'] ?? 0, link: '/admin/reb/exams' });
    if (i?.quran) out.push({ label: 'Hifz girls without a lesson in 14 days', count: i.quran.noLesson14, link: '/admin/reb/quran' });
    if (d.fees) out.push({ label: 'Girls with fees overdue', count: d.fees.girls_overdue, link: '/admin/reb/fees', query: { tab: 'dues' } });
    if (i?.enquiries) out.push({ label: 'New admission enquiries to answer', count: i.enquiries.waiting, link: '/admin/reb/enquiries' });
    if (i?.hostel) {
      out.push({ label: 'Girls late back from hostel leave', count: i.hostel.lateBack, link: '/admin/reb/hostel' });
    }
    if (i?.staff?.subjectsWithoutTeacher != null) out.push({ label: 'Subjects without a teacher', count: i.staff.subjectsWithoutTeacher, link: '/admin/reb/setup' });
    return out;
  }
  get waiting() { return this.attention.filter((a) => a.count > 0); }
  get cleared() { return this.attention.filter((a) => !a.count); }
  get clearedLabels(): string { return this.cleared.map((a) => a.label.toLowerCase()).join(', ') + '.'; }

  pct(v: number | null | undefined): string { return v === null || v === undefined ? '–' : v + '%'; }
  share(part: number, whole: number): number { return whole ? Math.min(100, Math.round((part / whole) * 100)) : 0; }
  fill(s: { girls: number; capacity: number }): number { return s.capacity ? Math.round((s.girls / s.capacity) * 100) : 0; }
  rateTone(rate: number | null | undefined, good: number, ok: number): 'success' | 'warning' | 'danger' | 'info' {
    return rate === null || rate === undefined ? 'info' : rate >= good ? 'success' : rate >= ok ? 'warning' : 'danger';
  }

  ngOnInit(): void {
    this.api.get<Dashboard>('/admin/reb/dashboard').subscribe({
      next: (r) => (this.d = r),
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }
}
