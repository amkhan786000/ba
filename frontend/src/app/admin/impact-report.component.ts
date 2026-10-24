import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { StatTileComponent } from '../shared/charts/stat-tile.component';
import { DonutChartComponent, Slice } from '../shared/charts/donut-chart.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';

type Counts = Record<string, number>;
interface Scholarship {
  students: number; girls: number; girls_percent: number; by_study_status: Counts; sponsored_now: number; sponsors: number;
  by_chapter: Counts; courses: Counts; institutions: number; installments_paid: number; progress_reports: number;
  average_latest_marks: number | null; graduates: number; graduate_outcomes: Counts;
}
interface Coaching {
  centers: number; students: number; studying_now: number; girls_percent: number; by_class: Counts; exams: number; prizes: number;
  average_exam_percent: number | null; attendance_percent: number | null; class_10: number; class_10_applied: number; class_10_accepted: number;
}
interface Impact { year: number | null; years: number[]; scope: string | null; generated_on: string; scholarship: Scholarship; coaching?: Coaching }

/**
 * Reports > Impact Report: what Rahbar achieved in a session year (or all years), in counts only, for sponsors, CSR
 * partners and the board. On screen and as a PDF. A chapter lead sees their chapter's.
 */
@Component({
  selector: 'app-impact-report',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, StatTileComponent, DonutChartComponent, BarChartComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; margin-bottom: 20px; }
    h5.section { margin: 8px 0 12px; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex flex-wrap justify-content-between align-items-center" style="gap: 8px">
      <h4 class="page-title">Impact Report <small *ngIf="d?.scope" class="text-muted">· {{ d!.scope }}</small></h4>
      <div class="d-flex" style="gap: 8px">
        <select class="form-control w-auto" [(ngModel)]="year" (ngModelChange)="load()" aria-label="Session year">
          <option [ngValue]="null">All years</option>
          <option *ngFor="let y of d?.years" [ngValue]="y">Session {{ y }}</option>
        </select>
        <button class="btn btn-primary" (click)="pdf()" [disabled]="busy"><i class="mdi mdi-file-pdf-box mr-1"></i>Download PDF</button>
      </div>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p class="text-muted small">Counts only, no amounts: ready to share with sponsors, CSR partners and the board.</p>

    <ng-container *ngIf="d">
      <h5 class="section">Rahbar scholarship</h5>
      <div class="tiles">
        <app-stat-tile label="Students" icon="mdi-school" [value]="d.scholarship.students" [sub]="d.scholarship.girls_percent + '% girls'"></app-stat-tile>
        <app-stat-tile label="Sponsored now" icon="mdi-hand-heart" tone="success" [value]="d.scholarship.sponsored_now" [sub]="d.scholarship.sponsors + ' sponsors'"></app-stat-tile>
        <app-stat-tile label="Institutions" icon="mdi-domain" tone="info" [value]="d.scholarship.institutions"></app-stat-tile>
        <app-stat-tile label="Installments paid" icon="mdi-check-decagram" tone="purple" [value]="d.scholarship.installments_paid"></app-stat-tile>
        <app-stat-tile label="Average latest marks" icon="mdi-chart-line" [value]="d.scholarship.average_latest_marks != null ? d.scholarship.average_latest_marks + '%' : null"
                       [sub]="d.scholarship.progress_reports + ' progress reports'"></app-stat-tile>
        <app-stat-tile label="Graduates" icon="mdi-account-school" tone="success" [value]="d.scholarship.graduates"></app-stat-tile>
      </div>
      <div class="row">
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">Where they are in their studies</h4>
          <app-donut-chart [data]="slices(d.scholarship.by_study_status)" caption="students"></app-donut-chart>
        </div></div></div>
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">What our graduates do now</h4>
          <app-donut-chart [data]="slices(d.scholarship.graduate_outcomes)" caption="graduates" emptyText="No graduates yet."></app-donut-chart>
        </div></div></div>
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">What they study</h4>
          <app-bar-chart [data]="bars(d.scholarship.courses)" emptyText="No courses assigned yet."></app-bar-chart>
        </div></div></div>
        <div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">By chapter</h4>
          <app-bar-chart [data]="bars(d.scholarship.by_chapter)" emptyText="No students."></app-bar-chart>
        </div></div></div>
      </div>

      <ng-container *ngIf="d.coaching as c">
        <h5 class="section">Coaching centers <small class="text-muted">(classes 7–10)</small></h5>
        <div class="tiles">
          <app-stat-tile label="Students" icon="mdi-account-group" [value]="c.students" [sub]="c.studying_now + ' studying now · ' + c.girls_percent + '% girls'"></app-stat-tile>
          <app-stat-tile label="Exams held" icon="mdi-clipboard-text" tone="purple" [value]="c.exams" [sub]="c.prizes + ' prizes awarded'"></app-stat-tile>
          <app-stat-tile label="Average exam marks" icon="mdi-chart-line" [value]="c.average_exam_percent != null ? c.average_exam_percent + '%' : null"></app-stat-tile>
          <app-stat-tile label="Attendance" icon="mdi-calendar-check" tone="info" [value]="c.attendance_percent != null ? c.attendance_percent + '%' : null"></app-stat-tile>
          <app-stat-tile label="Class 10 → Rahbar" icon="mdi-school-outline" tone="success" [value]="c.class_10_applied + ' of ' + c.class_10"
                         [sub]="'applied · ' + c.class_10_accepted + ' accepted'"></app-stat-tile>
        </div>
        <div class="row"><div class="col-lg-6"><div class="card"><div class="card-body">
          <h4 class="header-title">Students by class</h4>
          <app-bar-chart [data]="bars(c.by_class)" emptyText="No students."></app-bar-chart>
        </div></div></div></div>
      </ng-container>
      <p class="small text-muted">Generated {{ d.generated_on | appDate }}.</p>
    </ng-container>
  `
})
export class ImpactReportComponent implements OnInit {
  d: Impact | null = null;
  year: number | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Impact>('/admin/impact', { year: this.year }).subscribe({
      next: (d) => (this.d = d),
      error: (e) => (this.error = errorText(e, 'Could not load the report.'))
    });
  }

  slices(c: Counts): Slice[] { return Object.entries(c ?? {}).filter(([, v]) => v > 0).map(([label, value]) => ({ label, value })); }
  bars(c: Counts): BarDatum[] { return Object.entries(c ?? {}).map(([label, value]) => ({ label, value })); }

  pdf(): void {
    this.busy = true;
    this.api.download('/admin/impact/pdf', { year: this.year }, 'Rahbar-impact.pdf').subscribe({
      next: () => (this.busy = false),
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not download the PDF.'); }
    });
  }
}
