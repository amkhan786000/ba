import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { CoachingCenter, CoachingScope } from './coaching-students.component';

interface RegisterRow { student_id: number; name: string; admission_no: string | null; status: 'P' | 'A' | 'L' | null; percent_30_days: number | null }
type Register = CoachingScope & { rcc_center_id: number; class_level: number; date: string; marked: boolean; rows: RegisterRow[]; students_per_class: Record<string, number> };
interface SummaryRow { student_id: number; name: string; class_level: number; center_name: string | null; present: number; absent: number; leave: number; percent: number | null }
interface Summary { from: string; to: string; rows: SummaryRow[] }

/**
 * Coaching Center > Attendance: the daily register of a class (Present / Absent / Leave), and each student's
 * attendance over a period, lowest first. Coordinators and coaching teachers mark it; others see it.
 */
@Component({
  selector: 'app-coaching-attendance',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, AppDatePipe],
  styles: [`
    .marks .btn { min-width: 38px; }
    .low { color: #b42318; font-weight: 600; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coaching Attendance</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <h4 class="header-title">{{ canEdit ? 'Mark attendance' : 'Attendance register' }}</h4>
      <p class="text-muted small">{{ canEdit ? 'Choose the class and the day, tap P, A or L for each student, then Save.' : 'You can see the register; marking it needs "Coaching center attendance: manage".' }}</p>
      <div class="d-flex flex-wrap align-items-end mb-3" style="gap: 10px">
        <div *ngIf="!scoped"><label class="small mb-0" for="ac">Center</label>
          <select id="ac" class="form-control" [(ngModel)]="centerId" (ngModelChange)="classLevel = null; loadRegister()">
            <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
          </select></div>
        <div><label class="small mb-0" for="acl">Class</label>
          <select id="acl" class="form-control" [(ngModel)]="classLevel" (ngModelChange)="loadRegister()">
            <option *ngFor="let c of classes" [ngValue]="c">Class {{ c }} ({{ reg?.students_per_class?.[c] ?? 0 }} students)</option>
          </select></div>
        <div><label class="small mb-0" for="ad">Date</label>
          <input id="ad" type="date" class="form-control" [max]="today" [(ngModel)]="date" (ngModelChange)="loadRegister()"></div>
        <button *ngIf="canEdit && reg?.rows?.length" class="btn btn-outline-success" (click)="all('P')"><i class="mdi mdi-check-all"></i> All present</button>
      </div>

      <p *ngIf="reg && !reg.marked && reg.rows.length" class="small text-warning mb-2"><i class="mdi mdi-alert-circle-outline"></i> Not marked yet for this day.</p>
      <div *ngIf="reg && !reg.rows.length" class="alert alert-light border mb-0">
        <ng-container *ngIf="totalStudents; else none">No studying students in class {{ classLevel }} here. Choose another class above.</ng-container>
        <ng-template #none>There are no coaching students at this center yet, so there is nobody to mark.
          <a routerLink="/admin/coaching/students">Add students in Coaching Center › Students</a> first.</ng-template>
      </div>
      <div class="table-responsive" *ngIf="reg?.rows?.length"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Student</th><th>{{ date | appDate }}</th><th class="text-right">Last 30 days</th></tr></thead>
        <tbody><tr *ngFor="let r of reg!.rows">
          <td>{{ r.name }} <span class="small text-muted">{{ r.admission_no }}</span></td>
          <td class="marks">
            <div class="btn-group btn-group-sm" *ngIf="canEdit; else shown">
              <button class="btn" [ngClass]="marks[r.student_id] === 'P' ? 'btn-success' : 'btn-outline-success'" (click)="set(r.student_id, 'P')" title="Present">P</button>
              <button class="btn" [ngClass]="marks[r.student_id] === 'A' ? 'btn-danger' : 'btn-outline-danger'" (click)="set(r.student_id, 'A')" title="Absent">A</button>
              <button class="btn" [ngClass]="marks[r.student_id] === 'L' ? 'btn-secondary' : 'btn-outline-secondary'" (click)="set(r.student_id, 'L')" title="Leave">L</button>
            </div>
            <ng-template #shown>{{ label(r.status) }}</ng-template>
          </td>
          <td class="text-right" [class.low]="r.percent_30_days != null && r.percent_30_days < 75">{{ r.percent_30_days != null ? r.percent_30_days + '%' : '-' }}</td>
        </tr></tbody>
      </table></div>
      <div class="mt-3" *ngIf="canEdit && reg?.rows?.length">
        <button class="btn btn-primary" (click)="save()" [disabled]="saving || !dirty">{{ saving ? 'Saving…' : 'Save attendance' }}</button>
        <span class="small text-muted ml-2">P present · A absent · L leave (leave doesn't lower the %)</span>
      </div>
    </div></div>

    <div class="card"><div class="card-body">
      <div class="d-flex flex-wrap align-items-end mb-2" style="gap: 10px">
        <h4 class="header-title mb-0 mr-auto">Attendance by student <small class="text-muted">· lowest first</small></h4>
        <div><label class="small mb-0" for="sf">From</label><input id="sf" type="date" class="form-control form-control-sm" [(ngModel)]="from" (ngModelChange)="loadSummary()"></div>
        <div><label class="small mb-0" for="st">To</label><input id="st" type="date" class="form-control form-control-sm" [max]="today" [(ngModel)]="to" (ngModelChange)="loadSummary()"></div>
        <div><label class="small mb-0" for="sc">Class</label>
          <select id="sc" class="form-control form-control-sm" [(ngModel)]="summaryClass" (ngModelChange)="loadSummary()">
            <option [ngValue]="null">All</option><option *ngFor="let c of classes" [ngValue]="c">{{ c }}</option>
          </select></div>
      </div>
      <p *ngIf="summary && !summary.rows.length" class="text-muted mb-0">No studying students.</p>
      <div class="table-responsive" *ngIf="summary?.rows?.length"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Student</th><th>Class</th><th *ngIf="!scoped">Center</th>
          <th class="text-right">Present</th><th class="text-right">Absent</th><th class="text-right">Leave</th><th class="text-right">Attendance</th></tr></thead>
        <tbody><tr *ngFor="let r of summary!.rows">
          <td>{{ r.name }}</td><td>{{ r.class_level }}</td><td *ngIf="!scoped">{{ r.center_name }}</td>
          <td class="text-right">{{ r.present }}</td><td class="text-right">{{ r.absent }}</td><td class="text-right">{{ r.leave }}</td>
          <td class="text-right" [class.low]="r.percent != null && r.percent < 75">{{ r.percent != null ? r.percent + '%' : 'not marked' }}</td>
        </tr></tbody>
      </table></div>
    </div></div>
  `
})
export class CoachingAttendanceComponent implements OnInit {
  readonly today = new Date().toISOString().slice(0, 10);
  reg: Register | null = null;
  summary: Summary | null = null;
  centerId: number | null = null;
  /** null until the server picks the first class with students. */
  classLevel: number | null = null;
  date = this.today;
  from = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);
  to = this.today;
  summaryClass: number | null = null;
  marks: Record<number, string | null> = {};
  private original: Record<number, string | null> = {};
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('COACHING_ATTENDANCE', 'EDIT'); }
  get centers(): CoachingCenter[] { return this.reg?.centers ?? []; }
  get scoped(): boolean { return this.reg?.scoped ?? false; }
  get classes(): number[] { return this.reg?.classes ?? [7, 8, 9, 10]; }
  get totalStudents(): number { return Object.values(this.reg?.students_per_class ?? {}).reduce((a, b) => a + b, 0); }
  get dirty(): boolean { return Object.keys(this.marks).some((k) => this.marks[+k] !== this.original[+k]); }

  ngOnInit(): void { this.loadRegister(); this.loadSummary(); }

  loadRegister(): void {
    if (this.dirty && !confirm('Discard the marks you haven\'t saved?')) return;
    this.api.get<Register>('/admin/coaching/attendance', { centerId: this.centerId, classLevel: this.classLevel, date: this.date }).subscribe({
      next: (r) => {
        this.reg = r;
        this.centerId = r.rcc_center_id;
        this.classLevel = r.class_level;
        this.marks = {};
        this.original = {};
        r.rows.forEach((x) => { this.marks[x.student_id] = x.status; this.original[x.student_id] = x.status; });
      },
      error: (e) => (this.error = errorText(e, 'Could not load the register.'))
    });
  }

  loadSummary(): void {
    this.api.get<Summary>('/admin/coaching/attendance/summary', { centerId: this.scoped ? null : this.centerId, classLevel: this.summaryClass, from: this.from, to: this.to })
      .subscribe({ next: (s) => (this.summary = s), error: (e) => (this.error = errorText(e, 'Could not load the attendance.')) });
  }

  set(id: number, s: string): void { this.marks[id] = this.marks[id] === s ? null : s; }

  all(s: string): void { this.reg?.rows.forEach((r) => { if (!this.marks[r.student_id]) this.marks[r.student_id] = s; }); }

  label(s: string | null): string { return s === 'P' ? 'Present' : s === 'A' ? 'Absent' : s === 'L' ? 'Leave' : '-'; }

  save(): void {
    const entries = Object.keys(this.marks).filter((k) => this.marks[+k] !== this.original[+k]).map((k) => ({ studentId: +k, status: this.marks[+k] ?? '' }));
    this.saving = true;
    this.api.put<{ message: string }>('/admin/coaching/attendance', { centerId: this.centerId, classLevel: this.classLevel, date: this.date, entries }).subscribe({
      next: (m) => { this.saving = false; this.message = m.message; this.original = { ...this.marks }; this.loadRegister(); this.loadSummary(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the attendance.'); }
    });
  }
}
