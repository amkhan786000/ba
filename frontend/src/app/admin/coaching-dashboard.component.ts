import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { StatTileComponent } from '../shared/charts/stat-tile.component';
import { ColumnChartComponent, SeriesData } from '../shared/charts/column-chart.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';
import { CoachingScope } from './coaching-students.component';

interface Tiles {
  students: number; girls: number; boys: number; left: number; admitted_this_year: number; teachers: number;
  exams_this_year: number; average_percent_3_months: number | null;
}
interface RecentExam {
  id: number; name: string; exam_date: string; class_level: number; center_name: string | null; appeared: number;
  average_percent: number | null; prizes: { position: number; name: string }[];
}
interface CenterRow {
  rcc_center_id: number; center_name: string; students: number; class_7: number; class_8: number; class_9: number; class_10: number;
  teachers: number; exams_this_year: number; last_exam: string | null; average_percent_3_months: number | null;
}
interface Dashboard extends CoachingScope {
  center_id: number | null; center_name: string | null; tiles: Tiles; by_class: SeriesData; average_trend: SeriesData;
  admissions: SeriesData; recent_exams: RecentExam[]; centers_summary?: CenterRow[]; students_by_center?: BarDatum[];
}

/**
 * Coaching Center > Dashboard. The office sees all centers together (with a row per center) and can open any one
 * center; a coaching center coordinator or teacher sees only their own center.
 */
