import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';
import { CardTableDirective } from '../shared/card-table.directive';

interface Volunteer {
  volunteer_id: number; full_name: string; email: string | null; phone: string | null; city: string | null;
  country: string | null; home_district: string | null; profession: string | null;
  wants_mentor: boolean; wants_tutor: boolean; wants_sponsor: boolean; wants_chapter: boolean;
  mentor_fields: string | null; tutor_subjects: string | null; tutor_grades: string | null; tutor_mode: string | null;
  rcc_center_id: number | null; rcc_center_name: string | null; hours_per_month: number | null;
  sponsor_students: number | null; chapter_id: number | null; chapter_name: string | null; other_offers: string | null;
  message: string | null; source: string | null; status: string; notes: string | null; created_at: string;
}

type Offer = 'mentor' | 'tutor' | 'sponsor' | 'chapter' | 'other';

const OFFERS: { key: Offer; label: string; field: keyof Volunteer }[] = [
  { key: 'mentor', label: 'Mentor', field: 'wants_mentor' },
  { key: 'tutor', label: 'Tutor', field: 'wants_tutor' },
  { key: 'sponsor', label: 'Sponsor', field: 'wants_sponsor' },
  { key: 'chapter', label: 'Join a chapter', field: 'wants_chapter' },
  { key: 'other', label: 'Other ways to help', field: 'other_offers' },
];

const STATUS_BADGE: Record<string, string> = {
  NEW: 'badge-primary', CONTACTED: 'badge-info', ONBOARDED: 'badge-success', DECLINED: 'badge-secondary',
};

/**
 * Admin > Volunteers: people who offered to mentor, tutor, sponsor students or join a chapter through the public
 * website (biharanjuman.com "Volunteer with us"). Staff record how far the follow-up got and keep internal notes.
 */
