import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { RebStudentFeesComponent } from './reb-student-fees.component';
import { isoDate, uploadUrl } from '../../shared/format';
import {
  CATEGORIES, DOCUMENT_KINDS, ENROLLMENT_STATUSES, FEE_PLANS, RELATIONS, RebGuardian, RebSectionRow, RebSession, STATUSES,
  guardianBody, newGuardian, planBadge, statusBadge
} from './reb.models';

interface Profile {
  student: Record<string, unknown>;
  guardians: RebGuardian[];
  enrollments: { id: number; session_id: number; session_name: string; session_current: boolean; section_id: number; section: string; roll_no: string | null; status: string }[];
  documents: { id: number; kind: string; file_path: string; file_name: string | null; created_at: string }[] | null;
  rahbar_user: { id: number; user_id: string | null; name: string } | null;
  private: boolean;
}

/**
 * Rahbar-e-Banaat > Girls > one girl: her details, class and history, parents and guardians, documents, status
 * (left / passed out) and certificates.
 */
@Component({
  selector: 'app-reb-student',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, CardTableDirective, RebStudentFeesComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex flex-wrap align-items-center justify-content-between">
      <h4 class="page-title mb-2">
        <a routerLink="/admin/reb/students" class="text-muted mr-1" aria-label="Back to girls"><i class="mdi mdi-arrow-left"></i></a>
        {{ s ? s['name'] : 'Girl' }}
        <span *ngIf="s" class="badge ml-1" [ngClass]="statusBadge(str(s['status']))">{{ statuses[str(s['status'])] }}</span>
      </h4>
      <div *ngIf="s" class="d-flex flex-wrap">
        <button class="btn btn-sm btn-outline-secondary mr-2 mb-2" (click)="certificate('BONAFIDE')" *ngIf="s['status'] === 'ACTIVE'"><i class="mdi mdi-certificate mr-1"></i>Bonafide certificate</button>
        <button class="btn btn-sm btn-outline-secondary mr-2 mb-2" (click)="certificate('TC')" *ngIf="s['status'] !== 'ACTIVE'"><i class="mdi mdi-certificate mr-1"></i>Transfer certificate</button>
        <button *ngIf="canEdit" class="btn btn-sm btn-outline-primary mb-2" (click)="openStatus()">Change status</button>
      </div>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div *ngIf="p && s" class="row">
      <div class="col-xl-7">
        <div class="card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <h4 class="header-title mb-0">Details</h4>
              <button *ngIf="canEdit && !editing" class="btn btn-sm btn-outline-primary" (click)="startEdit()">Edit</button>
            </div>
            <dl class="row mb-0" *ngIf="!editing">
              <dt class="col-sm-4">Admission no.</dt><dd class="col-sm-8">{{ s['admission_no'] }}</dd>
              <dt class="col-sm-4">Date of birth</dt><dd class="col-sm-8">{{ s['date_of_birth'] ? (str(s['date_of_birth']) | date: 'd MMM yyyy') : '--' }}</dd>
              <dt class="col-sm-4">Admitted on</dt><dd class="col-sm-8">{{ s['admitted_on'] ? (str(s['admitted_on']) | date: 'd MMM yyyy') : '--' }}</dd>
              <dt class="col-sm-4">Fee plan</dt><dd class="col-sm-8"><span class="badge" [ngClass]="planBadge(str(s['fee_plan']))">{{ plans[str(s['fee_plan'])] }}</span></dd>
              <dt class="col-sm-4">Hostel</dt><dd class="col-sm-8">{{ s['residential'] ? 'Lives in the hostel' : 'Day scholar' }}</dd>
              <dt class="col-sm-4">Address</dt><dd class="col-sm-8">{{ s['address'] || '--' }}<span *ngIf="s['district']">, {{ s['district'] }}</span><span *ngIf="s['state']">, {{ s['state'] }}</span></dd>
              <dt class="col-sm-4">Category</dt><dd class="col-sm-8">{{ s['social_category'] || '--' }}</dd>
              <dt class="col-sm-4">Blood group</dt><dd class="col-sm-8">{{ s['blood_group'] || '--' }}</dd>
              <dt class="col-sm-4">Board registration</dt><dd class="col-sm-8">{{ s['board_registration_no'] || '--' }}</dd>
              <dt class="col-sm-4">Previous school</dt><dd class="col-sm-8">{{ s['previous_school'] || '--' }}</dd>
              <ng-container *ngIf="p.private"><dt class="col-sm-4">Aadhaar</dt><dd class="col-sm-8">{{ s['aadhaar_last4'] ? 'XXXX XXXX ' + s['aadhaar_last4'] : '--' }}</dd></ng-container>
              <ng-container *ngIf="s['status'] !== 'ACTIVE'"><dt class="col-sm-4">Left on</dt><dd class="col-sm-8">{{ s['left_on'] ? (str(s['left_on']) | date: 'd MMM yyyy') : '--' }}<span *ngIf="s['leaving_reason']"> · {{ s['leaving_reason'] }}</span></dd></ng-container>
              <ng-container *ngIf="p.rahbar_user"><dt class="col-sm-4">Rahbar scholar</dt><dd class="col-sm-8">{{ p.rahbar_user.name }} {{ p.rahbar_user.user_id ? '(' + p.rahbar_user.user_id + ')' : '' }}</dd></ng-container>
              <ng-container *ngIf="s['notes']"><dt class="col-sm-4">Notes</dt><dd class="col-sm-8" style="white-space: pre-line">{{ s['notes'] }}</dd></ng-container>
            </dl>
            <div *ngIf="editing" class="row">
              <div class="col-md-6 form-group"><label for="e-name">Full name *</label><input id="e-name" class="form-control" [(ngModel)]="e['name']"></div>
              <div class="col-6 col-md-3 form-group"><label for="e-dob">Date of birth</label><input id="e-dob" type="date" class="form-control" [(ngModel)]="e['dateOfBirth']"></div>
              <div class="col-6 col-md-3 form-group"><label for="e-adm">Admitted on</label><input id="e-adm" type="date" class="form-control" [(ngModel)]="e['admittedOn']"></div>
              <div class="col-6 col-md-3 form-group"><label for="e-plan">Fee plan</label>
                <select id="e-plan" class="form-control" [(ngModel)]="e['feePlan']"><option *ngFor="let k of planKeys" [value]="k">{{ plans[k] }}</option></select></div>
              <div class="col-6 col-md-3 form-group"><label for="e-cat">Category</label>
                <select id="e-cat" class="form-control" [(ngModel)]="e['socialCategory']"><option [ngValue]="null">--</option><option *ngFor="let c of categories" [value]="c">{{ c }}</option></select></div>
              <div class="col-6 col-md-3 form-group"><label for="e-blood">Blood group</label><input id="e-blood" class="form-control" [(ngModel)]="e['bloodGroup']"></div>
              <div class="col-6 col-md-3 form-group d-flex align-items-end"><div class="custom-control custom-checkbox mb-2">
                <input type="checkbox" class="custom-control-input" id="e-res" [(ngModel)]="e['residential']"><label class="custom-control-label" for="e-res">Hostel</label></div></div>
              <div class="col-12 form-group"><label for="e-address">Address</label><input id="e-address" class="form-control" [(ngModel)]="e['address']"></div>
              <div class="col-6 form-group"><label for="e-district">District</label><input id="e-district" class="form-control" [(ngModel)]="e['district']"></div>
              <div class="col-6 form-group"><label for="e-state">State</label><input id="e-state" class="form-control" [(ngModel)]="e['state']"></div>
              <div class="col-md-6 form-group"><label for="e-board">Board registration no.</label><input id="e-board" class="form-control" [(ngModel)]="e['boardRegistrationNo']"></div>
              <div class="col-md-6 form-group"><label for="e-prev">Previous school</label><input id="e-prev" class="form-control" [(ngModel)]="e['previousSchool']"></div>
              <div class="col-md-6 form-group" *ngIf="canPrivate"><label for="e-aadhaar">Aadhaar (last 4 digits)</label><input id="e-aadhaar" class="form-control" maxlength="14" [(ngModel)]="e['aadhaarLast4']"></div>
              <div class="col-12 form-group"><label for="e-notes">Notes</label><textarea id="e-notes" class="form-control" rows="2" [(ngModel)]="e['notes']"></textarea></div>
              <div class="col-12 d-flex justify-content-end">
                <button class="btn btn-light mr-2" (click)="editing = false">Cancel</button>
                <button class="btn btn-primary" (click)="saveDetails()" [disabled]="busy || !e['name']">Save</button>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <h4 class="header-title mb-0">Parents and guardians</h4>
              <button *ngIf="canEdit" class="btn btn-sm btn-outline-primary" (click)="editGuardian()"><i class="mdi mdi-plus mr-1"></i>Add</button>
            </div>
            <div class="table-responsive"><table class="table table-sm table-centered mb-0">
              <thead><tr><th>Name</th><th>Phone</th><th>Occupation</th><th *ngIf="p.private">Income / month</th><th></th></tr></thead>
              <tbody>
                <tr *ngFor="let g of p.guardians">
                  <td><strong>{{ g.name }}</strong> <span class="text-muted small">{{ relations[g.relation] }}</span>
                    <div class="small"><span *ngIf="g.is_primary" class="badge badge-info mr-1">Main contact</span><span *ngIf="g.may_pick_up" class="text-muted">May take her home</span></div></td>
                  <td>{{ g.phone || '--' }}<div *ngIf="g.whatsapp && g.whatsapp !== g.phone" class="small text-muted">WhatsApp {{ g.whatsapp }}</div><div class="small text-muted">{{ g.email }}</div></td>
                  <td>{{ g.occupation || '--' }}</td>
                  <td *ngIf="p.private">{{ g.monthly_income != null ? '₹' + (g.monthly_income | number: '1.0-0') : '--' }}</td>
                  <td class="text-right text-nowrap">
                    <button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="editGuardian(g)">Edit</button>
                    <button *ngIf="canEdit && p.guardians.length > 1" class="btn btn-xs btn-outline-danger" (click)="deleteGuardian(g)">Remove</button>
                  </td>
                </tr>
              </tbody>
            </table></div>
          </div>
        </div>
      </div>

      <div class="col-xl-5">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Class</h4>
            <div *ngIf="canEdit" class="border rounded p-2 mb-3">
              <div class="row">
                <div class="col-12 col-sm-5 form-group mb-2"><label for="m-session">Session</label>
                  <select id="m-session" class="form-control form-control-sm" [(ngModel)]="move.sessionId" (ngModelChange)="loadSections()">
                    <option *ngFor="let x of sessions" [ngValue]="x.id">{{ x.name }}</option></select></div>
                <div class="col-8 col-sm-4 form-group mb-2"><label for="m-section">Class</label>
                  <select id="m-section" class="form-control form-control-sm" [(ngModel)]="move.sectionId">
                    <option [ngValue]="null">--</option><option *ngFor="let x of sections" [ngValue]="x.id">{{ x.label }}</option></select></div>
                <div class="col-4 col-sm-3 form-group mb-2"><label for="m-roll">Roll</label><input id="m-roll" class="form-control form-control-sm" [(ngModel)]="move.rollNo"></div>
              </div>
              <button class="btn btn-sm btn-primary" (click)="saveMove()" [disabled]="busy || !move.sectionId">Save class</button>
            </div>
            <div class="table-responsive"><table class="table table-sm mb-0">
              <thead><tr><th>Session</th><th>Class</th><th>Roll</th><th>Result</th></tr></thead>
              <tbody>
                <tr *ngIf="!p.enrollments.length"><td colspan="4" class="text-muted text-center">Not in any class yet.</td></tr>
                <tr *ngFor="let x of p.enrollments">
                  <td>{{ x.session_name }}<span *ngIf="x.session_current" class="badge badge-success ml-1">Current</span></td>
                  <td>{{ x.section }}</td><td>{{ x.roll_no || '' }}</td><td>{{ enrollmentStatuses[x.status] || x.status }}</td>
                </tr>
              </tbody>
            </table></div>
          </div>
        </div>

        <div class="card" *ngIf="att || results.length"><div class="card-body">
          <h4 class="header-title">Attendance and results</h4>
          <p *ngIf="att" class="mb-2">This session: <strong>{{ att['percent'] ?? '–' }}{{ att['percent'] != null ? '%' : '' }}</strong> attendance
            <span class="small text-muted">({{ att['PRESENT'] }} present, {{ att['ABSENT'] }} absent, {{ att['LATE'] }} late, {{ att['LEAVE'] }} leave)</span></p>
          <table class="table table-sm mb-0" *ngIf="results.length">
            <thead><tr><th>Exam</th><th class="text-right">%</th><th>Grade</th><th>Rank</th><th></th></tr></thead>
            <tbody><tr *ngFor="let r of results">
              <td>{{ r['exam_name'] }}</td><td class="text-right">{{ r['percent'] }}</td><td>{{ r['grade'] }}</td><td>{{ r['rank_in_section'] }}</td>
              <td class="text-right"><a href="" (click)="$event.preventDefault(); reportCard($any(r['exam_id']))">Report card</a></td>
            </tr></tbody>
          </table>
        </div></div>

        <app-reb-student-fees *ngIf="canFees" [studentId]="$any(s['id'])"></app-reb-student-fees>

        <div class="card" *ngIf="p.private">
          <div class="card-body">
            <h4 class="header-title">Documents</h4>
            <p class="small text-muted">Only people with “REB: private details” can see and open these.</p>
            <div *ngIf="canPrivateEdit" class="d-flex flex-wrap align-items-end border rounded p-2 mb-3">
              <div class="form-group mb-2 mr-2"><label for="d-kind">Type</label>
                <select id="d-kind" class="form-control form-control-sm" [(ngModel)]="doc.kind"><option *ngFor="let k of docKeys" [value]="k">{{ docKinds[k] }}</option></select></div>
              <div class="form-group mb-2 mr-2"><label for="d-file">File (PDF or photo, up to 5 MB)</label>
                <input id="d-file" type="file" accept="image/*,application/pdf" class="form-control-file" (change)="doc.file = fileOf($event)"></div>
              <button class="btn btn-sm btn-primary mb-2" (click)="upload()" [disabled]="busy || !doc.file">Upload</button>
            </div>
            <ul class="list-unstyled mb-0">
              <li *ngIf="!p.documents?.length" class="text-muted">No documents yet.</li>
              <li *ngFor="let d of p.documents" class="d-flex justify-content-between align-items-center border-bottom py-1">
                <span><a [href]="link(d.file_path)" target="_blank" rel="noopener">{{ docKinds[d.kind] || d.kind }}</a>
                  <span class="small text-muted"> · {{ d.file_name }} · {{ d.created_at | date: 'd MMM yyyy' }}</span></span>
                <button *ngIf="canPrivateEdit" class="btn btn-xs btn-outline-danger" (click)="deleteDoc(d.id)">Remove</button>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <!-- guardian -->
    <div *ngIf="g" class="modal fade show d-block" tabindex="-1" (click)="g = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ g.id ? 'Edit guardian' : 'Add guardian' }}</h5><button type="button" class="close" (click)="g = null" aria-label="Close">&times;</button></div>
          <div class="modal-body"><div class="row">
            <div class="col-5 form-group"><label for="g-rel">Relation</label>
              <select id="g-rel" class="form-control" [(ngModel)]="g.relation"><option *ngFor="let r of relationKeys" [value]="r">{{ relations[r] }}</option></select></div>
            <div class="col-7 form-group"><label for="g-name">Name *</label><input id="g-name" class="form-control" [(ngModel)]="g.name"></div>
            <div class="col-6 form-group"><label for="g-phone">Phone</label><input id="g-phone" type="tel" class="form-control" [(ngModel)]="g.phone"></div>
            <div class="col-6 form-group"><label for="g-wa">WhatsApp</label><input id="g-wa" type="tel" class="form-control" [(ngModel)]="g.whatsapp"></div>
            <div class="col-12 form-group"><label for="g-email">Email</label><input id="g-email" type="email" class="form-control" [(ngModel)]="g.email"></div>
            <div class="col-6 form-group"><label for="g-occ">Occupation</label><input id="g-occ" class="form-control" [(ngModel)]="g.occupation"></div>
            <div class="col-6 form-group" *ngIf="canPrivateEdit"><label for="g-inc">Monthly income (₹)</label><input id="g-inc" type="number" min="0" class="form-control" [(ngModel)]="g.monthly_income"></div>
            <div class="col-12">
              <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="g-pri" [(ngModel)]="g.is_primary"><label class="custom-control-label" for="g-pri">Main contact</label></div>
              <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="g-pick" [(ngModel)]="g.may_pick_up"><label class="custom-control-label" for="g-pick">May take her home from the hostel</label></div>
            </div>
          </div></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="g = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveGuardian()" [disabled]="busy || !g.name">Save</button>
          </div>
        </div>
      </div>
    </div>

    <!-- status -->
    <div *ngIf="st" class="modal fade show d-block" tabindex="-1" (click)="st = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Change status</h5><button type="button" class="close" (click)="st = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="s-status">Status</label>
              <select id="s-status" class="form-control" [(ngModel)]="st.status"><option *ngFor="let k of statusKeys" [value]="k">{{ statuses[k] }}</option></select></div>
            <ng-container *ngIf="st.status !== 'ACTIVE'">
              <div class="form-group"><label for="s-date">Date</label><input id="s-date" type="date" class="form-control" [(ngModel)]="st.date"></div>
              <div class="form-group mb-0"><label for="s-reason">Reason{{ st.status === 'LEFT' ? ' *' : '' }}</label><textarea id="s-reason" class="form-control" rows="2" [(ngModel)]="st.reason"></textarea></div>
            </ng-container>
            <p *ngIf="st.status === 'ACTIVE'" class="text-muted mb-0">She is studying again; check her class below after saving.</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="st = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveStatus()" [disabled]="busy || (st.status === 'LEFT' && !st.reason.trim())">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebStudentComponent implements OnChanges {
  /** From the route parameter :id. */
  @Input() id = '';

  readonly plans = FEE_PLANS;
  readonly planKeys = Object.keys(FEE_PLANS);
  readonly statuses = STATUSES;
  readonly statusKeys = Object.keys(STATUSES);
  readonly relations = RELATIONS;
  readonly relationKeys = Object.keys(RELATIONS);
  readonly categories = CATEGORIES;
  readonly docKinds = DOCUMENT_KINDS;
  readonly docKeys = Object.keys(DOCUMENT_KINDS);
  readonly enrollmentStatuses = ENROLLMENT_STATUSES;
  readonly planBadge = planBadge;
  readonly statusBadge = statusBadge;

  p: Profile | null = null;
  sessions: RebSession[] = [];
  sections: RebSectionRow[] = [];
  editing = false;
  e: Record<string, unknown> = {};
  move: { sessionId: number | null; sectionId: number | null; rollNo: string } = { sessionId: null, sectionId: null, rollNo: '' };
  doc: { kind: string; file: File | null } = { kind: 'BIRTH_CERTIFICATE', file: null };
  g: RebGuardian | null = null;
  st: { status: string; date: string; reason: string } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get s(): Record<string, unknown> | null { return this.p?.student ?? null; }
  get canEdit(): boolean { return this.auth.can('REB_STUDENTS', 'EDIT'); }
  get canPrivate(): boolean { return this.auth.can('REB_STUDENT_PRIVATE', 'EDIT'); }
  get canPrivateEdit(): boolean { return this.auth.can('REB_STUDENT_PRIVATE', 'EDIT'); }
  get canFees(): boolean { return this.auth.can('REB_FEES'); }
  att: Record<string, number | null> | null = null;
  results: Record<string, unknown>[] = [];

  private loadAcademics(): void {
    this.api.get<Record<string, number | null>>(`/admin/reb/students/${this.id}/attendance`).subscribe({ next: (r) => (this.att = r), error: () => (this.att = null) });
    this.api.get<Record<string, unknown>[]>(`/admin/reb/students/${this.id}/results`).subscribe({ next: (r) => (this.results = r), error: () => (this.results = []) });
  }

  reportCard(examId: number): void {
    this.api.download(`/admin/reb/students/${this.id}/report-card`, { examId }, 'report_card.pdf').subscribe({
      error: (e) => (this.error = errorText(e, 'Could not create the report card.'))
    });
  }

  str(v: unknown): string { return v == null ? '' : String(v); }
  link(path: string): string | null { return uploadUrl(path); }
  fileOf(ev: Event): File | null { return (ev.target as HTMLInputElement).files?.[0] ?? null; }

  ngOnChanges(): void {
    this.load();
    this.loadAcademics();
    if (!this.sessions.length) {
      this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({ next: (r) => (this.sessions = r), error: () => (this.sessions = []) });
    }
  }

  load(): void {
    this.api.get<Profile>(`/admin/reb/students/${this.id}`).subscribe({
      next: (r) => {
        this.p = r;
        const current = r.enrollments.find((x) => x.session_current) ?? r.enrollments[0];
        this.move = { sessionId: current?.session_id ?? null, sectionId: current?.section_id ?? null, rollNo: current?.roll_no ?? '' };
        if (!this.move.sessionId) this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
          next: (ss) => { this.move.sessionId = ss.find((x) => x.is_current)?.id ?? null; this.loadSections(); }
        });
        else this.loadSections();
      },
      error: (e) => (this.error = errorText(e, 'Could not load her details.'))
    });
  }

  loadSections(): void {
    if (!this.move.sessionId) { this.sections = []; return; }
    this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: this.move.sessionId }).subscribe({
      next: (r) => {
        this.sections = r;
        if (!r.some((x) => x.id === this.move.sectionId)) {
          const mine = this.p?.enrollments.find((x) => x.session_id === this.move.sessionId);
          this.move.sectionId = mine?.section_id ?? null;
          this.move.rollNo = mine?.roll_no ?? '';
        }
      },
      error: () => (this.sections = [])
    });
  }

  startEdit(): void {
    const s = this.s!;
    const v = (k: string) => (s[k] == null ? '' : s[k]);
    this.e = {
      name: v('name'), dateOfBirth: isoDate(s['date_of_birth']), admittedOn: isoDate(s['admitted_on']), feePlan: v('fee_plan'),
      socialCategory: s['social_category'] ?? null, bloodGroup: v('blood_group'), residential: !!s['residential'], address: v('address'),
      district: v('district'), state: v('state'), boardRegistrationNo: v('board_registration_no'), previousSchool: v('previous_school'),
      notes: v('notes')
    };
    if (this.canPrivate) this.e['aadhaarLast4'] = v('aadhaar_last4');
    this.editing = true;
  }

  private done(r: { message: string }): void { this.busy = false; this.message = r.message; this.load(); }
  private fail(e: unknown, text: string): void { this.busy = false; this.error = errorText(e, text); }

  saveDetails(): void {
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/reb/students/${this.id}`, this.e).subscribe({
      next: (r) => { this.editing = false; this.done(r); }, error: (e) => this.fail(e, 'Could not save.')
    });
  }

  saveMove(): void {
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/reb/students/${this.id}/enrollment`, { sectionId: this.move.sectionId, rollNo: this.move.rollNo }).subscribe({
      next: (r) => this.done(r), error: (e) => this.fail(e, 'Could not save the class.')
    });
  }

  editGuardian(g?: RebGuardian): void { this.g = g ? { ...g } : newGuardian(!this.p?.guardians.length); }

  saveGuardian(): void {
    const g = this.g;
    if (!g) return;
    this.busy = true;
    const req = g.id ? this.api.put<{ message: string }>(`/admin/reb/guardians/${g.id}`, guardianBody(g))
      : this.api.post<{ message: string }>(`/admin/reb/students/${this.id}/guardians`, guardianBody(g));
    req.subscribe({ next: (r) => { this.g = null; this.done(r); }, error: (e) => this.fail(e, 'Could not save the guardian.') });
  }

  deleteGuardian(g: RebGuardian): void {
    if (!confirm(`Remove ${g.name}?`)) return;
    this.api.delete<{ message: string }>(`/admin/reb/guardians/${g.id}`).subscribe({ next: (r) => this.done(r), error: (e) => this.fail(e, 'Could not remove.') });
  }

  upload(): void {
    if (!this.doc.file) return;
    const f = new FormData();
    f.append('kind', this.doc.kind);
    f.append('file', this.doc.file);
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/reb/students/${this.id}/documents`, f).subscribe({
      next: (r) => { this.doc = { kind: this.doc.kind, file: null }; this.done(r); }, error: (e) => this.fail(e, 'Could not upload.')
    });
  }

  deleteDoc(id: number): void {
    if (!confirm('Remove this document?')) return;
    this.api.delete<{ message: string }>(`/admin/reb/documents/${id}`).subscribe({ next: (r) => this.done(r), error: (e) => this.fail(e, 'Could not remove.') });
  }

  openStatus(): void {
    const s = this.s!;
    this.st = { status: s['status'] === 'ACTIVE' ? 'LEFT' : 'ACTIVE', date: isoDate(new Date()), reason: '' };
  }

  saveStatus(): void {
    if (!this.st) return;
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/reb/students/${this.id}/status`, this.st).subscribe({
      next: (r) => { this.st = null; this.done(r); }, error: (e) => this.fail(e, 'Could not change the status.')
    });
  }

  certificate(type: 'TC' | 'BONAFIDE'): void {
    this.api.download(`/admin/reb/students/${this.id}/certificate`, { type }, `${type}.pdf`).subscribe({
      error: (e) => (this.error = errorText(e, 'Could not create the certificate.'))
    });
  }
}
