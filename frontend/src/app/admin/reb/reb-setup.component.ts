import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { ASSESSMENTS, RebClass, RebSectionRow, RebSession, RebStaff, RebSubject, TRACKS } from './reb.models';

type Tab = 'sessions' | 'classes' | 'sections' | 'subjects' | 'curriculum' | 'settings';

interface CurriculumRow { subjectId: number; name: string; track: string; maxMarks: number; passMarks: number }

/**
 * Rahbar-e-Banaat > REB Setup: sessions (each with its own dates), class levels, the sections of a session,
 * subjects of the Madrasa and School tracks, the subjects of each class, and school settings.
 */
@Component({
  selector: 'app-reb-setup',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">REB Setup</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div *ngIf="!sessions.length && !loading" class="alert alert-info">
      Start by adding the current session (its name and dates), then the classes, sections and subjects.
    </div>

    <ul class="nav nav-tabs mb-3 flex-nowrap overflow-auto" role="tablist">
      <li class="nav-item" *ngFor="let t of tabs"><a class="nav-link text-nowrap" href="" [class.active]="tab === t.id" (click)="$event.preventDefault(); go(t.id)">{{ t.label }}</a></li>
    </ul>

    <!-- sessions -->
    <div *ngIf="tab === 'sessions'" class="card"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <p class="text-muted mb-2 mr-3">Each session has its own dates, sections, subjects and girls. The current session is the one every screen opens with.</p>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm" (click)="editSession()"><i class="mdi mdi-plus mr-1"></i>Add session</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Session</th><th>From</th><th>To</th><th>Status</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!sessions.length"><td colspan="5" class="text-center text-muted">No sessions yet.</td></tr>
          <tr *ngFor="let s of sessions">
            <td><strong>{{ s.name }}</strong> <span *ngIf="s.is_current" class="badge badge-success ml-1">Current</span></td>
            <td>{{ s.start_date | date: 'd MMM yyyy' }}</td><td>{{ s.end_date | date: 'd MMM yyyy' }}</td>
            <td>{{ s.status === 'OPEN' ? 'Open' : 'Closed' }}</td>
            <td class="text-right text-nowrap">
              <button *ngIf="canEdit && !s.is_current" class="btn btn-xs btn-outline-success mr-1" (click)="makeCurrent(s)">Make current</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-primary" (click)="editSession(s)">Edit</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- classes -->
    <div *ngIf="tab === 'classes'" class="card"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <p class="text-muted mb-2 mr-3">Class levels stay the same every year. The order decides promotion: girls move to the next class, or to the one you choose.</p>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm" (click)="editClass()"><i class="mdi mdi-plus mr-1"></i>Add class</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Order</th><th>Class</th><th>Track</th><th>Promoted to</th><th>Board exam</th><th>Active</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!classes.length"><td colspan="7" class="text-center text-muted">No classes yet.</td></tr>
          <tr *ngFor="let c of classes">
            <td>{{ c.level_order }}</td><td><strong>{{ c.name }}</strong></td><td>{{ tracks[c.track] }}</td>
            <td>{{ className(c.next_class_id) || (c.next_class_id ? '' : 'Next by order') }}</td>
            <td>{{ c.board_exam ? 'Yes' : '' }}</td><td>{{ c.active ? 'Yes' : 'No' }}</td>
            <td class="text-right text-nowrap">
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="editClass(c)">Edit</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="remove('classes', c.id, c.name)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- sections -->
    <div *ngIf="tab === 'sections'" class="card"><div class="card-body">
      <div class="d-flex justify-content-between align-items-end flex-wrap mb-3">
        <div class="form-group mb-2 mr-3">
          <label for="sec-session">Session</label>
          <select id="sec-session" class="form-control" [(ngModel)]="sessionId" (ngModelChange)="loadSections()">
            <option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}{{ s.is_current ? ' (current)' : '' }}</option>
          </select>
        </div>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm mb-2" (click)="editSection()" [disabled]="!sessionId || !classes.length"><i class="mdi mdi-plus mr-1"></i>Add section</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Class</th><th>Class teacher</th><th>Room</th><th>Girls</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!sections.length"><td colspan="5" class="text-center text-muted">No sections in this session yet.</td></tr>
          <tr *ngFor="let s of sections">
            <td><strong>{{ s.label }}</strong> <span class="small text-muted">{{ tracks[s.track] }}</span></td>
            <td>{{ s.class_teacher_name || '--' }}</td><td>{{ s.room || '--' }}</td>
            <td>{{ s.girls }}<span *ngIf="s.capacity" class="text-muted"> / {{ s.capacity }}</span></td>
            <td class="text-right text-nowrap">
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="editSection(s)">Edit</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="remove('sections', s.id, s.label)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- subjects -->
    <div *ngIf="tab === 'subjects'" class="card"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <p class="text-muted mb-2 mr-3">Subjects of the Madrasa track (Quran, Hifz, Arabic, Urdu, Diniyat, ...) and the School track (Bihar Board syllabus).</p>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm" (click)="editSubject()"><i class="mdi mdi-plus mr-1"></i>Add subject</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Subject</th><th>Code</th><th>Track</th><th>Assessment</th><th>Active</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!subjects.length"><td colspan="6" class="text-center text-muted">No subjects yet.</td></tr>
          <tr *ngFor="let s of subjects">
            <td><strong>{{ s.name }}</strong></td><td>{{ s.code || '' }}</td><td>{{ tracks[s.track] }}</td>
            <td>{{ assessments[s.assessment] }}</td><td>{{ s.active ? 'Yes' : 'No' }}</td>
            <td class="text-right text-nowrap">
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-primary mr-1" (click)="editSubject(s)">Edit</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="remove('subjects', s.id, s.name)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- curriculum -->
    <div *ngIf="tab === 'curriculum'" class="card"><div class="card-body">
      <p class="text-muted">The subjects each class studies in a session, with maximum and pass marks for exams.</p>
      <div class="d-flex flex-wrap align-items-end mb-3">
        <div class="form-group mb-2 mr-3">
          <label for="cur-session">Session</label>
          <select id="cur-session" class="form-control" [(ngModel)]="sessionId" (ngModelChange)="loadCurriculum()">
            <option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="form-group mb-2 mr-3">
          <label for="cur-class">Class</label>
          <select id="cur-class" class="form-control" [(ngModel)]="classId" (ngModelChange)="loadCurriculum()">
            <option [ngValue]="null">-- Choose --</option>
            <option *ngFor="let c of classes" [ngValue]="c.id">{{ c.name }}</option>
          </select>
        </div>
        <button *ngIf="canEdit && sessions.length > 1" class="btn btn-outline-secondary btn-sm mb-2" (click)="copy = { from: null }">Copy from another session</button>
      </div>
      <ng-container *ngIf="classId">
        <div class="table-responsive"><table class="table table-sm table-centered mb-2">
          <thead><tr><th>Subject</th><th>Track</th><th style="width:130px">Max marks</th><th style="width:130px">Pass marks</th><th></th></tr></thead>
          <tbody>
            <tr *ngIf="!curriculum.length"><td colspan="5" class="text-center text-muted">No subjects for this class yet.</td></tr>
            <tr *ngFor="let r of curriculum; let i = index">
              <td><strong>{{ r.name }}</strong></td><td>{{ tracks[r.track] }}</td>
              <td><input type="number" min="1" class="form-control form-control-sm" [(ngModel)]="r.maxMarks" [disabled]="!canEdit" [attr.aria-label]="'Max marks ' + r.name"></td>
              <td><input type="number" min="0" class="form-control form-control-sm" [(ngModel)]="r.passMarks" [disabled]="!canEdit" [attr.aria-label]="'Pass marks ' + r.name"></td>
              <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-outline-danger" (click)="curriculum.splice(i, 1)" aria-label="Remove">&times;</button></td>
            </tr>
          </tbody>
        </table></div>
        <div *ngIf="canEdit" class="d-flex flex-wrap align-items-center">
          <select class="form-control form-control-sm mr-2 mb-2" style="max-width: 260px" [(ngModel)]="addSubjectId" aria-label="Add a subject">
            <option [ngValue]="null">Add a subject…</option>
            <option *ngFor="let s of availableSubjects" [ngValue]="s.id">{{ s.name }} ({{ tracks[s.track] }})</option>
          </select>
          <button class="btn btn-sm btn-outline-primary mr-auto mb-2" (click)="addSubject()" [disabled]="!addSubjectId">Add</button>
          <button class="btn btn-primary mb-2" (click)="saveCurriculum()" [disabled]="busy">Save subjects of {{ className(classId) }}</button>
        </div>
      </ng-container>
    </div></div>

    <!-- settings -->
    <div *ngIf="tab === 'settings'" class="card"><div class="card-body">
      <p class="text-muted">Printed on certificates and used for admission numbers (PREFIX/YEAR/0001).</p>
      <div class="row">
        <div class="col-md-6 form-group" *ngFor="let s of settings">
          <label [for]="'set-' + s.key">{{ s.label }}</label>
          <input [id]="'set-' + s.key" class="form-control" [(ngModel)]="s.value" [disabled]="!canEdit">
        </div>
      </div>
      <button *ngIf="canEdit" class="btn btn-primary" (click)="saveSettings()" [disabled]="busy">Save settings</button>
    </div></div>

    <!-- modal -->
    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ form.title }}</h5><button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <ng-container [ngSwitch]="form.kind">
              <ng-container *ngSwitchCase="'sessions'">
                <div class="form-group"><label for="f-name">Name *</label><input id="f-name" class="form-control" [(ngModel)]="form.d['name']" placeholder="e.g. 2026 or 2026-27"></div>
                <div class="row">
                  <div class="col-6 form-group"><label for="f-start">Starts *</label><input id="f-start" type="date" class="form-control" [(ngModel)]="form.d['startDate']"></div>
                  <div class="col-6 form-group"><label for="f-end">Ends *</label><input id="f-end" type="date" class="form-control" [(ngModel)]="form.d['endDate']"></div>
                </div>
                <div class="form-group" *ngIf="form.id"><label for="f-status">Status</label>
                  <select id="f-status" class="form-control" [(ngModel)]="form.d['status']"><option value="OPEN">Open</option><option value="CLOSED">Closed</option></select></div>
              </ng-container>
              <ng-container *ngSwitchCase="'classes'">
                <div class="form-group"><label for="f-cname">Name *</label><input id="f-cname" class="form-control" [(ngModel)]="form.d['name']" placeholder="e.g. Class 6, Hifz, Alimiyat 1"></div>
                <div class="row">
                  <div class="col-6 form-group"><label for="f-track">Track</label>
                    <select id="f-track" class="form-control" [(ngModel)]="form.d['track']"><option *ngFor="let t of trackKeys" [value]="t">{{ tracks[t] }}</option></select></div>
                  <div class="col-6 form-group"><label for="f-order">Order</label><input id="f-order" type="number" class="form-control" [(ngModel)]="form.d['levelOrder']"></div>
                </div>
                <div class="form-group"><label for="f-next">Promoted to</label>
                  <select id="f-next" class="form-control" [(ngModel)]="form.d['nextClassId']">
                    <option [ngValue]="null">The next class by order</option>
                    <option *ngFor="let c of otherClasses(form.id)" [ngValue]="c.id">{{ c.name }}</option>
                  </select></div>
                <div class="custom-control custom-checkbox mb-2"><input type="checkbox" class="custom-control-input" id="f-board" [(ngModel)]="form.d['boardExam']"><label class="custom-control-label" for="f-board">Bihar Board exam class (e.g. Class 10)</label></div>
                <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="f-active" [(ngModel)]="form.d['active']"><label class="custom-control-label" for="f-active">Active</label></div>
              </ng-container>
              <ng-container *ngSwitchCase="'sections'">
                <div class="form-group" *ngIf="!form.id"><label for="f-class">Class *</label>
                  <select id="f-class" class="form-control" [(ngModel)]="form.d['classId']"><option [ngValue]="null">-- Choose --</option>
                    <option *ngFor="let c of activeClasses" [ngValue]="c.id">{{ c.name }}</option></select></div>
                <div class="row">
                  <div class="col-6 form-group"><label for="f-sname">Section</label><input id="f-sname" class="form-control" [(ngModel)]="form.d['name']" placeholder="A"></div>
                  <div class="col-6 form-group"><label for="f-cap">Capacity</label><input id="f-cap" type="number" min="1" class="form-control" [(ngModel)]="form.d['capacity']"></div>
                </div>
                <div class="form-group"><label for="f-teacher">Class teacher</label>
                  <select id="f-teacher" class="form-control" [(ngModel)]="form.d['classTeacherStaffId']"><option [ngValue]="null">-- None --</option>
                    <option *ngFor="let t of teachers" [ngValue]="t.id">{{ t.name }}</option></select></div>
                <div class="form-group mb-0"><label for="f-room">Room</label><input id="f-room" class="form-control" [(ngModel)]="form.d['room']"></div>
              </ng-container>
              <ng-container *ngSwitchCase="'subjects'">
                <div class="form-group"><label for="f-subname">Name *</label><input id="f-subname" class="form-control" [(ngModel)]="form.d['name']"></div>
                <div class="row">
                  <div class="col-6 form-group"><label for="f-code">Code</label><input id="f-code" class="form-control" [(ngModel)]="form.d['code']"></div>
                  <div class="col-6 form-group"><label for="f-strack">Track</label>
                    <select id="f-strack" class="form-control" [(ngModel)]="form.d['track']"><option *ngFor="let t of trackKeys" [value]="t">{{ tracks[t] }}</option></select></div>
                </div>
                <div class="form-group"><label for="f-assess">Assessment</label>
                  <select id="f-assess" class="form-control" [(ngModel)]="form.d['assessment']"><option *ngFor="let a of assessmentKeys" [value]="a">{{ assessments[a] }}</option></select></div>
                <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="f-sactive" [(ngModel)]="form.d['active']"><label class="custom-control-label" for="f-sactive">Active</label></div>
              </ng-container>
            </ng-container>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveForm()" [disabled]="busy">Save</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="copy" class="modal fade show d-block" tabindex="-1" (click)="copy = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Copy class subjects</h5><button type="button" class="close" (click)="copy = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <p class="text-muted">Copies the subjects of every class into {{ sessionName(sessionId) }}. Classes that already have subjects there are left as they are.</p>
            <label for="copy-from">Copy from</label>
            <select id="copy-from" class="form-control" [(ngModel)]="copy.from">
              <option [ngValue]="null">-- Choose --</option>
              <option *ngFor="let s of sessions" [ngValue]="s.id" [disabled]="s.id === sessionId">{{ s.name }}</option>
            </select>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="copy = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="copyCurriculum()" [disabled]="busy || !copy.from">Copy</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebSetupComponent implements OnInit {
  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'sessions', label: 'Sessions' }, { id: 'classes', label: 'Classes' }, { id: 'sections', label: 'Sections' },
    { id: 'subjects', label: 'Subjects' }, { id: 'curriculum', label: 'Class subjects' }, { id: 'settings', label: 'Settings' }
  ];
  readonly tracks = TRACKS;
  readonly trackKeys = Object.keys(TRACKS);
  readonly assessments = ASSESSMENTS;
  readonly assessmentKeys = Object.keys(ASSESSMENTS);

  tab: Tab = 'sessions';
  sessions: RebSession[] = [];
  classes: RebClass[] = [];
  sections: RebSectionRow[] = [];
  subjects: RebSubject[] = [];
  teachers: RebStaff[] = [];
  settings: { key: string; label: string; value: string }[] = [];
  curriculum: CurriculumRow[] = [];
  sessionId: number | null = null;
  classId: number | null = null;
  addSubjectId: number | null = null;
  form: { kind: 'sessions' | 'classes' | 'sections' | 'subjects'; id: number | null; title: string; d: Record<string, unknown> } | null = null;
  copy: { from: number | null } | null = null;
  loading = true;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_SETUP', 'EDIT'); }
  get activeClasses(): RebClass[] { return this.classes.filter((c) => c.active); }
  get availableSubjects(): RebSubject[] { return this.subjects.filter((s) => s.active && !this.curriculum.some((r) => r.subjectId === s.id)); }

  ngOnInit(): void {
    this.loadSessions();
    this.loadClasses();
    this.loadSubjects();
    this.api.get<RebStaff[]>('/admin/reb/staff').subscribe({ next: (r) => (this.teachers = r.filter((s) => s.status === 'ACTIVE')), error: () => (this.teachers = []) });
  }

  go(tab: Tab): void {
    this.tab = tab;
    if (tab === 'sections') this.loadSections();
    if (tab === 'curriculum') this.loadCurriculum();
    if (tab === 'settings' && !this.settings.length) {
      this.api.get<{ key: string; label: string; value: string }[]>('/admin/reb/settings').subscribe({
        next: (r) => (this.settings = r), error: (e) => (this.error = errorText(e, 'Could not load the settings.'))
      });
    }
  }

  loadSessions(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        this.loading = false;
        this.sessions = r;
        if (!this.sessionId || !r.some((s) => s.id === this.sessionId)) this.sessionId = (r.find((s) => s.is_current) ?? r[0])?.id ?? null;
      },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the sessions.'); }
    });
  }

  loadClasses(): void {
    this.api.get<RebClass[]>('/admin/reb/classes').subscribe({ next: (r) => (this.classes = r), error: (e) => (this.error = errorText(e, 'Could not load the classes.')) });
  }

  loadSubjects(): void {
    this.api.get<RebSubject[]>('/admin/reb/subjects').subscribe({ next: (r) => (this.subjects = r), error: (e) => (this.error = errorText(e, 'Could not load the subjects.')) });
  }

  loadSections(): void {
    if (!this.sessionId) { this.sections = []; return; }
    this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: this.sessionId }).subscribe({
      next: (r) => (this.sections = r), error: (e) => (this.error = errorText(e, 'Could not load the sections.'))
    });
  }

  loadCurriculum(): void {
    this.curriculum = [];
    if (!this.sessionId || !this.classId) return;
    this.api.get<{ subject_id: number; subject_name: string; track: string; max_marks: number; pass_marks: number }[]>(
      '/admin/reb/class-subjects', { sessionId: this.sessionId, classId: this.classId }).subscribe({
      next: (r) => (this.curriculum = r.map((x) => ({ subjectId: x.subject_id, name: x.subject_name, track: x.track, maxMarks: x.max_marks, passMarks: x.pass_marks }))),
      error: (e) => (this.error = errorText(e, 'Could not load the subjects of the class.'))
    });
  }

  className(id: number | null): string { return this.classes.find((c) => c.id === id)?.name ?? ''; }
  sessionName(id: number | null): string { return this.sessions.find((s) => s.id === id)?.name ?? ''; }
  otherClasses(id: number | null): RebClass[] { return this.classes.filter((c) => c.id !== id); }

  editSession(s?: RebSession): void {
    this.form = { kind: 'sessions', id: s?.id ?? null, title: s ? 'Edit session' : 'Add session',
      d: { name: s?.name ?? '', startDate: s?.start_date ?? '', endDate: s?.end_date ?? '', status: s?.status ?? 'OPEN' } };
  }

  editClass(c?: RebClass): void {
    const next = this.classes.reduce((m, x) => Math.max(m, x.level_order), 0) + 1;
    this.form = { kind: 'classes', id: c?.id ?? null, title: c ? 'Edit class' : 'Add class',
      d: { name: c?.name ?? '', track: c?.track ?? 'BOTH', levelOrder: c?.level_order ?? next, nextClassId: c?.next_class_id ?? null,
        boardExam: c?.board_exam ?? false, active: c?.active ?? true } };
  }

  editSection(s?: RebSectionRow): void {
    this.form = { kind: 'sections', id: s?.id ?? null, title: s ? 'Edit ' + s.label : 'Add section',
      d: { sessionId: this.sessionId, classId: s?.class_id ?? null, name: s?.name ?? 'A', capacity: s?.capacity ?? null,
        classTeacherStaffId: s?.class_teacher_staff_id ?? null, room: s?.room ?? '' } };
  }

  editSubject(s?: RebSubject): void {
    this.form = { kind: 'subjects', id: s?.id ?? null, title: s ? 'Edit subject' : 'Add subject',
      d: { name: s?.name ?? '', code: s?.code ?? '', track: s?.track ?? 'SCHOOL', assessment: s?.assessment ?? 'WRITTEN', active: s?.active ?? true } };
  }

  saveForm(): void {
    const f = this.form;
    if (!f) return;
    this.busy = true;
    const path = `/admin/reb/${f.kind}`;
    const req = f.id ? this.api.put<{ message: string }>(`${path}/${f.id}`, f.d) : this.api.post<{ message: string }>(path, f.d);
    req.subscribe({
      next: (r) => { this.busy = false; this.form = null; this.message = r.message; this.reload(f.kind); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }

  private reload(kind: string): void {
    if (kind === 'sessions') this.loadSessions();
    if (kind === 'classes') this.loadClasses();
    if (kind === 'sections') this.loadSections();
    if (kind === 'subjects') this.loadSubjects();
  }

  remove(kind: 'classes' | 'sections' | 'subjects', id: number, name: string): void {
    if (!confirm(`Delete ${name}?`)) return;
    this.api.delete<{ message: string }>(`/admin/reb/${kind}/${id}`).subscribe({
      next: (r) => { this.message = r.message; this.reload(kind); },
      error: (e) => (this.error = errorText(e, 'Could not delete.'))
    });
  }

  makeCurrent(s: RebSession): void {
    this.api.post<{ message: string }>(`/admin/reb/sessions/${s.id}/current`, {}).subscribe({
      next: (r) => { this.message = `${s.name}: ${r.message}`; this.loadSessions(); },
      error: (e) => (this.error = errorText(e, 'Could not change the current session.'))
    });
  }

  addSubject(): void {
    const s = this.subjects.find((x) => x.id === this.addSubjectId);
    if (!s) return;
    this.curriculum.push({ subjectId: s.id, name: s.name, track: s.track, maxMarks: 100, passMarks: 33 });
    this.addSubjectId = null;
  }

  saveCurriculum(): void {
    this.busy = true;
    this.api.put<{ message: string }>('/admin/reb/class-subjects', {
      sessionId: this.sessionId, classId: this.classId,
      subjects: this.curriculum.map((r) => ({ subjectId: r.subjectId, maxMarks: r.maxMarks, passMarks: r.passMarks }))
    }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.loadCurriculum(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the subjects.'); }
    });
  }

  copyCurriculum(): void {
    if (!this.copy?.from) return;
    this.busy = true;
    this.api.post<{ message: string }>('/admin/reb/class-subjects/copy', { fromSessionId: this.copy.from, toSessionId: this.sessionId }).subscribe({
      next: (r) => { this.busy = false; this.copy = null; this.message = r.message; this.loadCurriculum(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not copy.'); }
    });
  }

  saveSettings(): void {
    this.busy = true;
    const body: Record<string, string> = {};
    this.settings.forEach((s) => (body[s.key] = s.value));
    this.api.put<{ message: string }>('/admin/reb/settings', body).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the settings.'); }
    });
  }
}