@Component({
  selector: 'app-admin-volunteers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Volunteers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-6 col-md-3" *ngFor="let s of statusKeys">
        <div class="card mb-3" role="button" [class.border-primary]="status === s" (click)="status = status === s ? '' : s; table.filter()">
          <div class="card-body py-2">
            <h3 class="my-1">{{ counts[s] || 0 }}</h3>
            <span class="text-muted">{{ statuses[s] }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <p class="text-muted">
          People who offered to help through the website. Contact them, then record how it went; notes are internal.
          <a *ngIf="canOptions" routerLink="/admin/volunteer-options">Change the ways to help on the form</a>
        </p>
        <div class="row mb-3">
          <div class="col-12 col-md-5 mb-2 mb-md-0">
            <input class="form-control" placeholder="Search name, email, phone, city, district, profession" [(ngModel)]="q" (ngModelChange)="table.search()">
          </div>
          <div class="col-6 col-md-3">
            <select class="form-control" [(ngModel)]="offer" (ngModelChange)="table.filter()" aria-label="Offer">
              <option value="">Any offer</option>
              <option *ngFor="let o of offers" [value]="o.key">{{ o.label }}</option>
            </select>
          </div>
          <div class="col-6 col-md-3">
            <select class="form-control" [(ngModel)]="status" (ngModelChange)="table.filter()" aria-label="Status">
              <option value="">Any status</option>
              <option *ngFor="let s of statusKeys" [value]="s">{{ statuses[s] }}</option>
            </select>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-0">
            <thead><tr><th>Volunteer</th><th>Offers</th><th>Lives in</th><th>Signed up</th><th>Status</th><th></th></tr></thead>
            <tbody>
              <tr *ngIf="table.loading && !table.rows.length"><td colspan="6" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!table.loading && !table.rows.length"><td colspan="6" class="text-center text-muted">No volunteers found.</td></tr>
              <tr *ngFor="let r of table.rows">
                <td><strong>{{ r.full_name }}</strong>
                  <div class="small text-muted">{{ r.email }}<span *ngIf="r.email && r.phone"> · </span>{{ r.phone }}</div></td>
                <td><span *ngFor="let o of offersOf(r)" class="badge badge-light border mr-1">{{ o }}</span></td>
                <td>{{ place(r) || '--' }}</td>
                <td>{{ r.created_at | date: 'mediumDate' }}</td>
                <td><span class="badge" [ngClass]="badge(r.status)">{{ statuses[r.status] || r.status }}</span></td>
                <td class="text-right"><button class="btn btn-xs btn-outline-primary" (click)="open(r)">{{ canEdit ? 'Follow up' : 'View' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
      </div>
    </div>

    <div *ngIf="selected as v" class="modal fade show d-block" tabindex="-1" (click)="selected = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ v.full_name }}</h5>
            <button type="button" class="close" (click)="selected = null" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <dl class="row mb-0">
              <dt class="col-sm-4">Email</dt><dd class="col-sm-8"><a *ngIf="v.email" [href]="'mailto:' + v.email">{{ v.email }}</a><span *ngIf="!v.email">--</span></dd>
              <dt class="col-sm-4">Phone</dt><dd class="col-sm-8"><a *ngIf="v.phone" [href]="'tel:' + v.phone">{{ v.phone }}</a><span *ngIf="!v.phone">--</span></dd>
              <dt class="col-sm-4">Lives in</dt><dd class="col-sm-8">{{ place(v) || '--' }}</dd>
              <dt class="col-sm-4">Home district</dt><dd class="col-sm-8">{{ v.home_district || '--' }}</dd>
              <dt class="col-sm-4">Profession</dt><dd class="col-sm-8">{{ v.profession || '--' }}</dd>
              <dt class="col-sm-4">Signed up</dt><dd class="col-sm-8">{{ v.created_at | date: 'medium' }}<span *ngIf="v.source" class="text-muted"> via {{ v.source }}</span></dd>
            </dl>

            <ng-container *ngIf="v.wants_mentor">
              <h5 class="mt-3">Mentor</h5>
              <dl class="row mb-0">
                <dt class="col-sm-4">Fields</dt><dd class="col-sm-8">{{ list(v.mentor_fields) || '--' }}</dd>
              </dl>
            </ng-container>
            <ng-container *ngIf="v.wants_tutor">
              <h5 class="mt-3">Tutor</h5>
              <dl class="row mb-0">
                <dt class="col-sm-4">Subjects</dt><dd class="col-sm-8">{{ list(v.tutor_subjects) || '--' }}</dd>
                <dt class="col-sm-4">Grades</dt><dd class="col-sm-8">{{ list(v.tutor_grades) || '--' }}</dd>
                <dt class="col-sm-4">How</dt><dd class="col-sm-8">{{ modes[v.tutor_mode || ''] || '--' }}</dd>
                <dt class="col-sm-4">RCC center</dt><dd class="col-sm-8">{{ v.rcc_center_name || 'Any' }}</dd>
              </dl>
            </ng-container>
            <dl class="row mb-0" *ngIf="(v.wants_mentor || v.wants_tutor) && v.hours_per_month">
              <dt class="col-sm-4">Hours per month</dt><dd class="col-sm-8">{{ v.hours_per_month }}</dd>
            </dl>
            <ng-container *ngIf="v.wants_sponsor">
              <h5 class="mt-3">Sponsor</h5>
              <dl class="row mb-0">
                <dt class="col-sm-4">Students</dt><dd class="col-sm-8">{{ v.sponsor_students || 'Not sure yet' }}</dd>
              </dl>
            </ng-container>
            <ng-container *ngIf="v.wants_chapter">
              <h5 class="mt-3">Chapter</h5>
              <p class="mb-0">{{ v.chapter_name || 'No chapter in their district yet: would like to help start one.' }}</p>
            </ng-container>
            <ng-container *ngIf="v.other_offers">
              <h5 class="mt-3">Other ways to help</h5>
              <p class="mb-0" style="white-space: pre-line">{{ v.other_offers }}</p>
            </ng-container>
            <ng-container *ngIf="v.message">
              <h5 class="mt-3">Message</h5>
              <p class="mb-0" style="white-space: pre-line">{{ v.message }}</p>
            </ng-container>

            <hr>
            <fieldset [disabled]="!canEdit">
              <div class="form-group">
                <label for="vol-status">Follow-up status</label>
                <select id="vol-status" class="form-control" [(ngModel)]="editStatus">
                  <option *ngFor="let s of statusKeys" [value]="s">{{ statuses[s] }}</option>
                </select>
              </div>
              <div class="form-group mb-0">
                <label for="vol-notes">Internal notes</label>
                <textarea id="vol-notes" class="form-control" rows="3" maxlength="2000" [(ngModel)]="editNotes"
                          placeholder="e.g. Called on Sunday; will tutor maths at RCC Hazaribagh from March"></textarea>
              </div>
            </fieldset>
          </div>
          <div class="modal-footer">
            <button *ngIf="canEdit" type="button" class="btn btn-outline-danger mr-auto" (click)="remove(v)" [disabled]="saving">Delete</button>
            <button type="button" class="btn btn-light" (click)="selected = null">{{ canEdit ? 'Cancel' : 'Close' }}</button>
            <button *ngIf="canEdit" type="button" class="btn btn-primary" (click)="save(v)" [disabled]="saving">{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminVolunteersComponent implements OnInit, OnDestroy {
  readonly offers = OFFERS;
  q = '';
  offer: Offer | '' = '';
  status = '';
  readonly table = new ServerTable<Volunteer, PagedResult<Volunteer> & { counts: Record<string, number>; statuses: Record<string, string>; tutorModes: Record<string, string> }>(
    (p) => this.api.get('/admin/volunteers', { q: this.q, status: this.status, offer: this.offer, ...p }),
    (e) => (this.error = errorText(e, 'Could not load the volunteers.')));
  saving = false;
  message = '';
  error = '';
  selected: Volunteer | null = null;
  editStatus = 'NEW';
  editNotes = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('VOLUNTEERS', 'EDIT'); }
  get canOptions(): boolean { return this.auth.can('VOLUNTEER_OPTIONS'); }

  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { this.table.destroy(); }

  load(): void { this.table.load(); }

  /** Counts by status (all sign-ups), the status names and the tutoring modes, from the last answer. */
  get counts(): Record<string, number> { return this.table.last?.counts ?? {}; }
  get statuses(): Record<string, string> { return this.table.last?.statuses ?? {}; }
  get statusKeys(): string[] { return Object.keys(this.statuses); }
  get modes(): Record<string, string> { return this.table.last?.tutorModes ?? {}; }

  offersOf(r: Volunteer): string[] {
    // other ways to help: the option titles, e.g. "Organise events: Patna, Online" -> "Organise events"
    const other = (r.other_offers ?? '').split('\n').map((l) => l.split(':')[0].trim()).filter(Boolean);
    return [...OFFERS.filter((o) => o.key !== 'other' && r[o.field]).map((o) => o.label), ...other];
  }

  place(r: Volunteer): string {
    return [r.city, r.country].filter(Boolean).join(', ');
  }

  list(csv: string | null): string {
    return (csv ?? '').split(',').map((s) => s.trim()).filter(Boolean).join(', ');
  }

  badge(status: string): string {
    return STATUS_BADGE[status] ?? 'badge-light';
  }

  open(r: Volunteer): void {
    this.selected = r;
    this.editStatus = r.status;
    this.editNotes = r.notes ?? '';
  }

  save(v: Volunteer): void {
    this.saving = true;
    this.api.put<{ message: string }>(`/admin/volunteers/${v.volunteer_id}`, { status: this.editStatus, notes: this.editNotes }).subscribe({
      next: (r) => { this.saving = false; this.selected = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save.'); }
    });
  }

  remove(v: Volunteer): void {
    if (!confirm(`Delete the sign-up of ${v.full_name}? This cannot be undone.`)) return;
    this.saving = true;
    this.api.delete<{ message: string }>(`/admin/volunteers/${v.volunteer_id}`).subscribe({
      next: (r) => { this.saving = false; this.selected = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not delete.'); }
    });
  }
}
