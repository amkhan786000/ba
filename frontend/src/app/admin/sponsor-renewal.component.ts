import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { DonutChartComponent, Slice } from '../shared/charts/donut-chart.component';

interface Summary { asked: number; continuing: number; changing: number; stopping: number; waiting: number; students_now: number; students_next: number }
interface Campaign extends Partial<Summary> { id: number; session_year: number; deadline: string | null; message: string | null; closed_at: string | null; created_at: string }
interface Row {
  sponsor_id: number; sponsor_name: string; sponsor_code: string | null; chapter_name: string | null; has_email: boolean; current_students: number;
  emailed_at: string | null; reminded_at: string | null; answer: string | null; students_wanted: number | null; note: string | null;
  answered_at: string | null; answered_by_name: string | null;
}
interface Detail { campaign: Campaign; summary: Summary; rows: Row[] }

/**
 * Sponsors & Payments > Sponsor Renewal: before a session, ask every active sponsor who has students whether they will
 * continue. Sponsors answer by email link / in their portal; follow up the rest and record phone answers here.
 */
@Component({
  selector: 'app-sponsor-renewal-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, DonutChartComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Sponsor Renewal</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="canEdit && list && !list.open"><div class="card-body">
      <h4 class="header-title">Ask sponsors about the next session</h4>
      <p class="text-muted small">Every active sponsor who has students now gets an email and sees the question in their portal:
        continue with the same students, continue with a different number, or stop. You'll see the answers here.</p>
      <div class="row">
        <div class="col-md-3 form-group"><label for="ry">Session year</label><input id="ry" type="number" class="form-control" [(ngModel)]="form.sessionYear"></div>
        <div class="col-md-3 form-group"><label for="rd">Answer by</label><input id="rd" type="date" class="form-control" [(ngModel)]="form.deadline"></div>
        <div class="col-md-6 form-group"><label for="rm">Your message <span class="text-muted small">(optional, added to the email)</span></label>
          <textarea id="rm" class="form-control" rows="2" maxlength="2000" [(ngModel)]="form.message"></textarea></div>
      </div>
      <button class="btn btn-primary" (click)="start()" [disabled]="busy || !form.sessionYear"><i class="mdi mdi-email-send-outline mr-1"></i>Start and email sponsors</button>
    </div></div>

    <div class="card" *ngIf="list?.rows?.length"><div class="card-body">
      <h4 class="header-title">Campaigns</h4>
      <div class="table-responsive"><table class="table table-sm table-hover mb-0">
        <thead class="thead-light"><tr><th>Session</th><th>Started</th><th>Answer by</th><th class="text-right">Asked</th><th class="text-right">Continue</th>
          <th class="text-right">Change</th><th class="text-right">Stop</th><th class="text-right">Waiting</th><th></th></tr></thead>
        <tbody><tr *ngFor="let c of list!.rows" style="cursor: pointer" (click)="open(c.id)" [class.table-active]="detail?.campaign?.id === c.id">
          <td><strong>{{ c.session_year }}</strong> <span class="badge" [ngClass]="c.closed_at ? 'badge-light border' : 'badge-success'">{{ c.closed_at ? 'Closed' : 'Open' }}</span></td>
          <td>{{ c.created_at | appDate }}</td><td>{{ c.deadline ? (c.deadline | appDate) : '-' }}</td>
          <td class="text-right">{{ c.asked }}</td><td class="text-right">{{ c.continuing }}</td><td class="text-right">{{ c.changing }}</td>
          <td class="text-right">{{ c.stopping }}</td><td class="text-right">{{ c.waiting }}</td>
          <td class="text-right"><a href="" (click)="$event.preventDefault()">Open</a></td>
        </tr></tbody>
      </table></div>
    </div></div>

    <ng-container *ngIf="detail as d">
      <div class="row">
        <div class="col-lg-4"><div class="card"><div class="card-body">
          <h4 class="header-title">Session {{ d.campaign.session_year }} answers</h4>
          <app-donut-chart [data]="slices(d.summary)" caption="sponsors"></app-donut-chart>
          <p class="mb-1 mt-2">Students sponsored now: <strong>{{ d.summary.students_now }}</strong></p>
          <p class="mb-1">Covered next session so far: <strong>{{ d.summary.students_next }}</strong></p>
          <p class="small text-muted mb-0">The {{ d.summary.waiting }} sponsor(s) still waiting aren't counted yet.</p>
        </div></div></div>
        <div class="col-lg-8"><div class="card"><div class="card-body">
          <div class="d-flex flex-wrap align-items-center mb-2" style="gap: 8px">
            <h4 class="header-title mb-0 mr-auto">Sponsors</h4>
            <select class="form-control form-control-sm w-auto" [(ngModel)]="filter" aria-label="Answer">
              <option value="">Everyone</option><option value="WAITING">Not answered</option><option value="CONTINUE">Continue</option>
              <option value="CHANGE">Different number</option><option value="STOP">Stop</option>
            </select>
            <ng-container *ngIf="canEdit && !d.campaign.closed_at">
              <button class="btn btn-sm btn-outline-primary" (click)="remind(d.campaign.id)" [disabled]="busy || !d.summary.waiting"><i class="mdi mdi-bell-ring-outline"></i> Remind {{ d.summary.waiting }}</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="close(d.campaign.id)" [disabled]="busy">Close campaign</button>
            </ng-container>
          </div>
          <div class="table-responsive"><table class="table table-sm mb-0">
            <thead class="thead-light"><tr><th>Sponsor</th><th class="text-right">Students now</th><th>Asked</th><th>Answer</th><th></th></tr></thead>
            <tbody><tr *ngFor="let r of shown(d.rows)">
              <td><strong>{{ r.sponsor_name }}</strong> <span class="small text-muted">{{ r.sponsor_code }}</span><div class="small text-muted">{{ r.chapter_name }}</div></td>
              <td class="text-right">{{ r.current_students }}</td>
              <td class="small">{{ r.emailed_at ? 'Emailed ' + (r.emailed_at | appDate) : (r.has_email ? 'Sending…' : 'No email: call') }}
                <div *ngIf="r.reminded_at" class="text-muted">Reminded {{ r.reminded_at | appDate }}</div></td>
              <td class="small">
                <span *ngIf="!r.answer" class="badge badge-light border">Waiting</span>
                <ng-container *ngIf="r.answer"><span class="badge" [ngClass]="badge(r.answer)">{{ label(r.answer, r.students_wanted) }}</span>
                  <div class="text-muted">{{ r.answered_at | appDate }}{{ r.answered_by_name ? ' · ' + r.answered_by_name : '' }}</div>
                  <div *ngIf="r.note">{{ r.note }}</div></ng-container>
              </td>
              <td class="text-right"><button *ngIf="canEdit && !d.campaign.closed_at" class="btn btn-xs btn-outline-primary" (click)="openAnswer(r)">Record answer</button></td>
            </tr></tbody>
          </table></div>
        </div></div></div>
      </div>
    </ng-container>

    <div *ngIf="answering" class="modal fade show d-block" tabindex="-1" (click)="answering = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ answering.row.sponsor_name }}'s answer</h5>
            <button type="button" class="close" (click)="answering = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="answerError" class="alert alert-danger">{{ answerError }}</div>
            <div class="form-group"><label>Answer</label>
              <select class="form-control" [(ngModel)]="answering.answer">
                <option value="CONTINUE">Continue with the same {{ answering.row.current_students }} student(s)</option>
                <option value="CHANGE">Continue with a different number</option><option value="STOP">Stop</option>
              </select></div>
            <div class="form-group" *ngIf="answering.answer === 'CHANGE'"><label>How many students?</label>
              <input type="number" min="1" max="100" class="form-control" [(ngModel)]="answering.studentsWanted"></div>
            <div class="form-group"><label>Note</label><input class="form-control" maxlength="500" [(ngModel)]="answering.note" placeholder="e.g. Called on 12 Nov"></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="answering = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveAnswer()" [disabled]="busy">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SponsorRenewalAdminComponent implements OnInit {
  list: { rows: Campaign[]; open: boolean; next_year: number } | null = null;
  detail: Detail | null = null;
  form = { sessionYear: new Date().getFullYear() + 1, deadline: '', message: '' };
  filter = '';
  answering: { row: Row; answer: string; studentsWanted: number | null; note: string } | null = null;
  answerError = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('SPONSORSHIPS', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<{ rows: Campaign[]; open: boolean; next_year: number }>('/admin/renewals').subscribe({
      next: (l) => {
        this.list = l;
        this.form.sessionYear = l.next_year;
        const show = this.detail?.campaign.id ?? l.rows[0]?.id;
        if (show) this.open(show);
      },
      error: (e) => (this.error = errorText(e, 'Could not load the campaigns.'))
    });
  }

  open(id: number): void {
    this.api.get<Detail>(`/admin/renewals/${id}`).subscribe({ next: (d) => (this.detail = d), error: (e) => (this.error = errorText(e, 'Could not load the campaign.')) });
  }

  shown(rows: Row[]): Row[] {
    return rows.filter((r) => !this.filter || (this.filter === 'WAITING' ? !r.answer : r.answer === this.filter));
  }

  slices(s: Summary): Slice[] {
    return [{ label: 'Continue', value: s.continuing }, { label: 'Different number', value: s.changing },
      { label: 'Stop', value: s.stopping }, { label: 'Waiting', value: s.waiting }].filter((x) => x.value > 0);
  }

  label(a: string, n: number | null): string { return a === 'CONTINUE' ? 'Continue' : a === 'CHANGE' ? `Continue with ${n}` : 'Stop'; }
  badge(a: string): string { return a === 'STOP' ? 'badge-danger' : a === 'CHANGE' ? 'badge-info' : 'badge-success'; }

  start(): void {
    if (!confirm(`Email every active sponsor with students to ask about session ${this.form.sessionYear}?`)) return;
    this.busy = true;
    this.api.post<{ id: number; message: string }>('/admin/renewals', this.form).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.detail = null; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not start the campaign.'); }
    });
  }

  remind(id: number): void {
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/renewals/${id}/remind`, {}).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send reminders.'); }
    });
  }

  close(id: number): void {
    if (!confirm('Close this campaign? Sponsors can no longer answer or change their answer.')) return;
    this.api.post<{ message: string }>(`/admin/renewals/${id}/close`, {}).subscribe({
      next: (r) => { this.message = r.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not close the campaign.'))
    });
  }

  openAnswer(r: Row): void {
    this.answerError = '';
    this.answering = { row: r, answer: r.answer ?? 'CONTINUE', studentsWanted: r.students_wanted, note: r.note ?? '' };
  }

  saveAnswer(): void {
    const a = this.answering;
    if (!a || !this.detail) return;
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/renewals/${this.detail.campaign.id}/answer`,
      { sponsorId: a.row.sponsor_id, answer: a.answer, studentsWanted: a.studentsWanted, note: a.note }).subscribe({
      next: (m) => { this.busy = false; this.answering = null; this.message = m.message; this.load(); },
      error: (e) => { this.busy = false; this.answerError = errorText(e, 'Could not save the answer.'); }
    });
  }
}
