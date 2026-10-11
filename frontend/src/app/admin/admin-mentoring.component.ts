import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface Pair {
  id: number; student_id: number; student_ref_id: string | null; student_name: string; chapter: string | null;
  mentor_kind: 'VOLUNTEER' | 'ALUMNI'; mentor_name: string; mentor_email: string | null; mentor_phone: string | null;
  help: string; help_label: string; focus: string | null; status: 'ACTIVE' | 'ENDED'; started_on: string; ended_on: string | null; notes: string | null;
}
interface Student {
  id: number; user_id: string | null; name: string; chapter: string | null; course: string | null; institution: string | null;
  study_status: string; mentors_now?: number; fields?: string[];
}
interface Mentor {
  key: string; kind: 'VOLUNTEER' | 'ALUMNI'; volunteer_id?: number; alumni_user_id?: number; user_id?: string; name: string;
  email: string | null; phone: string | null; city: string | null; about: string | null; offers: string[];
  hours_per_month: number | null; status: string | null; fields: string[]; mentees_now: number;
  shared_fields?: string[]; same_chapter?: boolean; score?: number;
}
interface Overview { rows: Pair[]; active: number; waiting: number; mentors: number; help: Record<string, string> }

/**
 * Students > Mentoring: pair scholarship students with volunteers who offered to mentor or tutor and with graduates
 * who agreed to be contacted. "Find a mentor" ranks everyone by the fields they share with the student's course,
 * the same chapter, and how many students they already have.
 */
