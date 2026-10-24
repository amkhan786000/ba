import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

type Tab = 'openings' | 'placements' | 'employers' | 'guidance';
type Row = Record<string, any>;

export const OPENING_KINDS: Record<string, string> = { JOB: 'Job', INTERNSHIP: 'Internship', TRAINING: 'Training' };
export const PLACEMENT_KINDS: Record<string, string> = { JOB: 'Job', INTERNSHIP: 'Internship', HIGHER_STUDIES: 'Higher studies', SELF_EMPLOYED: 'Own work / business' };
export const TOPICS: Record<string, string> = { COURSE: 'Choosing a course', JOBS: 'Finding a job', HIGHER_STUDIES: 'Higher studies', EXAMS: 'Entrance / competitive exams', OTHER: 'Something else' };
const INTEREST: Record<string, string> = { INTERESTED: 'Interested', APPLIED: 'Applied', SELECTED: 'Selected', NOT_SELECTED: 'Not selected' };
const GUIDANCE: Record<string, string> = { OPEN: 'New', SCHEDULED: 'Session set', DONE: 'Done', CLOSED: 'Closed' };

/**
 * Students > Careers: job / internship openings and who is interested, placements (students' own ones to confirm),
 * employers, and career guidance requests.
 */
@Component({
  selector: 'app-careers',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Careers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <ul class="nav nav-pills mb-3">
      <li class="nav-item" *ngFor="let t of tabs"><a href="" class="nav-link" [class.active]="tab === t.key" (click)="$event.preventDefault(); go(t.key)">{{ t.label }}</a></li>
    </ul>

    <!-- openings -->
    <div class="card" *ngIf="tab === 'openings'"><div class="card-body">
      <div class="d-flex mb-2"><h4 class="header-title mb-0 mr-auto">Job & internship openings</h4>
        <button *ngIf="canEdit" class="btn btn-sm btn-primary" (click)="edit('opening', { kind: 'JOB', status: 'OPEN' })"><i class="mdi mdi-plus"></i> New opening</button></div>
      <p class="small text-muted">Students and graduates see open ones on their Careers page and can say they're interested.</p>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Opening</th><th>Where</th><th>Apply by</th><th class="text-right">Interested</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="5" class="text-muted text-center">No openings yet.</td></tr>
          <tr *ngFor="let o of rows">
            <td><strong>{{ o['title'] }}</strong> <span class="badge badge-light border">{{ kinds[o['kind']] }}</span>
              <span *ngIf="!o['open_now']" class="badge badge-secondary">Closed</span><div class="small text-muted">{{ o['organisation'] }}</div></td>
            <td>{{ o['location'] || '-' }}</td>
            <td>{{ o['last_date'] ? (o['last_date'] | appDate) : '-' }}</td>
            <td class="text-right"><a href="" (click)="$event.preventDefault(); showInterested(o)">{{ o['interested'] }}</a></td>
            <td class="text-right text-nowrap" *ngIf="canEdit">
              <button class="btn btn-xs btn-outline-primary" (click)="edit('opening', o)">Edit</button>
              <button class="btn btn-xs btn-outline-danger ml-1" (click)="remove('opportunities', o['id'], o['title'])"><i class="mdi mdi-delete"></i></button></td>
          </tr>
        </tbody>
      </table></div>
      <div *ngIf="interested" class="mt-3">
        <h5>Interested in {{ interested.title }}</h5>
        <p *ngIf="!interested.rows.length" class="text-muted small">Nobody yet.</p>
        <table class="table table-sm" *ngIf="interested.rows.length"><tbody>
          <tr *ngFor="let i of interested.rows">
            <td>{{ i['name'] }} <span class="small text-muted">{{ i['code'] }} · {{ i['study_status'] }}</span><div class="small">{{ i['phone'] }} {{ i['email'] }}</div></td>
            <td class="small">{{ i['chapter_name'] }}</td>
            <td style="width: 180px"><select class="form-control form-control-sm" [disabled]="!canEdit" [(ngModel)]="i['status']" (ngModelChange)="setInterest(i)">
              <option *ngFor="let s of interestStatuses" [value]="s.key">{{ s.label }}</option></select></td>
            <td class="text-right"><button *ngIf="canEdit && i['status'] === 'SELECTED'" class="btn btn-xs btn-success"
                (click)="edit('placement', { userId: i['user_id'], studentName: i['name'], kind: interested.kind === 'INTERNSHIP' ? 'INTERNSHIP' : 'JOB', organisation: interested.organisation, roleTitle: interested.title, opportunityId: interested.id })">Record placement</button></td>
          </tr>
        </tbody></table>
      </div>
    </div></div>

    <!-- placements -->
    <div class="card" *ngIf="tab === 'placements'"><div class="card-body">
      <div class="d-flex flex-wrap mb-2" style="gap: 8px"><h4 class="header-title mb-0 mr-auto">Placements</h4>
        <select class="form-control form-control-sm w-auto" [(ngModel)]="confirmed" (ngModelChange)="load()" aria-label="Show">
          <option [ngValue]="null">All</option><option [ngValue]="false">To confirm</option><option [ngValue]="true">Confirmed</option></select>
        <input class="form-control form-control-sm w-auto" placeholder="Search" [(ngModel)]="q" (ngModelChange)="load()">
        <button *ngIf="canEdit" class="btn btn-sm btn-primary" (click)="edit('placement', { kind: 'JOB' })"><i class="mdi mdi-plus"></i> Record placement</button></div>
      <p class="small text-muted">A confirmed job, higher studies or own work of a graduate also updates their Alumni profile.</p>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Student</th><th>Placement</th><th>From</th><th></th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="5" class="text-muted text-center">No placements.</td></tr>
          <tr *ngFor="let p of rows">
            <td>{{ p['name'] }} <span class="small text-muted">{{ p['code'] }}</span><div class="small text-muted">{{ p['chapter_name'] }} · {{ p['study_status'] }}</div></td>
            <td><span class="badge badge-light border">{{ placementKinds[p['kind']] }}</span> <strong>{{ p['organisation'] }}</strong>
              <div class="small">{{ p['role_title'] }}{{ p['location'] ? ' · ' + p['location'] : '' }}{{ p['salary_band'] ? ' · ' + p['salary_band'] : '' }}</div></td>
            <td>{{ p['start_date'] ? (p['start_date'] | appDate) : '-' }}</td>
            <td><span *ngIf="p['verified_at']" class="badge badge-success">Confirmed</span>
              <span *ngIf="!p['verified_at']" class="badge badge-warning">Added by student</span></td>
            <td class="text-right text-nowrap" *ngIf="canEdit">
              <button *ngIf="!p['verified_at']" class="btn btn-xs btn-success" (click)="confirmPlacement(p)">Confirm</button>
              <button class="btn btn-xs btn-outline-primary ml-1" (click)="edit('placement', p)">Edit</button>
              <button class="btn btn-xs btn-outline-danger ml-1" (click)="remove('placements', p['id'], p['organisation'])"><i class="mdi mdi-delete"></i></button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- employers -->
    <div class="card" *ngIf="tab === 'employers'"><div class="card-body">
      <div class="d-flex mb-2"><h4 class="header-title mb-0 mr-auto">Employers</h4>
        <button *ngIf="canEdit" class="btn btn-sm btn-primary" (click)="edit('employer', { active: true })"><i class="mdi mdi-plus"></i> Add employer</button></div>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Employer</th><th>Contact</th><th class="text-right">Placements</th><th>Notes</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="5" class="text-muted text-center">No employers yet.</td></tr>
          <tr *ngFor="let e of rows" [class.text-muted]="!e['active']">
            <td><strong>{{ e['name'] }}</strong><div class="small text-muted">{{ e['sector'] }}{{ e['city'] ? ' · ' + e['city'] : '' }}</div>
              <a *ngIf="e['website']" [href]="e['website']" target="_blank" rel="noopener" class="small">{{ e['website'] }}</a></td>
            <td class="small">{{ e['contact_person'] }}<div>{{ e['contact_phone'] }}</div><div>{{ e['contact_email'] }}</div></td>
            <td class="text-right">{{ e['placements'] }}</td>
            <td class="small">{{ e['notes'] }}</td>
            <td class="text-right text-nowrap" *ngIf="canEdit">
              <button class="btn btn-xs btn-outline-primary" (click)="edit('employer', e)">Edit</button>
              <button class="btn btn-xs btn-outline-danger ml-1" (click)="remove('employers', e['id'], e['name'])"><i class="mdi mdi-delete"></i></button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- guidance -->
    <div class="card" *ngIf="tab === 'guidance'"><div class="card-body">
      <div class="d-flex mb-2"><h4 class="header-title mb-0 mr-auto">Career guidance requests</h4>
        <select class="form-control form-control-sm w-auto" [(ngModel)]="guidanceStatus" (ngModelChange)="load()" aria-label="Status">
          <option value="">All</option><option *ngFor="let s of guidanceStatuses" [value]="s.key">{{ s.label }}</option></select></div>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Student</th><th>Asks about</th><th>Status</th><th>With</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="5" class="text-muted text-center">No requests.</td></tr>
          <tr *ngFor="let g of rows">
            <td>{{ g['name'] }} <span class="small text-muted">{{ g['code'] }}</span><div class="small">{{ g['phone'] }}</div><div class="small text-muted">{{ g['created_at'] | appDate }}</div></td>
            <td><strong>{{ topics[g['topic']] }}</strong><div class="small" style="white-space: pre-line">{{ g['message'] }}</div>
              <div *ngIf="g['outcome']" class="small text-success mt-1">Notes: {{ g['outcome'] }}</div></td>
            <td><span class="badge" [ngClass]="g['status'] === 'OPEN' ? 'badge-warning' : g['status'] === 'SCHEDULED' ? 'badge-info' : 'badge-light border'">{{ guidanceLabels[g['status']] }}</span>
              <div *ngIf="g['session_at']" class="small">{{ g['session_at'] | appDate: 'datetime' }}</div></td>
            <td class="small">{{ g['assigned_name'] || '-' }}</td>
            <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary" (click)="edit('guidance', g)">Update</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- one form for all four -->
    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ formTitle }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <div class="row" *ngIf="form.type === 'opening'">
              <div class="col-md-4 form-group"><label>Kind</label><select class="form-control" [(ngModel)]="f['kind']">
                <option *ngFor="let k of kindList" [value]="k.key">{{ k.label }}</option></select></div>
              <div class="col-md-8 form-group"><label>Title *</label><input class="form-control" maxlength="200" [(ngModel)]="f['title']" placeholder="e.g. Lab Technician"></div>
              <div class="col-md-6 form-group"><label>Organisation *</label><input class="form-control" maxlength="200" [(ngModel)]="f['organisation']"></div>
              <div class="col-md-6 form-group"><label>Employer (from your list)</label><select class="form-control" [(ngModel)]="f['employerId']">
                <option [ngValue]="null">--</option><option *ngFor="let e of employerList" [ngValue]="e['id']">{{ e['name'] }}</option></select></div>
              <div class="col-md-6 form-group"><label>Location</label><input class="form-control" maxlength="150" [(ngModel)]="f['location']"></div>
              <div class="col-md-3 form-group"><label>Apply by</label><input type="date" class="form-control" [(ngModel)]="f['lastDate']"></div>
              <div class="col-md-3 form-group"><label>Status</label><select class="form-control" [(ngModel)]="f['status']"><option value="OPEN">Open</option><option value="CLOSED">Closed</option></select></div>
              <div class="col-12 form-group"><label>Who can apply</label><input class="form-control" maxlength="500" [(ngModel)]="f['eligibility']" placeholder="e.g. DMLT final year or graduates"></div>
              <div class="col-12 form-group"><label>Details</label><textarea class="form-control" rows="4" maxlength="3000" [(ngModel)]="f['description']"></textarea></div>
              <div class="col-12 form-group"><label>Apply link</label><input class="form-control" maxlength="500" [(ngModel)]="f['applyLink']" placeholder="https://"></div>
            </div>
            <div class="row" *ngIf="form.type === 'placement'">
              <div class="col-12 form-group" *ngIf="!form.id"><label>Student *</label>
                <div *ngIf="f['userId']; else pickStudent" class="form-control-plaintext"><strong>{{ f['studentName'] }}</strong>
                  <a href="" class="small ml-2" (click)="$event.preventDefault(); f['userId'] = null">change</a></div>
                <ng-template #pickStudent>
                  <input class="form-control" placeholder="Type a name, ID or phone" [(ngModel)]="studentQuery" (ngModelChange)="searchStudents()">
                  <div class="list-group mt-1" *ngIf="studentHits.length">
                    <a href="" class="list-group-item list-group-item-action py-1" *ngFor="let s of studentHits"
                       (click)="$event.preventDefault(); f['userId'] = s['id']; f['studentName'] = s['name']; studentHits = []">
                      {{ s['name'] }} <span class="small text-muted">{{ s['code'] }} · {{ s['study_status'] }}</span></a></div>
                </ng-template></div>
              <div class="col-md-4 form-group"><label>Kind</label><select class="form-control" [(ngModel)]="f['kind']">
                <option *ngFor="let k of placementKindList" [value]="k.key">{{ k.label }}</option></select></div>
              <div class="col-md-8 form-group"><label>{{ f['kind'] === 'HIGHER_STUDIES' ? 'College / university' : 'Organisation' }} *</label><input class="form-control" maxlength="200" [(ngModel)]="f['organisation']"></div>
              <div class="col-md-6 form-group"><label>{{ f['kind'] === 'HIGHER_STUDIES' ? 'Course' : 'Role' }}</label><input class="form-control" maxlength="150" [(ngModel)]="f['roleTitle']"></div>
              <div class="col-md-6 form-group"><label>Employer (from your list)</label><select class="form-control" [(ngModel)]="f['employerId']">
                <option [ngValue]="null">--</option><option *ngFor="let e of employerList" [ngValue]="e['id']">{{ e['name'] }}</option></select></div>
              <div class="col-md-4 form-group"><label>Location</label><input class="form-control" maxlength="150" [(ngModel)]="f['location']"></div>
              <div class="col-md-4 form-group"><label>From</label><input type="date" class="form-control" [(ngModel)]="f['startDate']"></div>
              <div class="col-md-4 form-group"><label>Salary band <span class="small text-muted">(optional)</span></label><input class="form-control" maxlength="30" [(ngModel)]="f['salaryBand']" placeholder="e.g. 1–2 lakh / year"></div>
              <div class="col-12 form-group"><label>Note</label><input class="form-control" maxlength="500" [(ngModel)]="f['note']"></div>
            </div>
            <div class="row" *ngIf="form.type === 'employer'">
              <div class="col-md-6 form-group"><label>Name *</label><input class="form-control" maxlength="200" [(ngModel)]="f['name']"></div>
              <div class="col-md-3 form-group"><label>Sector</label><input class="form-control" maxlength="100" [(ngModel)]="f['sector']" placeholder="e.g. Hospital"></div>
              <div class="col-md-3 form-group"><label>City</label><input class="form-control" maxlength="100" [(ngModel)]="f['city']"></div>
              <div class="col-md-4 form-group"><label>Contact person</label><input class="form-control" maxlength="150" [(ngModel)]="f['contactPerson']"></div>
              <div class="col-md-4 form-group"><label>Phone</label><input class="form-control" maxlength="30" [(ngModel)]="f['contactPhone']"></div>
              <div class="col-md-4 form-group"><label>Email</label><input class="form-control" maxlength="150" [(ngModel)]="f['contactEmail']"></div>
              <div class="col-md-8 form-group"><label>Website</label><input class="form-control" maxlength="300" [(ngModel)]="f['website']" placeholder="https://"></div>
              <div class="col-md-4 form-group d-flex align-items-end"><div class="custom-control custom-checkbox mb-2">
                <input type="checkbox" class="custom-control-input" id="empActive" [(ngModel)]="f['active']"><label class="custom-control-label" for="empActive">Active</label></div></div>
              <div class="col-12 form-group"><label>Notes</label><textarea class="form-control" rows="2" maxlength="1000" [(ngModel)]="f['notes']"></textarea></div>
            </div>
            <div class="row" *ngIf="form.type === 'guidance'">
              <div class="col-md-4 form-group"><label>Status</label><select class="form-control" [(ngModel)]="f['status']">
                <option *ngFor="let s of guidanceStatuses" [value]="s.key">{{ s.label }}</option></select></div>
              <div class="col-md-4 form-group"><label>With</label><select class="form-control" [(ngModel)]="f['assignedTo']">
                <option [ngValue]="null">--</option><option *ngFor="let s of staff" [ngValue]="s['id']">{{ s['name'] }}</option></select></div>
              <div class="col-md-4 form-group"><label>Session</label><input type="datetime-local" class="form-control" [(ngModel)]="f['sessionAt']"></div>
              <div class="col-12 form-group"><label>Notes / advice given <span class="small text-muted">(the student sees these)</span></label>
                <textarea class="form-control" rows="3" maxlength="2000" [(ngModel)]="f['outcome']"></textarea></div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="busy">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CareersComponent implements OnInit {
  readonly tabs: { key: Tab; label: string }[] = [
    { key: 'openings', label: 'Openings' }, { key: 'placements', label: 'Placements' },
    { key: 'employers', label: 'Employers' }, { key: 'guidance', label: 'Guidance requests' }];
  readonly kinds = OPENING_KINDS;
  readonly placementKinds = PLACEMENT_KINDS;
  readonly topics = TOPICS;
  readonly guidanceLabels = GUIDANCE;
  readonly kindList = Object.entries(OPENING_KINDS).map(([key, label]) => ({ key, label }));
  readonly placementKindList = Object.entries(PLACEMENT_KINDS).map(([key, label]) => ({ key, label }));
  readonly interestStatuses = Object.entries(INTEREST).map(([key, label]) => ({ key, label }));
  readonly guidanceStatuses = Object.entries(GUIDANCE).map(([key, label]) => ({ key, label }));
  tab: Tab = 'openings';
  rows: Row[] = [];
  employerList: Row[] = [];
  staff: Row[] = [];
  interested: { id: number; title: string; organisation: string; kind: string; rows: Row[] } | null = null;
  q = '';
  confirmed: boolean | null = null;
  guidanceStatus = '';
  form: { type: 'opening' | 'placement' | 'employer' | 'guidance'; id: number | null } | null = null;
  f: Row = {};
  formError = '';
  studentQuery = '';
  studentHits: Row[] = [];
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('CAREERS', 'EDIT'); }
  get formTitle(): string {
    const what = { opening: 'opening', placement: 'placement', employer: 'employer', guidance: 'guidance request' }[this.form!.type];
    return (this.form!.id ? 'Edit ' : this.form!.type === 'guidance' ? 'Update ' : 'New ') + what;
  }

  ngOnInit(): void { this.load(); this.loadEmployers(); }

  go(t: Tab): void { this.tab = t; this.rows = []; this.interested = null; this.load(); }

  load(): void {
    const req = this.tab === 'openings' ? this.api.get<Row[]>('/admin/careers/opportunities')
      : this.tab === 'placements' ? this.api.get<Row[]>('/admin/careers/placements', { q: this.q, confirmed: this.confirmed })
      : this.tab === 'employers' ? this.api.get<Row[]>('/admin/careers/employers')
      : null;
    if (req) {
      req.subscribe({ next: (r) => (this.rows = r), error: (e) => (this.error = errorText(e, 'Could not load.')) });
    } else {
      this.api.get<{ rows: Row[]; staff: Row[] }>('/admin/careers/guidance', { status: this.guidanceStatus }).subscribe({
        next: (r) => { this.rows = r.rows; this.staff = r.staff; }, error: (e) => (this.error = errorText(e, 'Could not load.'))
      });
    }
  }

  loadEmployers(): void { this.api.get<Row[]>('/admin/careers/employers').subscribe({ next: (r) => (this.employerList = r.filter((e) => e['active'])) }); }

  showInterested(o: Row): void {
    this.api.get<Row[]>(`/admin/careers/opportunities/${o['id']}/interested`).subscribe({
      next: (rows) => (this.interested = { id: o['id'], title: o['title'], organisation: o['organisation'], kind: o['kind'], rows }),
      error: (e) => (this.error = errorText(e, 'Could not load.'))
    });
  }

  setInterest(i: Row): void {
    this.api.put(`/admin/careers/interests/${i['id']}`, { status: i['status'], note: i['note'] }).subscribe({ error: (e) => (this.error = errorText(e, 'Could not save.')) });
  }

  /** Opens the form with a row from the server (snake_case) or a new one (camelCase). */
  edit(type: 'opening' | 'placement' | 'employer' | 'guidance', r: Row): void {
    const camel = (k: string) => k.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
    this.f = {};
    Object.entries(r).forEach(([k, v]) => (this.f[camel(k)] = v));
    if (type === 'guidance' && this.f['sessionAt']) this.f['sessionAt'] = String(this.f['sessionAt']).slice(0, 16);
    if (type === 'placement' && r['name']) this.f['studentName'] = r['name'];
    this.form = { type, id: r['id'] ?? null };
    this.formError = '';
    this.studentQuery = '';
    this.studentHits = [];
  }

  searchStudents(): void {
    if (this.studentQuery.trim().length < 2) { this.studentHits = []; return; }
    this.api.get<Row[]>('/admin/careers/students', { q: this.studentQuery }).subscribe({ next: (r) => (this.studentHits = r) });
  }

  save(): void {
    if (!this.form) return;
    const path = { opening: 'opportunities', placement: 'placements', employer: 'employers', guidance: 'guidance' }[this.form.type];
    const id = this.form.id;
    this.busy = true;
    const req = id ? this.api.put(`/admin/careers/${path}/${id}`, this.f) : this.api.post(`/admin/careers/${path}`, this.f);
    req.subscribe({
      next: () => {
        this.busy = false;
        const t = this.form!.type;
        this.form = null;
        this.message = 'Saved.';
        if (t === 'employer') this.loadEmployers();
        if (t === 'placement' && this.tab === 'openings') { this.go('placements'); return; }
        this.load();
      },
      error: (e) => { this.busy = false; this.formError = errorText(e, 'Could not save.'); }
    });
  }

  confirmPlacement(p: Row): void {
    this.api.post<{ message: string }>(`/admin/careers/placements/${p['id']}/confirm`, {}).subscribe({
      next: (m) => { this.message = m.message; this.load(); }, error: (e) => (this.error = errorText(e, 'Could not confirm.'))
    });
  }

  remove(path: string, id: number, name: string): void {
    if (!confirm(`Delete ${name}?`)) return;
    this.api.delete<{ message: string }>(`/admin/careers/${path}/${id}`).subscribe({
      next: (m) => { this.message = m.message; this.load(); if (path === 'employers') this.loadEmployers(); },
      error: (e) => (this.error = errorText(e, 'Could not delete.'))
    });
  }
}
