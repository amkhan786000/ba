import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { isoDate } from '../../shared/format';
import { RebSectionRow, RebSession } from './reb.models';
import { DateInputComponent } from '../../shared/date-input.component';

interface DayRow { student_id: number; name: string; roll_no: string | null; status: string; remark: string | null }
interface Day { section: string; date: string; marked: boolean; holiday: string | null; rows: DayRow[] }
interface RegisterRow { student_id: number; name: string; roll_no: string | null; days: string; present: number; percent: number | null }
interface CalendarEntry { id: number; date_from: string; date_to: string; title: string; kind: string }

export const ATTENDANCE: { code: string; short: string; label: string; css: string }[] = [
  { code: 'PRESENT', short: 'P', label: 'Present', css: 'btn-success' },
  { code: 'ABSENT', short: 'A', label: 'Absent', css: 'btn-danger' },
  { code: 'LATE', short: 'L', label: 'Late', css: 'btn-warning' },
  { code: 'LEAVE', short: 'V', label: 'Leave', css: 'btn-info' }
];

/**
 * Rahbar-e-Banaat > Attendance: mark a class for a day (everyone present by default: tap the girls who are
 * absent, late or on leave), the month's register with percentages, and the holidays of the session.
 * Teachers see only their own classes.
 */
@Component({
  selector: 'app-reb-attendance',
  standalone: true,
  imports: [DateInputComponent, CommonModule, FormsModule, AlertsComponent],
  styles: [`
    .reg { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .8rem; letter-spacing: .12em; white-space: nowrap; }
    .mark-row { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: .45rem 0; border-bottom: 1px solid #eef1f0; }
    .mark-row .btn { min-width: 2.4rem; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Attendance</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'mark'" (click)="$event.preventDefault(); tab = 'mark'">Mark</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'register'" (click)="$event.preventDefault(); tab = 'register'; loadRegister()">Register</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'holidays'" (click)="$event.preventDefault(); tab = 'holidays'; loadCalendar()">Holidays</a></li>
    </ul>

    <div class="card" *ngIf="tab !== 'holidays'"><div class="card-body pb-1">
      <div class="row">
        <div class="col-12 col-md-5 form-group"><label for="a-section">Class</label>
          <select id="a-section" class="form-control" [(ngModel)]="sectionId" (ngModelChange)="reload()">
            <option [ngValue]="null">-- Choose --</option><option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select></div>
        <div class="col-6 col-md-3 form-group" *ngIf="tab === 'mark'"><label for="a-date">Day</label><app-date-input id="a-date" [max]="today" [(ngModel)]="date" (ngModelChange)="loadDay()"></app-date-input></div>
        <div class="col-6 col-md-3 form-group" *ngIf="tab === 'register'"><label for="a-month">Month</label><input id="a-month" type="month" class="form-control" [(ngModel)]="month" (ngModelChange)="loadRegister()"></div>
      </div>
      <p *ngIf="!sections.length" class="text-muted">No classes{{ teacherScoped ? ' are linked to you yet (ask the office to set who teaches what)' : ' in the current session yet' }}.</p>
    </div></div>

    <!-- mark -->
    <div class="card" *ngIf="tab === 'mark' && day">
      <div class="card-body">
        <div class="d-flex flex-wrap justify-content-between align-items-center mb-2">
          <div><strong>{{ day.section }}</strong> · {{ day.date | date: 'EEEE d MMMM' }}
            <span *ngIf="day.marked" class="badge badge-success ml-1">Marked</span>
            <span *ngIf="day.holiday" class="badge badge-warning ml-1">Holiday: {{ day.holiday }}</span></div>
          <div class="small">{{ count('PRESENT') + count('LATE') }} present · {{ count('ABSENT') }} absent<span *ngIf="count('LEAVE')"> · {{ count('LEAVE') }} on leave</span></div>
        </div>
        <p *ngIf="!day.rows.length" class="text-muted">No girls in this class.</p>
        <div class="mark-row" *ngFor="let r of day.rows">
          <div style="min-width: 0"><span class="text-muted mr-1">{{ r.roll_no }}</span>{{ r.name }}</div>
          <div class="btn-group btn-group-sm" role="group" [attr.aria-label]="'Attendance of ' + r.name">
            <button *ngFor="let a of statuses" type="button" class="btn" [ngClass]="r.status === a.code ? a.css : 'btn-light'"
                    [attr.aria-pressed]="r.status === a.code" [title]="a.label" (click)="r.status = a.code" [disabled]="!canEdit">{{ a.short }}</button>
          </div>
        </div>
        <div class="d-flex justify-content-between align-items-center mt-3" *ngIf="canEdit && day.rows.length">
          <button class="btn btn-light btn-sm" (click)="allPresent()">Everyone present</button>
          <button class="btn btn-primary" (click)="save()" [disabled]="busy">{{ busy ? 'Saving…' : 'Save attendance' }}</button>
        </div>
      </div>
    </div>

    <!-- register -->
    <div class="card" *ngIf="tab === 'register' && register">
      <div class="card-body">
        <p class="small text-muted">P present · A absent · L late · V leave · H holiday · . not marked. Percentage: present and late out of the days marked (leave left out).</p>
        <div class="table-responsive"><table class="table table-sm mb-0">
          <thead><tr><th>Girl</th><th class="reg">{{ dayNumbers }}</th><th class="text-right">%</th></tr></thead>
          <tbody>
            <tr *ngIf="!register.rows.length"><td colspan="3" class="text-muted">No girls in this class.</td></tr>
            <tr *ngFor="let r of register.rows"><td class="text-nowrap"><span class="text-muted">{{ r.roll_no }}</span> {{ r.name }}</td>
              <td class="reg">{{ r.days }}</td><td class="text-right" [class.text-danger]="r.percent !== null && r.percent < 75">{{ r.percent ?? '' }}</td></tr>
          </tbody>
        </table></div>
      </div>
    </div>

    <!-- holidays -->
    <div class="card" *ngIf="tab === 'holidays'"><div class="card-body">
      <p class="text-muted">Holidays of the current session are left out of the attendance percentage.</p>
      <div *ngIf="canEditCalendar" class="d-flex flex-wrap align-items-end border rounded p-2 mb-3">
        <div class="form-group mb-2 mr-2"><label for="c-from">From</label><app-date-input id="c-from" [(ngModel)]="entry.dateFrom"></app-date-input></div>
        <div class="form-group mb-2 mr-2"><label for="c-to">To</label><app-date-input id="c-to" [(ngModel)]="entry.dateTo"></app-date-input></div>
        <div class="form-group mb-2 mr-2 flex-fill"><label for="c-title">What</label><input id="c-title" class="form-control" [(ngModel)]="entry.title" placeholder="e.g. Eid ul-Fitr holidays"></div>
        <div class="form-group mb-2 mr-2"><label for="c-kind">Kind</label>
          <select id="c-kind" class="form-control" [(ngModel)]="entry.kind"><option value="HOLIDAY">Holiday</option><option value="EVENT">Event</option><option value="EXAM">Exam</option></select></div>
        <button class="btn btn-primary mb-2" (click)="addEntry()" [disabled]="!entry.dateFrom || !entry.title.trim()">Add</button>
      </div>
      <table class="table table-sm mb-0">
        <tbody>
          <tr *ngIf="!calendar.length"><td class="text-muted">Nothing yet.</td></tr>
          <tr *ngFor="let c of calendar"><td class="text-nowrap">{{ c.date_from | date: 'd MMM' }}<span *ngIf="c.date_to !== c.date_from"> – {{ c.date_to | date: 'd MMM' }}</span></td>
            <td>{{ c.title }} <span class="badge badge-light border">{{ c.kind === 'HOLIDAY' ? 'Holiday' : c.kind === 'EXAM' ? 'Exam' : 'Event' }}</span></td>
            <td class="text-right"><button *ngIf="canEditCalendar" class="btn btn-xs btn-link text-danger" (click)="removeEntry(c)">Remove</button></td></tr>
        </tbody>
      </table>
    </div></div>
  `
})
export class RebAttendanceComponent implements OnInit {
  readonly statuses = ATTENDANCE;
  readonly today = isoDate(new Date());
  tab: 'mark' | 'register' | 'holidays' = 'mark';
  sessions: RebSession[] = [];
  sections: RebSectionRow[] = [];
  sectionId: number | null = null;
  date = isoDate(new Date());
  month = isoDate(new Date()).slice(0, 7);
  day: Day | null = null;
  register: { rows: RegisterRow[]; days_in_month: number } | null = null;
  calendar: CalendarEntry[] = [];
  entry = { dateFrom: '', dateTo: '', title: '', kind: 'HOLIDAY' };
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_ATTENDANCE', 'EDIT'); }
  get canEditCalendar(): boolean { return this.auth.can('REB_SETUP', 'EDIT'); }
  get teacherScoped(): boolean { return !this.auth.can('REB_SETUP'); }
  get dayNumbers(): string { return Array.from({ length: this.register?.days_in_month ?? 0 }, (_, i) => String((i + 1) % 10)).join(''); }
  get currentSessionId(): number | null { return (this.sessions.find((s) => s.is_current) ?? this.sessions[0])?.id ?? null; }

  count(code: string): number { return this.day?.rows.filter((r) => r.status === code).length ?? 0; }

  ngOnInit(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        this.sessions = r;
        const sid = this.currentSessionId;
        if (sid) this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: sid }).subscribe({
          next: (s) => { this.sections = s; if (s.length === 1) { this.sectionId = s[0].id; this.reload(); } }
        });
      },
      error: (e) => (this.error = errorText(e, 'Could not load the classes.'))
    });
  }

  reload(): void { if (this.tab === 'register') this.loadRegister(); else this.loadDay(); }

  loadDay(): void {
    if (!this.sectionId) { this.day = null; return; }
    this.api.get<Day>('/admin/reb/attendance', { sectionId: this.sectionId, date: this.date }).subscribe({
      next: (d) => (this.day = d), error: (e) => (this.error = errorText(e, 'Could not load the class.'))
    });
  }

  allPresent(): void { this.day?.rows.forEach((r) => (r.status = 'PRESENT')); }

  save(): void {
    if (!this.day || !this.sectionId) return;
    this.busy = true;
    this.api.put<{ message: string }>('/admin/reb/attendance', {
      sectionId: this.sectionId, date: this.date, marks: this.day.rows.map((r) => ({ studentId: r.student_id, status: r.status, remark: r.remark }))
    }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.loadDay(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the attendance.'); }
    });
  }

  loadRegister(): void {
    if (!this.sectionId) { this.register = null; return; }
    this.api.get<{ rows: RegisterRow[]; days_in_month: number }>('/admin/reb/attendance/register', { sectionId: this.sectionId, month: this.month }).subscribe({
      next: (r) => (this.register = r), error: (e) => (this.error = errorText(e, 'Could not load the register.'))
    });
  }

  loadCalendar(): void {
    this.api.get<CalendarEntry[]>('/admin/reb/calendar', { sessionId: this.currentSessionId }).subscribe({
      next: (r) => (this.calendar = r), error: (e) => (this.error = errorText(e, 'Could not load the holidays.'))
    });
  }

  addEntry(): void {
    this.api.post<{ message: string }>('/admin/reb/calendar', { ...this.entry, sessionId: this.currentSessionId }).subscribe({
      next: (r) => { this.message = r.message; this.entry = { dateFrom: '', dateTo: '', title: '', kind: 'HOLIDAY' }; this.loadCalendar(); },
      error: (e) => (this.error = errorText(e, 'Could not add it.'))
    });
  }

  removeEntry(c: CalendarEntry): void {
    if (!confirm(`Remove ${c.title}?`)) return;
    this.api.delete<{ message: string }>(`/admin/reb/calendar/${c.id}`).subscribe({ next: () => this.loadCalendar(), error: (e) => (this.error = errorText(e, 'Could not remove it.')) });
  }
}
