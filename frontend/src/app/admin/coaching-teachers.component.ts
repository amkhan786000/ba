import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { CoachingCenter, CoachingScope } from './coaching-students.component';

interface Teacher {
  id: number; rcc_center_id: number; center_name: string | null; name: string; gender: string | null; phone: string | null;
  email: string | null; qualification: string | null; subjects: string | null; classes: string | null; joining_date: string | null;
  address: string | null; active: boolean; remarks: string | null; login_email: string | null;
}
type Answer = CoachingScope & { rows: Teacher[]; subjects: string[] };

interface TeacherForm {
  rccCenterId: number | null; name: string; gender: string; phone: string; email: string; qualification: string;
  subjects: string[]; classes: number[]; joiningDate: string; address: string; active: boolean; remarks: string; loginEmail: string;
}

/**
 * Coaching Center > Teachers: basic details, the subjects and classes they teach, and (optionally) their Rahbar login,
 * which lets them sign in and see their center's students and exams (role Coaching Teacher).
 */
@Component({
  selector: 'app-coaching-teachers',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coaching Center Teachers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <button *ngIf="canEdit" class="btn btn-primary" (click)="openNew()" [disabled]="!centers.length"><i class="mdi mdi-plus mr-1"></i>Add teacher</button>
      <input class="form-control w-auto" placeholder="Search name, phone, subject" [(ngModel)]="q" (ngModelChange)="load()">
      <select *ngIf="!scoped" class="form-control w-auto" [(ngModel)]="centerId" (ngModelChange)="load()" aria-label="Center">
        <option [ngValue]="null">All centers</option>
        <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
      </select>
    </div>

    <div class="card"><div class="card-body">
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Teacher</th><th>Subjects</th><th>Classes</th><th>Phone</th><th>Qualification</th><th>Joined</th><th *ngIf="!scoped">Center</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!data"><td colspan="8" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
          <tr *ngIf="data && !data.rows.length"><td colspan="8" class="text-center text-muted">No teachers yet.</td></tr>
          <tr *ngFor="let t of data?.rows" [class.text-muted]="!t.active">
            <td><strong>{{ t.name }}</strong> <span *ngIf="!t.active" class="badge badge-light border">Inactive</span>
              <div class="small text-muted" *ngIf="t.login_email"><i class="mdi mdi-login"></i> {{ t.login_email }}</div></td>
            <td>{{ t.subjects || '-' }}</td>
            <td>{{ t.classes || '-' }}</td>
            <td>{{ t.phone || '-' }}<div class="small text-muted">{{ t.email }}</div></td>
            <td>{{ t.qualification || '-' }}</td>
            <td>{{ t.joining_date ? (t.joining_date | appDate) : '-' }}</td>
            <td *ngIf="!scoped">{{ t.center_name }}</td>
            <td class="text-right text-nowrap">
              <button class="btn btn-xs btn-outline-primary" (click)="openEdit(t)">{{ canEdit ? 'Edit' : 'View' }}</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger ml-1" (click)="remove(t)" title="Delete"><i class="mdi mdi-delete"></i></button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ editId ? form.name : 'Add teacher' }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <fieldset [disabled]="!canEdit">
              <div class="row">
                <div class="col-md-6 form-group"><label>Full name *</label><input class="form-control" maxlength="150" [(ngModel)]="form.name"></div>
                <div class="col-md-3 form-group"><label>Gender</label>
                  <select class="form-control" [(ngModel)]="form.gender"><option value="M">Male</option><option value="F">Female</option></select></div>
                <div class="col-md-3 form-group" *ngIf="!scoped"><label>Center *</label>
                  <select class="form-control" [(ngModel)]="form.rccCenterId">
                    <option [ngValue]="null">-- Choose --</option>
                    <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
                  </select></div>
                <div class="col-md-4 form-group"><label>Phone</label><input class="form-control" [(ngModel)]="form.phone"></div>
                <div class="col-md-4 form-group"><label>Email</label><input type="email" class="form-control" maxlength="150" [(ngModel)]="form.email"></div>
                <div class="col-md-4 form-group"><label>Qualification</label><input class="form-control" maxlength="150" [(ngModel)]="form.qualification" placeholder="e.g. B.Sc., B.Ed."></div>
                <div class="col-md-8 form-group"><label>Subjects taught</label>
                  <div class="d-flex flex-wrap" style="gap: 4px 14px">
                    <div class="custom-control custom-checkbox" *ngFor="let s of subjectChoices; let i = index">
                      <input type="checkbox" class="custom-control-input" [id]="'ts' + i" [checked]="form.subjects.includes(s)" (change)="toggle(form.subjects, s)">
                      <label class="custom-control-label" [for]="'ts' + i">{{ s }}</label>
                    </div>
                  </div></div>
                <div class="col-md-4 form-group"><label>Classes taught</label>
                  <div class="d-flex flex-wrap" style="gap: 4px 14px">
                    <div class="custom-control custom-checkbox" *ngFor="let c of classes">
                      <input type="checkbox" class="custom-control-input" [id]="'tc' + c" [checked]="form.classes.includes(c)" (change)="toggle(form.classes, c)">
                      <label class="custom-control-label" [for]="'tc' + c">{{ c }}</label>
                    </div>
                  </div></div>
                <div class="col-md-4 form-group"><label>Joining date</label><input type="date" class="form-control" [(ngModel)]="form.joiningDate"></div>
                <div class="col-md-8 form-group"><label>Address</label><input class="form-control" maxlength="500" [(ngModel)]="form.address"></div>
                <div class="col-md-8 form-group"><label>Remarks</label><input class="form-control" maxlength="500" [(ngModel)]="form.remarks"></div>
                <div class="col-md-4 form-group d-flex align-items-end">
                  <div class="custom-control custom-checkbox mb-2">
                    <input type="checkbox" class="custom-control-input" id="tactive" [(ngModel)]="form.active">
                    <label class="custom-control-label" for="tactive">Teaching here now</label>
                  </div></div>
                <div class="col-12 form-group"><label>Rahbar login <span class="text-muted small">(optional)</span></label>
                  <input type="email" class="form-control" [(ngModel)]="form.loginEmail" placeholder="Email of their Rahbar account">
                  <small class="text-muted">To let the teacher sign in and see this center's students and exams, the office adds a user with
                    the <strong>Coaching Teacher</strong> role in Manage Users; enter that account's email here. Teachers can't change records.</small></div>
              </div>
            </fieldset>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">{{ canEdit ? 'Cancel' : 'Close' }}</button>
            <button *ngIf="canEdit" type="button" class="btn btn-primary" (click)="save()"
                    [disabled]="saving || !form.name.trim() || (!scoped && !form.rccCenterId)">{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoachingTeachersComponent implements OnInit {
  data: Answer | null = null;
  q = '';
  centerId: number | null = null;
  form: TeacherForm | null = null;
  editId: number | null = null;
  formError = '';
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('COACHING_TEACHERS', 'EDIT'); }
  get centers(): CoachingCenter[] { return this.data?.centers ?? []; }
  get scoped(): boolean { return this.data?.scoped ?? false; }
  get classes(): number[] { return this.data?.classes ?? [7, 8, 9, 10]; }
  /** The subject list, plus any subject the teacher has that isn't on it (any more). */
  get subjectChoices(): string[] {
    const list = [...(this.data?.subjects ?? [])];
    for (const s of this.form?.subjects ?? []) if (!list.includes(s)) list.push(s);
    return list;
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Answer>('/admin/coaching/teachers', { q: this.q, centerId: this.centerId }).subscribe({
      next: (d) => (this.data = d),
      error: (e) => (this.error = errorText(e, 'Could not load the teachers.'))
    });
  }

  toggle<T>(list: T[], v: T): void {
    const i = list.indexOf(v);
    if (i >= 0) list.splice(i, 1); else list.push(v);
  }

  openNew(): void {
    this.editId = null;
    this.formError = '';
    this.form = { rccCenterId: this.scoped ? this.centers[0]?.rcc_center_id ?? null : this.centerId, name: '', gender: 'M', phone: '', email: '',
      qualification: '', subjects: [], classes: [], joiningDate: '', address: '', active: true, remarks: '', loginEmail: '' };
  }

  openEdit(t: Teacher): void {
    const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
    this.editId = t.id;
    this.formError = '';
    this.form = {
      rccCenterId: t.rcc_center_id, name: t.name, gender: t.gender === 'F' ? 'F' : 'M', phone: s(t.phone), email: s(t.email),
      qualification: s(t.qualification), subjects: (t.subjects ?? '').split(',').map((x) => x.trim()).filter((x) => x),
      classes: (t.classes ?? '').split(',').filter((x) => x).map(Number), joiningDate: s(t.joining_date), address: s(t.address),
      active: t.active, remarks: s(t.remarks), loginEmail: s(t.login_email)
    };
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    const req = this.editId ? this.api.put(`/admin/coaching/teachers/${this.editId}`, this.form) : this.api.post('/admin/coaching/teachers', this.form);
    req.subscribe({
      next: () => { this.saving = false; this.message = 'Teacher saved.'; this.form = null; this.load(); },
      error: (e) => { this.saving = false; this.formError = errorText(e, 'Could not save the teacher.'); }
    });
  }

  remove(t: Teacher): void {
    if (!confirm(`Delete ${t.name}? To keep the record, untick "Teaching here now" instead.`)) return;
    this.api.delete<{ message: string }>(`/admin/coaching/teachers/${t.id}`).subscribe({
      next: (m) => { this.message = m.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the teacher.'))
    });
  }
}
