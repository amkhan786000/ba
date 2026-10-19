import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { APPLICATION_STATUSES, applicationBadge } from './admin-applications.component';
import { WEBSITE_TRACK_URL } from '../core/models/website';

interface Found {
  id: number; name: string; email: string; student_mobile: string | null; father_mobile: string | null; mother_mobile: string | null;
  course_applied: string | null; rcc_name: string | null; created_at: string | null; status: string; status_label: string;
  interview_at: string | null; interview_venue: string | null; student_user_id: number | null; documents: number;
  history: { status: string; comments: string | null; date: string | null }[];
}

/**
 * Applications > Track Application: staff find a scholarship application by its number, a mobile on the form, the email or
 * part of the name, and see where it stands (the steps so far, the interview, documents), e.g. when a family calls.
 * Families track their own on the website.
 */
@Component({
  selector: 'app-admin-track-application',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, AppDatePipe],
  styles: [`
    .steps { display: flex; flex-wrap: wrap; gap: 6px; margin: 10px 0; }
    .steps span { font-size: 12px; padding: 3px 10px; border-radius: 12px; background: var(--bg, #f4f6f2); color: #6b776f; }
    .steps span.done { background: #e3eecd; color: #375500; }
    .steps span.now { background: #4f7a00; color: #fff; }
    .steps span.rejected { background: #c0262d; color: #fff; }
    .timeline li { padding: 4px 0; font-size: 13px; border-bottom: 1px solid var(--line, #e5e9e4); }
    .timeline li:last-child { border-bottom: 0; }
    .facts { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 6px 16px; font-size: 13px; }
    .facts span { color: #6b776f; display: block; font-size: 12px; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Track Application</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <form class="d-flex flex-wrap" (ngSubmit)="search()">
        <input class="form-control mr-2 mb-2" style="max-width: 420px" name="q" [(ngModel)]="q" autofocus
               placeholder="Application number, mobile number, email or name" aria-label="Find an application">
        <button class="btn btn-primary mb-2" type="submit" [disabled]="busy || q.trim().length < 2">
          <i class="mdi mdi-magnify mr-1"></i>{{ busy ? 'Searching…' : 'Find' }}</button>
      </form>
      <p class="small text-muted mb-0">Families track their own application on the website:
        <a [href]="websiteTrack" target="_blank" rel="noopener">Track your application</a> (application number + a mobile number on the form).</p>
    </div></div>

    <p *ngIf="searched && !results.length && !busy" class="text-muted">No application matches "{{ searched }}".</p>

    <div class="card" *ngFor="let a of results"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-start">
        <div>
          <h4 class="header-title mb-1">{{ a.name }} <small class="text-muted">#{{ a.id }}</small></h4>
          <span class="badge" [ngClass]="badge(a.status)">{{ a.status_label }}</span>
          <small class="text-muted ml-2">applied {{ a.created_at | appDate }}</small>
        </div>
        <div class="mt-2 mt-md-0">
          <a class="btn btn-sm btn-primary mr-1" [routerLink]="['/admin/applications', a.id]">Open application</a>
          <a *ngIf="a.student_user_id" class="btn btn-sm btn-outline-success" [routerLink]="['/admin/users', a.student_user_id, 'edit']">Student record</a>
        </div>
      </div>

      <div class="steps">
        <ng-container *ngIf="a.status !== 'REJECTED'; else rejected">
          <span *ngFor="let s of steps; let i = index" [class.done]="i < stepIndex(a)" [class.now]="i === stepIndex(a)">{{ s.label }}</span>
        </ng-container>
        <ng-template #rejected><span class="rejected">Rejected</span></ng-template>
      </div>

      <div class="facts mb-2">
        <div><span>Email</span>{{ a.email }}</div>
        <div><span>Mobiles (student / father / mother)</span>{{ a.student_mobile || '--' }} / {{ a.father_mobile || '--' }} / {{ a.mother_mobile || '--' }}</div>
        <div><span>Course applied</span>{{ a.course_applied || '--' }}</div>
        <div><span>RCC centre</span>{{ a.rcc_name || '--' }}</div>
        <div *ngIf="a.interview_at"><span>Interview</span>{{ a.interview_at | appDate: 'datetime' }}<ng-container *ngIf="a.interview_venue"> · {{ a.interview_venue }}</ng-container></div>
        <div><span>Documents uploaded</span>{{ a.documents }}</div>
      </div>

      <ul class="list-unstyled timeline mb-0">
        <li *ngFor="let h of a.history"><strong>{{ h.status }}</strong> <span class="text-muted">· {{ h.date | appDate: 'datetime' }}</span>
          <span *ngIf="h.comments" class="d-block text-muted">{{ h.comments }}</span></li>
      </ul>
    </div></div>
  `
})
export class AdminTrackApplicationComponent {
  /** The steps before a decision, then Accepted (Rejected is shown on its own). */
  readonly steps = APPLICATION_STATUSES.filter((s) => s.value !== 'REJECTED');
  readonly badge = applicationBadge;
  readonly websiteTrack = WEBSITE_TRACK_URL;
  q = '';
  searched = '';
  results: Found[] = [];
  busy = false;
  error = '';

  constructor(private api: ApiService, private router: Router, route: ActivatedRoute) {
    // ?q= keeps the search in the address (back button, sharing with a colleague)
    this.q = route.snapshot.queryParamMap.get('q') ?? '';
    if (this.q.trim().length >= 2) this.search(false);
  }

  stepIndex(a: Found): number {
    const i = this.steps.findIndex((s) => s.value === a.status);
    return i < 0 ? 0 : i;
  }

  search(remember = true): void {
    const q = this.q.trim();
    if (q.length < 2) return;
    this.busy = true;
    this.error = '';
    if (remember) this.router.navigate([], { queryParams: { q }, replaceUrl: true });
    this.api.get<Found[]>('/admin/applications/lookup', { q }).subscribe({
      next: (r) => { this.busy = false; this.results = r; this.searched = q; },
      error: (e) => { this.busy = false; this.results = []; this.error = errorText(e, 'Could not search the applications.'); }
    });
  }
}
