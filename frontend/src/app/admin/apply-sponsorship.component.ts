import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { PreferenceOptions, SponsorPreferences, SponsorPreferencesComponent, emptyPreferences, preferencesFrom } from '../shared/sponsor-preferences.component';

interface Period { id: number; start_date: string; end_date: string; active: boolean }
interface SponsorOption { id: number; user_id: string | null; name: string; chapter_name: string | null }
interface Form {
  open: boolean; period?: Period; options?: PreferenceOptions; for_self?: boolean; for_others?: boolean;
  sponsors?: SponsorOption[]; me?: number;
}
interface Application {
  application_id: number; sponsor_id: number; sponsor_code: string | null; sponsor_name: string; chapter_name: string | null;
  students_wanted: number; college_type: string | null; father_professions: string[]; income_min: number | null; income_max: number | null;
  preferences: string; note: string | null; applied_by: string | null; created_at: string; updated_at: string;
}
interface Applications { periods: Period[]; period: Period | null; rows: Application[]; students_wanted: number }

/**
 * Applications > Apply for Sponsorship (and the sponsor portal's): while an application period is active a sponsor
 * says how many students they'd sponsor and what kind. Users who may apply for others choose the sponsor and see
 * everyone's applications. With no active period: "stay tuned".
 */
@Component({
  selector: 'app-apply-sponsorship',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, SponsorPreferencesComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Apply for Sponsorship</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div *ngIf="form && !form.open" class="card"><div class="card-body text-center py-5">
      <i class="mdi mdi-calendar-clock text-primary" style="font-size: 48px"></i>
      <h4 class="mt-2">Sponsorship applications aren't open yet</h4>
      <p class="text-muted mb-0">Stay tuned! You can apply here as soon as the next session starts.</p>
    </div></div>

    <ng-container *ngIf="form?.open">
      <div class="card"><div class="card-body">
        <h4 class="header-title">Session {{ form!.period!.start_date | appDate }} – {{ form!.period!.end_date | appDate }}</h4>
        <p class="text-muted small">Tell us how many students you'd like to sponsor and, if it matters to you, what kind. We use this
          when matching students to sponsors. You can change it while the session is open.</p>

        <div class="row" *ngIf="form!.for_others">
          <div class="col-md-6 form-group">
            <label for="sponsor">Applying for</label>
            <select id="sponsor" class="form-control" [(ngModel)]="sponsorId" (ngModelChange)="loadFor()">
              <option [ngValue]="null">-- Choose a sponsor --</option>
              <option *ngIf="form!.for_self" [ngValue]="form!.me">Myself</option>
              <option *ngFor="let s of otherSponsors" [ngValue]="s.id">{{ s.name }}{{ s.user_id ? ' (' + s.user_id + ')' : '' }}{{ s.chapter_name ? ' · ' + s.chapter_name : '' }}</option>
            </select>
          </div>
        </div>
        <p *ngIf="!form!.for_self && !form!.for_others" class="text-muted">Only sponsors can apply.</p>

        <ng-container *ngIf="sponsorId !== null">
          <div *ngIf="existing" class="alert alert-light border small">
            Applied on {{ existing.created_at | appDate }}<span *ngIf="existing.updated_at !== existing.created_at">, last changed {{ existing.updated_at | appDate }}</span>.
            Saving again updates the application.</div>
          <div class="row">
            <div class="col-md-4 form-group">
              <label for="wanted">How many students would you like to sponsor? <span class="text-danger">*</span></label>
              <input id="wanted" type="number" min="1" max="100" class="form-control" [(ngModel)]="studentsWanted">
            </div>
          </div>
          <app-sponsor-preferences [(value)]="prefs" [options]="form!.options ?? null"></app-sponsor-preferences>
          <div class="form-group">
            <label for="note">Anything else? <span class="text-muted small">(optional)</span></label>
            <textarea id="note" class="form-control" rows="2" maxlength="500" [(ngModel)]="note"></textarea>
          </div>
          <button class="btn btn-primary" (click)="submit()" [disabled]="busy || !studentsWanted">
            <i class="mdi mdi-send mr-1"></i>{{ existing ? 'Update application' : 'Apply' }}</button>
        </ng-container>
      </div></div>
    </ng-container>

    <div class="card" *ngIf="form?.for_others && list"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-center mb-2">
        <h4 class="header-title mb-0">Sponsorship applications
          <small class="text-muted" *ngIf="list.period">· {{ list.rows.length }} sponsor{{ list.rows.length === 1 ? '' : 's' }},
            {{ list.students_wanted }} student{{ list.students_wanted === 1 ? '' : 's' }} wanted</small></h4>
        <select class="form-control form-control-sm w-auto" [(ngModel)]="periodId" (ngModelChange)="loadList()" aria-label="Session">
          <option *ngFor="let p of list.periods" [ngValue]="p.id">{{ p.start_date | appDate }} – {{ p.end_date | appDate }}{{ p.active ? ' (current)' : '' }}</option>
        </select>
      </div>
      <p *ngIf="!list.rows.length" class="text-muted mb-0">No applications for this session yet.</p>
      <div class="table-responsive" *ngIf="list.rows.length"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Sponsor</th><th>Chapter</th><th class="text-right">Students</th><th>Wants</th><th>Note</th><th>Applied</th></tr></thead>
        <tbody><tr *ngFor="let a of list.rows">
          <td>{{ a.sponsor_name }} <span class="text-muted small">{{ a.sponsor_code }}</span></td>
          <td>{{ a.chapter_name || '-' }}</td>
          <td class="text-right"><strong>{{ a.students_wanted }}</strong></td>
          <td class="small">{{ a.preferences }}</td>
          <td class="small">{{ a.note || '' }}</td>
          <td class="small">{{ a.updated_at | appDate }}<div *ngIf="a.applied_by" class="text-muted">by {{ a.applied_by }}</div></td>
        </tr></tbody>
      </table></div>
    </div></div>
  `
})
export class ApplySponsorshipComponent implements OnInit {
  form: Form | null = null;
  list: Applications | null = null;
  periodId: number | null = null;
  sponsorId: number | null = null;
  existing: Application | null = null;
  studentsWanted: number | null = 1;
  prefs: SponsorPreferences = emptyPreferences();
  note = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  get otherSponsors(): SponsorOption[] { return (this.form?.sponsors ?? []).filter((s) => s.id !== this.form?.me); }

  ngOnInit(): void {
    this.api.get<Form>('/sponsor-applications/form').subscribe({
      next: (f) => {
        this.form = f;
        if (f.open && f.for_self && !f.for_others) { this.sponsorId = f.me ?? null; this.loadFor(); }
        if (f.for_others) this.loadList();
      },
      error: (e) => (this.error = errorText(e, 'Could not load the form.'))
    });
  }

  /** Starts the form with the sponsor's application this session, or else their preferences. */
  loadFor(): void {
    this.existing = null;
    this.message = '';
    if (this.sponsorId === null) return;
    this.api.get<{ application: Application | null; preferences: Record<string, unknown> }>(`/sponsor-applications/for/${this.sponsorId}`).subscribe({
      next: (r) => {
        this.existing = r.application;
        this.studentsWanted = r.application?.students_wanted ?? 1;
        this.note = r.application?.note ?? '';
        this.prefs = r.application ? preferencesFrom(r.application as unknown as Record<string, unknown>, '') : preferencesFrom(r.preferences);
      },
      error: (e) => (this.error = errorText(e, 'Could not load the application.'))
    });
  }

  submit(): void {
    this.busy = true;
    this.error = '';
    const body = { sponsorId: this.sponsorId, studentsWanted: this.studentsWanted, note: this.note, ...this.prefs };
    this.api.post<Application>('/sponsor-applications', body).subscribe({
      next: (a) => {
        this.busy = false;
        this.message = this.existing ? 'Application updated. Thank you!' : 'Thank you! Your application has been received.';
        this.existing = a;
        if (this.form?.for_others) this.loadList();
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send the application.'); }
    });
  }

  loadList(): void {
    const params: Record<string, string | number> = {};
    if (this.periodId) params['periodId'] = this.periodId;
    this.api.get<Applications>('/sponsor-applications', params).subscribe({
      next: (l) => { this.list = l; this.periodId = l.period?.id ?? null; }
    });
  }
}