@Component({
  selector: 'app-admin-mentoring',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Mentoring</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'PAIRS'" (click)="$event.preventDefault(); tab = 'PAIRS'">Pairs ({{ data?.active ?? 0 }} active)</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'STUDENTS'" (click)="$event.preventDefault(); openStudents()">Students ({{ data?.waiting ?? 0 }} without a mentor)</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'MENTORS'" (click)="$event.preventDefault(); openMentors()">Mentors ({{ data?.mentors ?? 0 }})</a></li>
    </ul>

    <div class="card" *ngIf="tab === 'PAIRS'"><div class="card-body">
      <div class="d-flex flex-wrap align-items-center mb-2">
        <select class="form-control form-control-sm mr-2 mb-1" style="width: auto" [(ngModel)]="pairStatus" aria-label="Status">
          <option value="ACTIVE">Active</option><option value="ENDED">Ended</option><option value="">All</option></select>
        <input class="form-control form-control-sm mb-1" style="max-width: 260px" placeholder="Search student or mentor" [(ngModel)]="pairQ" aria-label="Search">
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Student</th><th>Mentor</th><th>Help</th><th>Since</th><th>Notes</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!pairs.length"><td colspan="6" class="text-center text-muted">No pairs yet. Open the Students tab and use "Find a mentor".</td></tr>
          <tr *ngFor="let p of pairs">
            <td><strong>{{ p.student_name }}</strong><div class="small text-muted">{{ p.student_ref_id }}{{ p.chapter ? ' · ' + p.chapter : '' }}</div></td>
            <td>{{ p.mentor_name }} <span class="badge" [ngClass]="p.mentor_kind === 'ALUMNI' ? 'badge-soft-success' : 'badge-soft-info'">{{ p.mentor_kind === 'ALUMNI' ? 'Graduate' : 'Volunteer' }}</span>
              <div class="small text-muted">{{ p.mentor_email }}{{ p.mentor_email && p.mentor_phone ? ' · ' : '' }}{{ p.mentor_phone }}</div></td>
            <td>{{ p.help_label }}<div class="small text-muted" *ngIf="p.focus">{{ p.focus }}</div></td>
            <td>{{ p.started_on | date: 'd MMM yyyy' }}<div class="small text-muted" *ngIf="p.ended_on">ended {{ p.ended_on | date: 'd MMM yyyy' }}</div></td>
            <td class="small"><div class="text-truncate" style="max-width: 240px">{{ p.notes }}</div></td>
            <td class="text-right text-nowrap" *ngIf="canEdit">
              <button class="btn btn-xs btn-outline-secondary mr-1" (click)="edit = { pair: p, focus: p.focus || '', notes: p.notes || '' }">Notes</button>
              <button *ngIf="p.status === 'ACTIVE'" class="btn btn-xs btn-outline-danger" (click)="setStatus(p, 'ENDED')">End</button>
              <button *ngIf="p.status === 'ENDED'" class="btn btn-xs btn-outline-primary" (click)="setStatus(p, 'ACTIVE')">Restart</button></td>
            <td *ngIf="!canEdit"></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div class="card" *ngIf="tab === 'STUDENTS'"><div class="card-body">
      <div class="d-flex flex-wrap align-items-center mb-2">
        <input class="form-control form-control-sm mr-2 mb-1" style="max-width: 260px" placeholder="Search name, ID, course" [(ngModel)]="studentQ" aria-label="Search">
        <div class="custom-control custom-checkbox mb-1"><input type="checkbox" class="custom-control-input" id="only-waiting" [(ngModel)]="onlyWaiting">
          <label class="custom-control-label" for="only-waiting">Only students without a mentor</label></div>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Student</th><th>Course</th><th>Mentors now</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!shownStudents.length"><td colspan="4" class="text-center text-muted">Nobody here.</td></tr>
          <tr *ngFor="let s of shownStudents">
            <td><strong>{{ s.name }}</strong><div class="small text-muted">{{ s.user_id }}{{ s.chapter ? ' · ' + s.chapter : '' }}{{ s.study_status !== 'Studying' ? ' · ' + s.study_status : '' }}</div></td>
            <td>{{ s.course || '--' }}<div class="small text-muted">{{ s.institution }}</div></td>
            <td>{{ s.mentors_now }}</td>
            <td class="text-right"><button class="btn btn-xs btn-outline-primary" (click)="suggest(s)">Find a mentor</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div class="card" *ngIf="tab === 'MENTORS'"><div class="card-body">
      <p class="text-muted small">Volunteers who offered to mentor or tutor on the website (sign-ups not declined, see Volunteers) and graduates who agreed to be contacted (see Alumni).</p>
      <input class="form-control form-control-sm mb-2" style="max-width: 260px" placeholder="Search name or field" [(ngModel)]="mentorQ" aria-label="Search">
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Mentor</th><th>About</th><th>Fields</th><th>Offers</th><th>Students now</th></tr></thead>
        <tbody>
          <tr *ngIf="!shownMentors.length"><td colspan="5" class="text-center text-muted">Nobody here.</td></tr>
          <tr *ngFor="let m of shownMentors">
            <td><strong>{{ m.name }}</strong> <span class="badge" [ngClass]="m.kind === 'ALUMNI' ? 'badge-soft-success' : 'badge-soft-info'">{{ m.kind === 'ALUMNI' ? 'Graduate' : 'Volunteer' }}</span>
              <div class="small text-muted">{{ m.email }}{{ m.email && m.phone ? ' · ' : '' }}{{ m.phone }}{{ m.city ? ' · ' + m.city : '' }}</div></td>
            <td class="small"><div style="max-width: 280px">{{ m.about }}</div></td>
            <td><span *ngFor="let f of m.fields" class="badge badge-light mr-1">{{ f }}</span></td>
            <td>{{ offersText(m) }}<div class="small text-muted" *ngIf="m.hours_per_month">{{ m.hours_per_month }} h / month</div></td>
            <td>{{ m.mentees_now }}</td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="found" class="modal fade show d-block" tabindex="-1" (click)="found = null">
      <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">A mentor for {{ found.student.name }}</h5><button type="button" class="close" (click)="found = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <p class="small text-muted">{{ found.student.course || 'No course on record' }}{{ found.student.institution ? ', ' + found.student.institution : '' }}
              <ng-container *ngIf="found.student.fields?.length"> · fields: <strong>{{ found.student.fields!.join(', ') }}</strong></ng-container>.
              Best matches first: shared fields, then the same chapter, then fewer students already.</p>
            <p *ngIf="!found.matches.length" class="text-muted mb-0">Nobody available. Volunteers who offer to mentor on the website, and graduates who agree to be contacted, show here.</p>
            <div *ngFor="let m of found.matches" class="border-bottom py-2 d-flex flex-wrap justify-content-between align-items-start">
              <div class="mr-2" style="flex: 1 1 320px">
                <strong>{{ m.name }}</strong> <span class="badge" [ngClass]="m.kind === 'ALUMNI' ? 'badge-soft-success' : 'badge-soft-info'">{{ m.kind === 'ALUMNI' ? 'Graduate' : 'Volunteer' }}</span>
                <span *ngFor="let f of m.shared_fields" class="badge badge-success ml-1">{{ f }}</span>
                <span *ngIf="m.same_chapter" class="badge badge-soft-secondary ml-1">same chapter</span>
                <div class="small text-muted">{{ m.about }}</div>
                <div class="small text-muted">{{ m.mentees_now }} student{{ m.mentees_now === 1 ? '' : 's' }} now{{ m.hours_per_month ? ' · ' + m.hours_per_month + ' h / month' : '' }}{{ m.status ? ' · ' + m.status : '' }}</div>
              </div>
              <div *ngIf="canEdit" class="text-nowrap">
                <button *ngFor="let h of m.offers" class="btn btn-xs btn-outline-primary ml-1" (click)="startPair(m, h)">Pair as {{ data?.help?.[h] ?? h }}</button></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="pairing" class="modal fade show d-block" tabindex="-1" style="z-index: 1060" (click)="pairing = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ pairing.mentor.name }} → {{ pairing.student.name }}</h5><button type="button" class="close" (click)="pairing = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <p class="small text-muted">As {{ (data?.help?.[pairing.help] ?? pairing.help).toLowerCase() }}. Introduce them to each other once saved.</p>
            <div class="form-group"><label for="p-focus">Focus</label><input id="p-focus" class="form-control" maxlength="200" [(ngModel)]="pairing.focus" placeholder="e.g. Career in software, or Physics for Class 12"></div>
            <div class="form-group"><label for="p-start">Starting</label><input id="p-start" type="date" class="form-control" [(ngModel)]="pairing.startedOn"></div>
            <div class="form-group mb-0"><label for="p-notes">Notes</label><textarea id="p-notes" class="form-control" rows="2" maxlength="1000" [(ngModel)]="pairing.notes"></textarea></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="pairing = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="savePair()" [disabled]="busy">Pair</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="edit" class="modal fade show d-block" tabindex="-1" (click)="edit = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ edit.pair.mentor_name }} → {{ edit.pair.student_name }}</h5><button type="button" class="close" (click)="edit = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="e-focus">Focus</label><input id="e-focus" class="form-control" maxlength="200" [(ngModel)]="edit.focus"></div>
            <div class="form-group mb-0"><label for="e-notes">Notes</label><textarea id="e-notes" class="form-control" rows="4" maxlength="1000" [(ngModel)]="edit.notes"
              placeholder="e.g. Met twice in May; helping with internship applications"></textarea></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="edit = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveEdit()" [disabled]="busy">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminMentoringComponent implements OnInit {
  tab: 'PAIRS' | 'STUDENTS' | 'MENTORS' = 'PAIRS';
  data: Overview | null = null;
  students: Student[] | null = null;
  mentors: Mentor[] | null = null;
  pairStatus = 'ACTIVE';
  pairQ = '';
  studentQ = '';
  onlyWaiting = true;
  mentorQ = '';
  found: { student: Student; matches: Mentor[] } | null = null;
  pairing: { student: Student; mentor: Mentor; help: string; focus: string; startedOn: string; notes: string } | null = null;
  edit: { pair: Pair; focus: string; notes: string } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('MENTORING', 'EDIT'); }

  get pairs(): Pair[] {
    const q = this.pairQ.trim().toLowerCase();
    return (this.data?.rows ?? []).filter((p) => (!this.pairStatus || p.status === this.pairStatus)
      && (!q || [p.student_name, p.student_ref_id, p.mentor_name, p.focus].some((v) => (v || '').toLowerCase().includes(q))));
  }

  get shownStudents(): Student[] {
    const q = this.studentQ.trim().toLowerCase();
    return (this.students ?? []).filter((s) => (!this.onlyWaiting || !s.mentors_now)
      && (!q || [s.name, s.user_id, s.course, s.institution, s.chapter].some((v) => (v || '').toLowerCase().includes(q))));
  }

  get shownMentors(): Mentor[] {
    const q = this.mentorQ.trim().toLowerCase();
    return (this.mentors ?? []).filter((m) => !q || [m.name, m.about, m.city, ...m.fields].some((v) => (v || '').toLowerCase().includes(q)));
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Overview>('/admin/mentoring').subscribe({ next: (d) => (this.data = d), error: (e) => (this.error = errorText(e, 'Could not load mentoring.')) });
    if (this.students) this.loadStudents();
    if (this.mentors) this.loadMentors();
  }

  openStudents(): void { this.tab = 'STUDENTS'; if (!this.students) this.loadStudents(); }
  openMentors(): void { this.tab = 'MENTORS'; if (!this.mentors) this.loadMentors(); }

  private loadStudents(): void {
    this.api.get<Student[]>('/admin/mentoring/students').subscribe({ next: (r) => (this.students = r), error: (e) => (this.error = errorText(e, 'Could not load the students.')) });
  }

  private loadMentors(): void {
    this.api.get<Mentor[]>('/admin/mentoring/mentors').subscribe({ next: (r) => (this.mentors = r), error: (e) => (this.error = errorText(e, 'Could not load the mentors.')) });
  }

  offersText(m: Mentor): string { return m.offers.map((h) => this.data?.help?.[h] ?? h).join(', '); }

  suggest(s: Student): void {
    this.api.get<{ student: Student; matches: Mentor[] }>(`/admin/mentoring/students/${s.id}/suggestions`).subscribe({
      next: (r) => (this.found = r), error: (e) => (this.error = errorText(e, 'Could not find mentors.'))
    });
  }

  startPair(m: Mentor, help: string): void {
    if (!this.found) return;
    this.pairing = { student: this.found.student, mentor: m, help, focus: '', startedOn: new Date().toISOString().slice(0, 10), notes: '' };
  }

  savePair(): void {
    if (!this.pairing) return;
    const p = this.pairing;
    this.busy = true;
    this.api.post<{ message: string }>('/admin/mentoring', {
      studentId: p.student.id, volunteerId: p.mentor.volunteer_id ?? null, alumniUserId: p.mentor.alumni_user_id ?? null,
      help: p.help, focus: p.focus, startedOn: p.startedOn, notes: p.notes
    }).subscribe({
      next: (r) => { this.busy = false; this.pairing = null; this.found = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not pair them.'); }
    });
  }

  setStatus(p: Pair, status: 'ACTIVE' | 'ENDED'): void {
    if (status === 'ENDED' && !confirm(`End ${p.mentor_name}'s mentoring of ${p.student_name}?`)) return;
    this.api.put<{ message: string }>(`/admin/mentoring/${p.id}`, { status }).subscribe({
      next: (r) => { this.message = r.message; this.load(); }, error: (e) => (this.error = errorText(e, 'Could not save.'))
    });
  }

  saveEdit(): void {
    if (!this.edit) return;
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/mentoring/${this.edit.pair.id}`, { focus: this.edit.focus, notes: this.edit.notes }).subscribe({
      next: (r) => { this.busy = false; this.edit = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }
}
