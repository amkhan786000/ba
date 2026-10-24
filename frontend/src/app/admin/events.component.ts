import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

type Row = Record<string, any>;
export const EVENT_KINDS: Record<string, string> = {
  WORKSHOP: 'Workshop', CAREER: 'Career session', PARENTS: "Parents' meeting", EXAM_PREP: 'Exam preparation', CEREMONY: 'Ceremony', OTHER: 'Other'
};
const AUDIENCES: Record<string, string> = { SCHOLARS: 'Scholarship students', COACHING: 'Coaching students', SPONSORS: 'Sponsors', PARENTS: 'Parents', ALL: 'Everyone' };

/**
 * Community > Events: workshops, career sessions, parents' meetings... Students and sponsors sign up in their portal;
 * staff add others (coaching students, parents as guests) and mark who came. Chapter leads and coaching coordinators
 * run their own chapter's / center's events.
 */
@Component({
  selector: 'app-events',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Events & Workshops</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <div class="btn-group">
        <button class="btn btn-sm" [ngClass]="upcoming === true ? 'btn-secondary' : 'btn-light'" (click)="show(true)">Upcoming</button>
        <button class="btn btn-sm" [ngClass]="upcoming === false ? 'btn-secondary' : 'btn-light'" (click)="show(false)">Past</button>
      </div>
      <button *ngIf="canEdit" class="btn btn-sm btn-primary ml-auto" (click)="openForm(null)"><i class="mdi mdi-plus"></i> New event</button>
    </div>

    <div class="card"><div class="card-body">
      <p *ngIf="d && !d.rows.length" class="text-muted mb-0">{{ upcoming ? 'No upcoming events.' : 'No past events.' }}</p>
      <div class="table-responsive" *ngIf="d?.rows?.length"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Event</th><th>When</th><th>For</th><th class="text-right">Registered</th><th class="text-right">Came</th><th></th></tr></thead>
        <tbody><tr *ngFor="let e of d!.rows">
          <td><strong>{{ e['title'] }}</strong> <span class="badge badge-light border">{{ kinds[e['kind']] }}</span>
            <span *ngIf="e['status'] !== 'SCHEDULED'" class="badge" [ngClass]="e['status'] === 'CANCELLED' ? 'badge-danger' : 'badge-secondary'">{{ e['status'] === 'CANCELLED' ? 'Cancelled' : 'Done' }}</span>
            <div class="small text-muted">{{ e['venue'] }}{{ e['chapter_name'] ? ' · ' + e['chapter_name'] : '' }}{{ e['center_name'] ? ' · ' + e['center_name'] : '' }}</div></td>
          <td>{{ e['starts_at'] | appDate: 'datetime' }}</td>
          <td class="small">{{ audiences[e['audience']] }}</td>
          <td class="text-right">{{ e['registered'] }}{{ e['capacity'] ? ' / ' + e['capacity'] : '' }}</td>
          <td class="text-right">{{ e['attended'] }}</td>
          <td class="text-right text-nowrap">
            <button class="btn btn-xs btn-outline-primary" (click)="openPeople(e)"><i class="mdi mdi-account-multiple"></i> People</button>
            <ng-container *ngIf="e['can_edit']">
              <button class="btn btn-xs btn-outline-secondary ml-1" (click)="openForm(e)">Edit</button>
              <button class="btn btn-xs btn-outline-danger ml-1" (click)="remove(e)"><i class="mdi mdi-delete"></i></button></ng-container>
          </td>
        </tr></tbody>
      </table></div>
    </div></div>

    <!-- event form -->
    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ editId ? 'Edit event' : 'New event' }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <div class="row">
              <div class="col-md-8 form-group"><label>Title *</label><input class="form-control" maxlength="200" [(ngModel)]="form['title']" placeholder="e.g. Career guidance after DMLT"></div>
              <div class="col-md-4 form-group"><label>Kind</label><select class="form-control" [(ngModel)]="form['kind']">
                <option *ngFor="let k of kindList" [value]="k.key">{{ k.label }}</option></select></div>
              <div class="col-md-4 form-group"><label>Starts *</label><input type="datetime-local" class="form-control" [(ngModel)]="form['startsAt']"></div>
              <div class="col-md-4 form-group"><label>Ends</label><input type="datetime-local" class="form-control" [(ngModel)]="form['endsAt']"></div>
              <div class="col-md-4 form-group"><label>For</label><select class="form-control" [(ngModel)]="form['audience']">
                <option *ngFor="let a of audienceList" [value]="a.key">{{ a.label }}</option></select></div>
              <div class="col-md-6 form-group"><label>Venue</label><input class="form-control" maxlength="300" [(ngModel)]="form['venue']"></div>
              <div class="col-md-6 form-group"><label>Online link</label><input class="form-control" maxlength="500" [(ngModel)]="form['onlineLink']" placeholder="https://"></div>
              <ng-container *ngIf="d?.scope === 'ALL'">
                <div class="col-md-6 form-group"><label>Chapter <span class="small text-muted">(empty: everyone)</span></label><select class="form-control" [(ngModel)]="form['chapterId']">
                  <option [ngValue]="null">All chapters</option><option *ngFor="let c of d!.chapters" [ngValue]="c['chapter_id']">{{ c['chapter_name'] }}</option></select></div>
                <div class="col-md-6 form-group"><label>Coaching center</label><select class="form-control" [(ngModel)]="form['rccCenterId']">
                  <option [ngValue]="null">--</option><option *ngFor="let c of d!.centers" [ngValue]="c['rcc_center_id']">{{ c['center_name'] }}</option></select></div>
              </ng-container>
              <div class="col-md-3 form-group"><label>Places</label><input type="number" min="1" class="form-control" [(ngModel)]="form['capacity']" placeholder="No limit"></div>
              <div class="col-md-3 form-group"><label>Status</label><select class="form-control" [(ngModel)]="form['status']">
                <option value="SCHEDULED">Scheduled</option><option value="DONE">Done</option><option value="CANCELLED">Cancelled</option></select></div>
              <div class="col-md-6 form-group d-flex align-items-end flex-column justify-content-end">
                <div class="custom-control custom-checkbox w-100"><input type="checkbox" class="custom-control-input" id="evReg" [(ngModel)]="form['registrationOpen']">
                  <label class="custom-control-label" for="evReg">Students / sponsors can sign up in their portal</label></div>
                <div class="custom-control custom-checkbox w-100" *ngIf="!editId"><input type="checkbox" class="custom-control-input" id="evNotify" [(ngModel)]="form['notify']">
                  <label class="custom-control-label" for="evNotify">Tell them now (notification in their portal)</label></div>
              </div>
              <div class="col-12 form-group"><label>Description</label><textarea class="form-control" rows="4" maxlength="3000" [(ngModel)]="form['description']"></textarea></div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="busy">Save</button>
          </div>
        </div>
      </div>
    </div>

    <!-- people -->
    <div *ngIf="people" class="modal fade show d-block" tabindex="-1" (click)="people = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ people.event['title'] }} <small class="text-muted">{{ people.event['starts_at'] | appDate: 'datetime' }}</small></h5>
            <button type="button" class="close" (click)="people = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="peopleError" class="alert alert-danger">{{ peopleError }}</div>
            <div *ngIf="people.can_edit" class="mb-3">
              <div class="d-flex" style="gap: 6px">
                <input class="form-control form-control-sm" placeholder="Add a student, sponsor or coaching student: type a name" [(ngModel)]="who" (ngModelChange)="search()">
                <button class="btn btn-sm btn-light text-nowrap" (click)="guest = guest ? null : { name: '', phone: '' }">+ Guest</button>
              </div>
              <div class="list-group mt-1" *ngIf="hits.length">
                <a href="" class="list-group-item list-group-item-action py-1" *ngFor="let h of hits" (click)="$event.preventDefault(); add(h)">
                  {{ h['name'] }} <span class="small text-muted">{{ h['who'] }}</span></a></div>
              <div *ngIf="guest" class="d-flex mt-2" style="gap: 6px">
                <input class="form-control form-control-sm" placeholder="Guest name (e.g. a parent)" [(ngModel)]="guest.name">
                <input class="form-control form-control-sm" placeholder="Phone" [(ngModel)]="guest.phone">
                <button class="btn btn-sm btn-primary" (click)="add({ guestName: guest.name, guestPhone: guest.phone })" [disabled]="!guest.name.trim()">Add</button>
              </div>
            </div>
            <p *ngIf="!people.rows.length" class="text-muted">Nobody registered yet.</p>
            <table class="table table-sm mb-0" *ngIf="people.rows.length">
              <thead class="thead-light"><tr><th>Name</th><th>Who</th><th>Phone</th><th>Came?</th><th></th></tr></thead>
              <tbody><tr *ngFor="let r of people.rows">
                <td>{{ r['name'] }} <span *ngIf="r['self_registered']" class="badge badge-light border">signed up</span></td>
                <td class="small">{{ r['who'] }}</td><td class="small">{{ r['phone'] }}</td>
                <td>
                  <div class="btn-group btn-group-sm" *ngIf="people.can_edit; else cameShown">
                    <button class="btn" [ngClass]="came[r['id']] === true ? 'btn-success' : 'btn-outline-success'" (click)="came[r['id']] = came[r['id']] === true ? null : true">Yes</button>
                    <button class="btn" [ngClass]="came[r['id']] === false ? 'btn-danger' : 'btn-outline-danger'" (click)="came[r['id']] = came[r['id']] === false ? null : false">No</button>
                  </div>
                  <ng-template #cameShown>{{ r['attended'] === true ? 'Yes' : r['attended'] === false ? 'No' : '-' }}</ng-template>
                </td>
                <td class="text-right"><button *ngIf="people.can_edit" class="btn btn-xs btn-light" (click)="unregister(r)" title="Remove"><i class="mdi mdi-close"></i></button></td>
              </tr></tbody>
            </table>
          </div>
          <div class="modal-footer">
            <span class="small text-muted mr-auto">{{ people.rows.length }} registered</span>
            <button type="button" class="btn btn-light" (click)="people = null">Close</button>
            <button *ngIf="people.can_edit && people.rows.length" type="button" class="btn btn-primary" (click)="saveAttendance()" [disabled]="busy">Save who came</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class EventsComponent implements OnInit {
  readonly kinds = EVENT_KINDS;
  readonly audiences = AUDIENCES;
  readonly kindList = Object.entries(EVENT_KINDS).map(([key, label]) => ({ key, label }));
  readonly audienceList = Object.entries(AUDIENCES).map(([key, label]) => ({ key, label }));
  d: { rows: Row[]; scope: string; chapters: Row[]; centers: Row[] } | null = null;
  upcoming = true;
  form: Row | null = null;
  editId: number | null = null;
  formError = '';
  people: { event: Row; can_edit: boolean; rows: Row[] } | null = null;
  peopleError = '';
  came: Record<number, boolean | null> = {};
  who = '';
  hits: Row[] = [];
  guest: { name: string; phone: string } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('EVENTS', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  show(u: boolean): void { this.upcoming = u; this.load(); }

  load(): void {
    this.api.get<{ rows: Row[]; scope: string; chapters: Row[]; centers: Row[] }>('/admin/events', { upcoming: this.upcoming }).subscribe({
      next: (d) => (this.d = d), error: (e) => (this.error = errorText(e, 'Could not load the events.'))
    });
  }

  openForm(e: Row | null): void {
    this.formError = '';
    this.editId = e ? e['id'] : null;
    const at = (v: unknown) => (v ? String(v).slice(0, 16) : '');
    this.form = e ? {
      title: e['title'], kind: e['kind'], description: e['description'], startsAt: at(e['starts_at']), endsAt: at(e['ends_at']),
      venue: e['venue'], onlineLink: e['online_link'], audience: e['audience'], chapterId: e['chapter_id'], rccCenterId: e['rcc_center_id'],
      capacity: e['capacity'], registrationOpen: e['registration_open'], status: e['status']
    } : { kind: 'WORKSHOP', audience: 'SCHOLARS', status: 'SCHEDULED', registrationOpen: true, notify: true, chapterId: null, rccCenterId: null };
  }

  save(): void {
    if (!this.form) return;
    this.busy = true;
    const req = this.editId ? this.api.put(`/admin/events/${this.editId}`, this.form) : this.api.post('/admin/events', this.form);
    req.subscribe({
      next: () => { this.busy = false; this.form = null; this.message = 'Event saved.'; this.load(); },
      error: (e) => { this.busy = false; this.formError = errorText(e, 'Could not save the event.'); }
    });
  }

  remove(e: Row): void {
    if (!confirm(`Delete ${e['title']} and its registrations? To keep the record, set it to Cancelled instead.`)) return;
    this.api.delete<{ message: string }>(`/admin/events/${e['id']}`).subscribe({
      next: (m) => { this.message = m.message; this.load(); }, error: (err) => (this.error = errorText(err, 'Could not delete.'))
    });
  }

  openPeople(e: Row): void {
    this.peopleError = '';
    this.who = '';
    this.hits = [];
    this.guest = null;
    this.api.get<{ event: Row; can_edit: boolean; rows: Row[] }>(`/admin/events/${e['id']}/registrations`).subscribe({
      next: (p) => { this.people = p; this.came = {}; p.rows.forEach((r) => (this.came[r['id']] = r['attended'] ?? null)); },
      error: (err) => (this.error = errorText(err, 'Could not load the people.'))
    });
  }

  search(): void {
    if (this.who.trim().length < 2) { this.hits = []; return; }
    this.api.get<Row[]>('/admin/events/people', { q: this.who }).subscribe({ next: (h) => (this.hits = h) });
  }

  add(h: Row): void {
    if (!this.people) return;
    const body = h['user_id'] ? { userId: h['user_id'] } : h['coaching_student_id'] ? { coachingStudentId: h['coaching_student_id'] } : h;
    const id = this.people.event['id'];
    this.api.post(`/admin/events/${id}/registrations`, body).subscribe({
      next: () => { this.openPeople(this.people!.event); this.load(); },
      error: (e) => (this.peopleError = errorText(e, 'Could not add.'))
    });
  }

  unregister(r: Row): void {
    if (!this.people) return;
    this.api.delete(`/admin/events/${this.people.event['id']}/registrations/${r['id']}`).subscribe({
      next: () => { this.openPeople(this.people!.event); this.load(); }, error: (e) => (this.peopleError = errorText(e, 'Could not remove.'))
    });
  }

  saveAttendance(): void {
    if (!this.people) return;
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/events/${this.people.event['id']}/attendance`, { attended: this.came }).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.people = null; this.load(); },
      error: (e) => { this.busy = false; this.peopleError = errorText(e, 'Could not save.'); }
    });
  }
}
