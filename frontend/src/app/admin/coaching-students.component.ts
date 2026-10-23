import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';

export interface CoachingCenter { rcc_center_id: number; center_name: string }
/** What every coaching screen gets: the centers the user may choose, and whether they are tied to their own. */
export interface CoachingScope { centers: CoachingCenter[]; scoped: boolean; classes: number[] }

interface Student {
  id: number; rcc_center_id: number; center_name: string | null; admission_no: string | null; admission_date: string | null;
  class_level: number; name: string; gender: string | null; date_of_birth: string | null; school_name: string | null;
  previous_class_percent: number | null; father_name: string | null; father_occupation: string | null; mother_name: string | null;
  guardian_mobile: string | null; student_mobile: string | null; family_income: number | null; address: string | null;
  aadhaar_last4: string | null; status: string; remarks: string | null;
}
type Page = PagedResult<Student> & CoachingScope & { by_class: Record<string, number> };

const EMPTY_FORM = {
  rccCenterId: null as number | null, classLevel: 7 as number | null, admissionNo: '', admissionDate: '', name: '', gender: 'M',
  dateOfBirth: '', schoolName: '', previousClassPercent: '', fatherName: '', fatherOccupation: '', motherName: '',
  guardianMobile: '', studentMobile: '', familyIncome: '', address: '', aadhaarLast4: '', status: 'ACTIVE', remarks: ''
};
type StudentForm = typeof EMPTY_FORM;

/**
 * Coaching Center > Students (classes 7 to 10), from the admission form. The coaching center coordinator manages
 * their own center's students; a coaching teacher sees them; the office sees every center.
 */
