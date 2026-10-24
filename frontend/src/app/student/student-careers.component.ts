import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { OPENING_KINDS, PLACEMENT_KINDS, TOPICS } from '../admin/careers.component';

type Row = Record<string, any>;

/** Student portal > Careers: openings (say "I'm interested"), my placements (add one), and asking for career guidance. */
@Component({
  selector: 'app-student-careers',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Careers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <h4 class="header-title">Jobs & internships</h4>
      <p *ngIf="d && !d.opportunities.length" class="text-muted mb-0">No openings right now. Check back soon.</p>
      <div *ngFor="let o of d?.opportunities" class="border rounded p-3 mb-2">
        <div class="d-flex flex-wrap" style="gap: 8px">
          <div class="mr-auto"><strong>{{ o['title'] }}</strong> <span class="badge badge-light border">{{ kinds[o['kind']] }}</span>
            <div class="small text-muted">{{ o['organisation'] }}{{ o['location'] ? ' · ' + o['location'] : '' }}{{ o['last_date'] ? ' · apply by ' + (o['last_date'] | appDate) : '' }}</div></div>
          <div class="text-nowrap">
            <a *ngIf="o['apply_link'] && o['open_now']" [href]="o['apply_link']" target="_blank" rel="noopener" class="btn btn-sm btn-outline-primary">Apply</a>
            <button *ngIf="!o['my_status'] && o['open_now']" class="btn btn-sm btn-primary ml-1" (click)="interest(o, true)">I'm interested</button>
            <ng-container *ngIf="o['my_status']"><span class="badge badge-success">{{ o['my_status'] === 'INTERESTED' ? 'You are interested' : o['my_status'] === 'APPLIED' ? 'Applied' : o['my_status'] === 'SELECTED' ? 'Selected!' : 'Not selected' }}</span>
              <a *ngIf="o['my_status'] === 'INTERESTED'" href="" class="small ml-2" (click)="$event.preventDefault(); interest(o, false)">undo</a></ng-container>
          </div>
        </div>
        <div *ngIf="o['eligibility']" class="small mt-1"><strong>Who can apply:</strong> {{ o['eligibility'] }}</div>
        <div *ngIf="o['description']" class="small mt-1" style="white-space: pre-line">{{ o['description'] }}</div>
      </div>
    </div></div>

    <div class="row">
      <div class="col-lg-6"><div class="card"><div class="card-body">
        <div class="d-flex"><h4 class="header-title mr-auto">My placements</h4>
          <button class="btn btn-sm btn-outline-primary" (click)="placement = placement ? null : { kind: 'JOB' }">{{ placement ? 'Cancel' : '+ Add' }}</button></div>
        <p class="small text-muted">Got a job, internship or admission for higher studies? Tell us; the office will confirm it.</p>
        <div *ngIf="placement" class="border rounded p-2 mb-3">
          <div class="row">
            <div class="col-md-5 form-group"><label class="small">Kind</label><select class="form-control form-control-sm" [(ngModel)]="placement['kind']">
              <option *ngFor="let k of placementKindList" [value]="k.key">{{ k.label }}</option></select></div>
            <div class="col-md-7 form-group"><label class="small">{{ placement['kind'] === 'HIGHER_STUDIES' ? 'College / university' : 'Organisation' }} *</label>
              <input class="form-control form-control-sm" maxlength="200" [(ngModel)]="placement['organisation']"></div>
            <div class="col-md-6 form-group"><label class="small">{{ placement['kind'] === 'HIGHER_STUDIES' ? 'Course' : 'Role' }}</label>
              <input class="form-control form-control-sm" maxlength="150" [(ngModel)]="placement['roleTitle']"></div>
            <div class="col-md-6 form-group"><label class="small">Location</label><input class="form-control form-control-sm" maxlength="150" [(ngModel)]="placement['location']"></div>
            <div class="col-md-6 form-group"><label class="small">From</label><input type="date" class="form-control form-control-sm" [(ngModel)]="placement['startDate']"></div>
          </div>
          <button class="btn btn-sm btn-primary" (click)="addPlacement()" [disabled]="busy || !placement['organisation']">Send</button>
        </div>
        <p *ngIf="d && !d.placements.length && !placement" class="text-muted mb-0">None yet.</p>
        <div *ngFor="let p of d?.placements" class="mb-2"><strong>{{ p['organisation'] }}</strong> <span class="badge badge-light border">{{ placementKinds[p['kind']] }}</span>
          <span class="badge" [ngClass]="p['verified_at'] ? 'badge-success' : 'badge-warning'">{{ p['verified_at'] ? 'Confirmed' : 'Waiting for the office' }}</span>
          <div class="small text-muted">{{ p['role_title'] }}{{ p['start_date'] ? ' · from ' + (p['start_date'] | appDate) : '' }}</div></div>
      </div></div></div>

      <div class="col-lg-6"><div class="card"><div class="card-body">
        <h4 class="header-title">Career guidance</h4>
        <p class="small text-muted">Not sure what to do next? Ask, and the office will arrange a session with you.</p>
        <div class="form-group"><select class="form-control form-control-sm" [(ngModel)]="topic">
          <option *ngFor="let t of topicList" [value]="t.key">{{ t.label }}</option></select></div>
        <textarea class="form-control mb-2" rows="3" maxlength="2000" [(ngModel)]="question" placeholder="Your question"></textarea>
        <button class="btn btn-sm btn-primary mb-3" (click)="ask()" [disabled]="busy || !question.trim()">Ask</button>
        <div *ngFor="let g of d?.guidance" class="border-top pt-2 mb-2 small">
          <strong>{{ topics[g['topic']] }}</strong> · {{ g['created_at'] | appDate }} ·
          <span class="badge" [ngClass]="g['status'] === 'DONE' ? 'badge-success' : g['status'] === 'SCHEDULED' ? 'badge-info' : 'badge-light border'">
            {{ g['status'] === 'OPEN' ? 'Waiting' : g['status'] === 'SCHEDULED' ? 'Session set' : g['status'] === 'DONE' ? 'Done' : 'Closed' }}</span>
          <div *ngIf="g['session_at']">Session: <strong>{{ g['session_at'] | appDate: 'datetime' }}</strong></div>
          <div class="text-muted" style="white-space: pre-line">{{ g['message'] }}</div>
          <div *ngIf="g['outcome']" class="text-success" style="white-space: pre-line">Advice: {{ g['outcome'] }}</div>
        </div>
      </div></div></div>
    </div>
  `
})
export class StudentCareersComponent implements OnInit {
  readonly kinds = OPENING_KINDS;
  readonly placementKinds = PLACEMENT_KINDS;
  readonly topics = TOPICS;
  readonly placementKindList = Object.entries(PLACEMENT_KINDS).map(([key, label]) => ({ key, label }));
  readonly topicList = Object.entries(TOPICS).map(([key, label]) => ({ key, label }));
  d: { opportunities: Row[]; placements: Row[]; guidance: Row[] } | null = null;
  placement: Row | null = null;
  topic = 'COURSE';
  question = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<typeof this.d>('/student/careers').subscribe({ next: (d) => (this.d = d), error: (e) => (this.error = errorText(e, 'Could not load.')) });
  }

  interest(o: Row, on: boolean): void {
    this.api.put<{ message: string }>(`/student/careers/opportunities/${o['id']}/interest`, { interested: on }).subscribe({
      next: (m) => { this.message = m.message; this.load(); }, error: (e) => (this.error = errorText(e, 'Could not save.'))
    });
  }

  addPlacement(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/student/careers/placements', this.placement).subscribe({
      next: (m) => { this.busy = false; this.placement = null; this.message = m.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send.'); }
    });
  }

  ask(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/student/careers/guidance', { topic: this.topic, message: this.question }).subscribe({
      next: (m) => { this.busy = false; this.question = ''; this.message = m.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send.'); }
    });
  }
}
