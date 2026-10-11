import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { DESIGNATIONS, RebSession, RebStaff, TRACKS } from './reb.models';

interface AssignmentSection { id: number; label: string; subjects: { subject_id: number; subject_name: string; track: string; staff_id: number | null }[] }

/**
 * Rahbar-e-Banaat > Teachers & Staff: the people who work at REB (optionally with a Rahbar login, for later
 * attendance and marks entry) and who teaches which subject in which class.
 */
@Component({
  selector: 'app-reb-staff',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Teachers &amp; Staff</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'staff'" (click)="$event.preventDefault(); tab = 'staff'">Staff</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'teaching'" (click)="$event.preventDefault(); openTeaching()">Who teaches what</a></li>
    </ul>

    <div *ngIf="tab === 'staff'" class="card"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <input class="form-control mb-2 mr-2" style="max-width: 280px" placeholder="Search name, phone" [(ngModel)]="q" aria-label="Search staff">
        <div class="d-flex align-items-center mb-2">
          <div class="custom-control custom-checkbox mr-3"><input type="checkbox" class="custom-control-input" id="show-left" [(ngModel)]="showLeft"><label class="custom-control-label" for="show-left">Show staff who left</label></div>
          <button *ngIf="canEdit" class="btn btn-primary btn-sm" (click)="edit()"><i class="mdi mdi-plus mr-1"></i>Add staff</button>
        </div>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Name</th><th>Role</th><th>Phone</th><th>Subjects this session</th><th>Rahbar login</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!visible.length"><td colspan="6" class="text-center text-muted">No staff yet.</td></tr>
          <tr *ngFor="let s of visible">
            <td><strong>{{ s.name }}</strong><div class="small text-muted">{{ s.qualification }}</div></td>
            <td>{{ designations[s.designation] }}<span *ngIf="s.status === 'LEFT'" class="badge badge-secondary ml-1">Left</span></td>
            <td>{{ s.phone || '--' }}</td>
            <td>{{ s.subjects_this_session || '' }}</td>
            <td class="small">{{ s.login || '--' }}</td>
            <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-outline-primary" (click)="edit(s)">Edit</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="tab === 'teaching'" class="card"><div class="card-body">
      <div class="d-flex flex-wrap align-items-end justify-content-between mb-3">
        <div class="form-group mb-2 mr-3"><label for="t-session">Session</label>
          <select id="t-session" class="form-control" [(ngModel)]="sessionId" (ngModelChange)="loadTeaching()">
            <option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option></select></div>
        <button *ngIf="canEdit && teaching.length" class="btn btn-primary mb-2" (click)="saveTeaching()" [disabled]="busy">Save</button>
      </div>
      <p *ngIf="!teaching.length" class="text-muted">No sections in this session yet, or their classes have no subjects. Set them up in REB Setup.</p>
      <div class="row">
        <div class="col-lg-6" *ngFor="let sec of teaching">
          <div class="border rounded p-2 mb-3">
            <h5 class="mt-0">{{ sec.label }}</h5>
            <p *ngIf="!sec.subjects.length" class="small text-muted mb-0">This class has no subjects yet (REB Setup &gt; Class subjects).</p>
            <div class="d-flex align-items-center mb-1" *ngFor="let sub of sec.subjects">
              <span class="flex-fill small">{{ sub.subject_name }} <span class="text-muted">{{ tracks[sub.track] }}</span></span>
              <select class="form-control form-control-sm" style="max-width: 200px" [(ngModel)]="sub.staff_id" [disabled]="!canEdit" [attr.aria-label]="'Teacher of ' + sub.subject_name + ' in ' + sec.label">
                <option [ngValue]="null">--</option>
                <option *ngFor="let t of teachers" [ngValue]="t.id">{{ t.name }}</option>
              </select>
            </div>
          </div>
        </div>
      </div>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ form.id ? 'Edit staff member' : 'Add staff member' }}</h5><button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body"><div class="row">
            <div class="col-md-6 form-group"><label for="s-name">Name *</label><input id="s-name" class="form-control" [(ngModel)]="form.d['name']"></div>
            <div class="col-md-6 form-group"><label for="s-role">Role</label>
              <select id="s-role" class="form-control" [(ngModel)]="form.d['designation']"><option *ngFor="let k of designationKeys" [value]="k">{{ designations[k] }}</option></select></div>
            <div class="col-md-6 form-group"><label for="s-phone">Phone</label><input id="s-phone" type="tel" class="form-control" [(ngModel)]="form.d['phone']"></div>
            <div class="col-md-6 form-group"><label for="s-email">Email</label><input id="s-email" type="email" class="form-control" [(ngModel)]="form.d['email']"></div>
            <div class="col-md-6 form-group"><label for="s-qual">Qualification</label><input id="s-qual" class="form-control" [(ngModel)]="form.d['qualification']"></div>
            <div class="col-6 col-md-3 form-group"><label for="s-join">Joined on</label><input id="s-join" type="date" class="form-control" [(ngModel)]="form.d['joinedOn']"></div>
            <div class="col-6 col-md-3 form-group"><label for="s-status">Status</label>
              <select id="s-status" class="form-control" [(ngModel)]="form.d['status']"><option value="ACTIVE">Working</option><option value="LEFT">Left</option></select></div>
            <div class="col-md-6 form-group" *ngIf="form.d['status'] === 'LEFT'"><label for="s-left">Left on</label><input id="s-left" type="date" class="form-control" [(ngModel)]="form.d['leftOn']"></div>
            <div class="col-12 form-group"><label for="s-login">Rahbar login (optional)</label>
              <input id="s-login" type="email" class="form-control" [(ngModel)]="form.d['loginEmail']" placeholder="Email of her Rahbar account">
              <small class="text-muted">Link a Rahbar account (made in Manage Users) so she can later mark attendance and enter marks for her classes. Leave empty for none.</small></div>
            <div class="col-12 form-group mb-0"><label for="s-notes">Notes</label><textarea id="s-notes" class="form-control" rows="2" [(ngModel)]="form.d['notes']"></textarea></div>
          </div></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="busy || !form.d['name']">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebStaffComponent implements OnInit {
  readonly designations = DESIGNATIONS;
  readonly designationKeys = Object.keys(DESIGNATIONS);
  readonly tracks = TRACKS;

  tab: 'staff' | 'teaching' = 'staff';
  staff: RebStaff[] = [];
  sessions: RebSession[] = [];
  sessionId: number | null = null;
  teaching: AssignmentSection[] = [];
  teachers: { id: number; name: string }[] = [];
  q = '';
  showLeft = false;
  form: { id: number | null; d: Record<string, unknown> } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_STAFF', 'EDIT'); }

  get visible(): RebStaff[] {
    const q = this.q.trim().toLowerCase();
    return this.staff.filter((s) => (this.showLeft || s.status === 'ACTIVE')
      && (!q || [s.name, s.phone, s.email].some((v) => (v ?? '').toLowerCase().includes(q))));
  }

  ngOnInit(): void {
    this.load();
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => { this.sessions = r; this.sessionId = (r.find((s) => s.is_current) ?? r[0])?.id ?? null; },
      error: () => (this.sessions = [])
    });
  }

  load(): void {
    this.api.get<RebStaff[]>('/admin/reb/staff').subscribe({ next: (r) => (this.staff = r), error: (e) => (this.error = errorText(e, 'Could not load the staff.')) });
  }

  edit(s?: RebStaff): void {
    this.form = { id: s?.id ?? null, d: {
      name: s?.name ?? '', designation: s?.designation ?? 'TEACHER', phone: s?.phone ?? '', email: s?.email ?? '',
      qualification: s?.qualification ?? '', joinedOn: s?.joined_on ?? '', leftOn: s?.left_on ?? '', status: s?.status ?? 'ACTIVE',
      notes: s?.notes ?? '', loginEmail: s?.login ?? ''
    } };
  }

  save(): void {
    const f = this.form;
    if (!f) return;
    this.busy = true;
    const req = f.id ? this.api.put<{ message: string }>(`/admin/reb/staff/${f.id}`, f.d) : this.api.post<{ message: string }>('/admin/reb/staff', f.d);
    req.subscribe({
      next: (r) => { this.busy = false; this.form = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }

  openTeaching(): void {
    this.tab = 'teaching';
    this.loadTeaching();
  }

  loadTeaching(): void {
    if (!this.sessionId) { this.teaching = []; return; }
    this.api.get<{ sections: AssignmentSection[]; staff: { id: number; name: string }[] }>('/admin/reb/assignments', { sessionId: this.sessionId }).subscribe({
      next: (r) => { this.teaching = r.sections; this.teachers = r.staff; },
      error: (e) => (this.error = errorText(e, 'Could not load who teaches what.'))
    });
  }

  saveTeaching(): void {
    const assignments = this.teaching.flatMap((sec) => sec.subjects.map((s) => ({ sectionId: sec.id, subjectId: s.subject_id, staffId: s.staff_id })));
    this.busy = true;
    this.api.put<{ message: string }>('/admin/reb/assignments', { sessionId: this.sessionId, assignments }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }
}