@Component({
  selector: 'app-coaching-students',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, PagerComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coaching Center Students</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <button *ngIf="canEdit" class="btn btn-primary" (click)="openNew()" [disabled]="!centers.length"><i class="mdi mdi-plus mr-1"></i>New admission</button>
      <div class="btn-group">
        <button class="btn btn-sm" [ngClass]="classLevel === null ? 'btn-secondary' : 'btn-light'" (click)="pickClass(null)">All classes</button>
        <button *ngFor="let c of classes" class="btn btn-sm" [ngClass]="classLevel === c ? 'btn-secondary' : 'btn-light'" (click)="pickClass(c)">
          Class {{ c }} <span class="badge badge-light ml-1">{{ table.last?.by_class?.[c] ?? 0 }}</span></button>
      </div>
    </div>

    <div class="card"><div class="card-body">
      <div class="row mb-3">
        <div class="col-md-4 mb-2"><input class="form-control" placeholder="Search name, admission no., father, mobile, school" [(ngModel)]="q" (ngModelChange)="table.search()"></div>
        <div class="col-md-3 mb-2" *ngIf="!scoped">
          <select class="form-control" [(ngModel)]="centerId" (ngModelChange)="table.filter()" aria-label="Center">
            <option [ngValue]="null">All centers</option>
            <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
          </select>
        </div>
        <div class="col-md-2 mb-2">
          <select class="form-control" [(ngModel)]="status" (ngModelChange)="table.filter()" aria-label="Status">
            <option value="ACTIVE">Studying</option><option value="LEFT">Left</option><option value="ALL">All</option>
          </select>
        </div>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Student</th><th>Class</th><th>Adm. no.</th><th>Father</th><th>Mobile</th><th>School</th><th *ngIf="!scoped">Center</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="table.loading && !table.rows.length"><td colspan="8" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
          <tr *ngIf="!table.loading && !table.rows.length"><td colspan="8" class="text-center text-muted">No students found.</td></tr>
          <tr *ngFor="let r of table.rows">
            <td><strong>{{ r.name }}</strong> <span *ngIf="r.status === 'LEFT'" class="badge badge-light border">Left</span></td>
            <td>{{ r.class_level }}</td>
            <td>{{ r.admission_no || '-' }}</td>
            <td>{{ r.father_name || '-' }}<div class="small text-muted">{{ r.father_occupation }}</div></td>
            <td>{{ r.guardian_mobile || r.student_mobile || '-' }}</td>
            <td>{{ r.school_name || '-' }}</td>
            <td *ngIf="!scoped">{{ r.center_name }}</td>
            <td class="text-right text-nowrap">
              <button class="btn btn-xs btn-outline-primary" (click)="openEdit(r)">{{ canEdit ? 'Edit' : 'View' }}</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger ml-1" (click)="remove(r)" title="Delete (entered by mistake)"><i class="mdi mdi-delete"></i></button>
            </td>
          </tr>
        </tbody>
      </table></div>
      <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ editId ? form.name : 'New admission' }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <fieldset [disabled]="!canEdit">
              <h6 class="mt-0">Admission</h6>
              <div class="row">
                <div class="col-md-4 form-group" *ngIf="!scoped"><label>Coaching center *</label>
                  <select class="form-control" [(ngModel)]="form.rccCenterId">
                    <option [ngValue]="null">-- Choose --</option>
                    <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
                  </select></div>
                <div class="col-md-2 form-group"><label>Class *</label>
                  <select class="form-control" [(ngModel)]="form.classLevel"><option *ngFor="let c of classes" [ngValue]="c">{{ c }}</option></select></div>
                <div class="col-md-3 form-group"><label>Admission no.</label><input class="form-control" maxlength="30" [(ngModel)]="form.admissionNo"></div>
                <div class="col-md-3 form-group"><label>Admission date</label><input type="date" class="form-control" [(ngModel)]="form.admissionDate"></div>
              </div>
              <h6>Student</h6>
              <div class="row">
                <div class="col-md-6 form-group"><label>Full name *</label><input class="form-control" maxlength="150" [(ngModel)]="form.name"></div>
                <div class="col-md-3 form-group"><label>Gender</label>
                  <select class="form-control" [(ngModel)]="form.gender"><option value="M">Male</option><option value="F">Female</option></select></div>
                <div class="col-md-3 form-group"><label>Date of birth</label><input type="date" class="form-control" [(ngModel)]="form.dateOfBirth"></div>
                <div class="col-md-6 form-group"><label>School</label><input class="form-control" maxlength="200" [(ngModel)]="form.schoolName"></div>
                <div class="col-md-3 form-group"><label>Previous class marks (%)</label><input type="number" min="0" max="100" class="form-control" [(ngModel)]="form.previousClassPercent"></div>
                <div class="col-md-3 form-group"><label>Aadhaar (last 4 digits)</label><input class="form-control" maxlength="4" inputmode="numeric" [(ngModel)]="form.aadhaarLast4"></div>
                <div class="col-md-6 form-group"><label>Student's mobile</label><input class="form-control" [(ngModel)]="form.studentMobile"></div>
              </div>
              <h6>Family</h6>
              <div class="row">
                <div class="col-md-6 form-group"><label>Father's name</label><input class="form-control" [(ngModel)]="form.fatherName"></div>
                <div class="col-md-6 form-group"><label>Father's occupation</label><input class="form-control" [(ngModel)]="form.fatherOccupation"></div>
                <div class="col-md-6 form-group"><label>Mother's name</label><input class="form-control" [(ngModel)]="form.motherName"></div>
                <div class="col-md-6 form-group"><label>Parent / guardian mobile</label><input class="form-control" [(ngModel)]="form.guardianMobile"></div>
                <div class="col-md-6 form-group"><label>Yearly family income (₹)</label><input type="number" min="0" class="form-control" [(ngModel)]="form.familyIncome"></div>
                <div class="col-12 form-group"><label>Address</label><textarea class="form-control" rows="2" maxlength="500" [(ngModel)]="form.address"></textarea></div>
              </div>
              <div class="row">
                <div class="col-md-3 form-group"><label>Status</label>
                  <select class="form-control" [(ngModel)]="form.status"><option value="ACTIVE">Studying</option><option value="LEFT">Left</option></select></div>
                <div class="col-md-9 form-group"><label>Remarks</label><input class="form-control" maxlength="500" [(ngModel)]="form.remarks"></div>
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
export class CoachingStudentsComponent implements OnInit, OnDestroy {
  q = '';
  centerId: number | null = null;
  classLevel: number | null = null;
  status = 'ACTIVE';
  readonly table = new ServerTable<Student, Page>(
    (p) => this.api.get<Page>('/admin/coaching/students', { q: this.q, centerId: this.centerId, classLevel: this.classLevel, status: this.status, ...p }),
    (e) => (this.error = errorText(e, 'Could not load the students.')));
  form: StudentForm | null = null;
  editId: number | null = null;
  formError = '';
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('RCC_STUDENTS', 'EDIT'); }
  get centers(): CoachingCenter[] { return this.table.last?.centers ?? []; }
  get scoped(): boolean { return this.table.last?.scoped ?? false; }
  get classes(): number[] { return this.table.last?.classes ?? [7, 8, 9, 10]; }

  ngOnInit(): void { this.table.load(); }
  ngOnDestroy(): void { this.table.destroy(); }

  pickClass(c: number | null): void { this.classLevel = c; this.table.filter(); }

  openNew(): void {
    this.editId = null;
    this.formError = '';
    this.form = { ...EMPTY_FORM, rccCenterId: this.scoped ? this.centers[0]?.rcc_center_id ?? null : this.centerId,
      classLevel: this.classLevel ?? 7, admissionDate: new Date().toISOString().slice(0, 10) };
  }

  openEdit(r: Student): void {
    const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
    this.editId = r.id;
    this.formError = '';
    this.form = {
      rccCenterId: r.rcc_center_id, classLevel: r.class_level, admissionNo: s(r.admission_no), admissionDate: s(r.admission_date),
      name: r.name, gender: r.gender === 'F' ? 'F' : 'M', dateOfBirth: s(r.date_of_birth), schoolName: s(r.school_name),
      previousClassPercent: s(r.previous_class_percent), fatherName: s(r.father_name), fatherOccupation: s(r.father_occupation),
      motherName: s(r.mother_name), guardianMobile: s(r.guardian_mobile), studentMobile: s(r.student_mobile),
      familyIncome: s(r.family_income), address: s(r.address), aadhaarLast4: s(r.aadhaar_last4), status: r.status, remarks: s(r.remarks)
    };
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    const req = this.editId ? this.api.put(`/admin/coaching/students/${this.editId}`, this.form) : this.api.post('/admin/coaching/students', this.form);
    req.subscribe({
      next: () => { this.saving = false; this.message = this.editId ? 'Student updated.' : 'Student admitted.'; this.form = null; this.table.load(); },
      error: (e) => { this.saving = false; this.formError = errorText(e, 'Could not save the student.'); }
    });
  }

  remove(r: Student): void {
    if (!confirm(`Delete ${r.name}? Only for a record entered by mistake; otherwise set the status to Left.`)) return;
    this.api.delete<{ message: string }>(`/admin/coaching/students/${r.id}`).subscribe({
      next: (m) => { this.message = m.message; this.table.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the student.'))
    });
  }
}