@Component({
  selector: 'app-coaching-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, AppDatePipe, StatTileComponent, ColumnChartComponent, BarChartComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
    tr.pick { cursor: pointer; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex flex-wrap justify-content-between align-items-center">
      <h4 class="page-title">Coaching Center Dashboard <small *ngIf="d?.center_name" class="text-muted">· {{ d!.center_name }}</small></h4>
      <select *ngIf="d && !d.scoped" class="form-control w-auto" [(ngModel)]="centerId" (ngModelChange)="load()" aria-label="Center">
        <option [ngValue]="null">All centers</option>
        <option *ngFor="let c of d.centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
      </select>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p *ngIf="d && !d.centers.length" class="text-muted">Your account is not linked to a coaching center. Ask the office to set it.</p>

    <ng-container *ngIf="d && d.centers.length">
      <div class="tiles">
        <app-stat-tile label="Students studying" icon="mdi-account-group" [value]="d.tiles.students"
                       [sub]="d.tiles.girls + ' girls · ' + d.tiles.boys + ' boys'" link="/admin/coaching/students" linkText="Students"></app-stat-tile>
        <app-stat-tile label="Admitted this year" icon="mdi-account-plus" tone="success" [value]="d.tiles.admitted_this_year"
                       [sub]="d.tiles.left + ' left so far'"></app-stat-tile>
        <app-stat-tile label="Teachers" icon="mdi-human-male-board" tone="info" [value]="d.tiles.teachers"
                       [link]="canTeachers ? '/admin/coaching/teachers' : null" linkText="Teachers"></app-stat-tile>
        <app-stat-tile label="Exams this year" icon="mdi-clipboard-text" tone="purple" [value]="d.tiles.exams_this_year"
                       [link]="canExams ? '/admin/coaching/exams' : null" linkText="Exams"></app-stat-tile>
        <app-stat-tile label="Average marks" icon="mdi-chart-line" [tone]="tone(d.tiles.average_percent_3_months)"
                       [value]="d.tiles.average_percent_3_months != null ? d.tiles.average_percent_3_months + '%' : null"
                       sub="exams of the last 3 months"></app-stat-tile>
      </div>

      <div class="card" *ngIf="d.centers_summary as rows"><div class="card-body">
        <h4 class="header-title">All centers <small>· click a center to open its dashboard</small></h4>
        <div class="table-responsive"><table class="table table-sm table-hover mb-0">
          <thead class="thead-light"><tr><th>Center</th><th class="text-right">Students</th><th class="text-right">7</th><th class="text-right">8</th>
            <th class="text-right">9</th><th class="text-right">10</th><th class="text-right">Teachers</th><th class="text-right">Exams this year</th>
            <th>Last exam</th><th class="text-right">Average (3 months)</th></tr></thead>
          <tbody>
            <tr *ngFor="let r of rows" class="pick" (click)="open(r.rcc_center_id)">
              <td><a href="" (click)="$event.preventDefault()">{{ r.center_name }}</a></td>
              <td class="text-right"><strong>{{ r.students }}</strong></td>
              <td class="text-right">{{ r.class_7 }}</td><td class="text-right">{{ r.class_8 }}</td>
              <td class="text-right">{{ r.class_9 }}</td><td class="text-right">{{ r.class_10 }}</td>
              <td class="text-right">{{ r.teachers }}</td><td class="text-right">{{ r.exams_this_year }}</td>
              <td>{{ r.last_exam ? (r.last_exam | appDate) : '-' }}</td>
              <td class="text-right">{{ r.average_percent_3_months != null ? r.average_percent_3_months + '%' : '-' }}</td>
            </tr>
          </tbody>
        </table></div>
      </div></div>

      <div class="row">
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">Students by class <small>· studying now</small></h4>
          <app-column-chart [data]="d.by_class" ariaLabel="Students by class and gender" emptyText="No students yet."></app-column-chart>
        </div></div></div>
        <div class="col-lg-6" *ngIf="d.students_by_center"><div class="card"><div class="card-body">
          <h4 class="header-title">Students by center</h4>
          <app-bar-chart [data]="d.students_by_center" emptyText="No students yet."></app-bar-chart>
        </div></div></div>
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">Average marks by month <small>· per class, last 12 months</small></h4>
          <app-column-chart [data]="d.average_trend" unit="%" [max100]="true" ariaLabel="Average marks per class by month" emptyText="No exam marks yet."></app-column-chart>
        </div></div></div>
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">Admissions by month <small>· last 12 months</small></h4>
          <app-column-chart [data]="d.admissions" ariaLabel="Admissions by month" emptyText="No admissions with a date yet."></app-column-chart>
        </div></div></div>
      </div>

      <div class="card"><div class="card-body">
        <h4 class="header-title">Recent exams and prize winners</h4>
        <p *ngIf="!d.recent_exams.length" class="text-muted mb-0">No exams yet.</p>
        <div class="table-responsive" *ngIf="d.recent_exams.length"><table class="table table-sm mb-0">
          <thead class="thead-light"><tr><th>Exam</th><th>Date</th><th>Class</th><th *ngIf="!d.center_id && !d.scoped">Center</th>
            <th class="text-right">Appeared</th><th class="text-right">Average</th><th>Prizes</th></tr></thead>
          <tbody><tr *ngFor="let e of d.recent_exams">
            <td>{{ e.name }}</td><td>{{ e.exam_date | appDate }}</td><td>{{ e.class_level }}</td>
            <td *ngIf="!d.center_id && !d.scoped">{{ e.center_name }}</td>
            <td class="text-right">{{ e.appeared }}</td>
            <td class="text-right">{{ e.average_percent != null ? e.average_percent + '%' : '-' }}</td>
            <td class="small"><span *ngFor="let p of e.prizes; let last = last"><i class="mdi mdi-trophy text-warning"></i> {{ ordinal(p.position) }} {{ p.name }}{{ last ? '' : ', ' }}</span>
              <span *ngIf="!e.prizes.length" class="text-muted">Not generated</span></td>
          </tr></tbody>
        </table></div>
      </div></div>
    </ng-container>
  `
})
export class CoachingDashboardComponent implements OnInit {
  d: Dashboard | null = null;
  centerId: number | null = null;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canTeachers(): boolean { return this.auth.can('COACHING_TEACHERS'); }
  get canExams(): boolean { return this.auth.can('COACHING_EXAMS'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Dashboard>('/admin/coaching/dashboard', { centerId: this.centerId }).subscribe({
      next: (d) => (this.d = d),
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }

  open(id: number): void { this.centerId = id; this.load(); window.scrollTo({ top: 0 }); }

  tone(p: number | null): 'success' | 'warning' | 'danger' | 'primary' {
    return p == null ? 'primary' : p >= 75 ? 'success' : p >= 50 ? 'warning' : 'danger';
  }

  ordinal(n: number): string { return n === 1 ? '1st' : n === 2 ? '2nd' : n === 3 ? '3rd' : n + 'th'; }
}
