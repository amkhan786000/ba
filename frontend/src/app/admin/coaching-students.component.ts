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
  aadhaar_last4: string | null; status: string; remarks: string | null; email: string | null;
  session_from: number | null; session_to: number | null; session_label: string | null; father_mobile: string | null; mother_mobile: string | null;
  school_class: string | null; school_roll_no: string | null; guardian_name: string | null; declaration_accepted: boolean;
  photo_path: string | null; test_roll_no: string | null; test_attendance: string | null; contact_mobile: string | null;
}
type Page = PagedResult<Student> & CoachingScope & { by_class: Record<string, number>; by_status: Record<string, number> };

/** Status codes and what the screen calls them: registered, admitted (selected), not admitted, left. */
const STATUS: Record<string, string> = { APPLIED: 'Applied', ACTIVE: 'Studying (selected)', NOT_SELECTED: 'Not selected', LEFT: 'Left' };

const EMPTY_FORM = {
  rccCenterId: null as number | null, classLevel: 7 as number | null, sessionFrom: '' as string | number, sessionTo: '' as string | number,
  admissionNo: '', admissionDate: '', name: '', gender: 'M', dateOfBirth: '', studentMobile: '', email: '', aadhaarLast4: '',
  schoolName: '', schoolClass: '', schoolRollNo: '', previousClassPercent: '',
  fatherName: '', fatherMobile: '', motherName: '', motherMobile: '', guardianMobile: '', fatherOccupation: '', familyIncome: '', address: '',
  guardianName: '', declarationAccepted: false, testRollNo: '', testAttendance: '', status: 'APPLIED', remarks: ''
};
type StudentForm = typeof EMPTY_FORM;

/**
 * Coaching Center > Students (classes 7 to 10), following the paper admission form. A registration is "Applied"
 * until the entrance test: mark the test roll no. and present / absent, then Selected (studying: they join classes,
 * attendance and exams) or Not selected. The coaching center coordinator manages their own center's students; a
 * coaching teacher sees them; the office sees every center.
 */
@Component({
  selector: 'app-coaching-students',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, PagerComponent],
  styles: [`
    .photo { width: 110px; height: 130px; object-fit: cover; border: 1px solid #dee2e6; border-radius: 4px; }
    .photo-empty { width: 110px; height: 130px; border: 1px dashed #ced4da; border-radius: 4px; display: flex; align-items: center; justify-content: center; color: #adb5bd; font-size: 40px; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coaching Center Students</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <button *ngIf="canEdit" class="btn btn-primary" (click)="openNew()" [disabled]="!centers.length"><i class="mdi mdi-plus mr-1"></i>New admission form</button>
      <div class="btn-group">
        <button class="btn btn-sm" [ngClass]="classLevel === null ? 'btn-secondary' : 'btn-light'" (click)="pickClass(null)">All classes</button>
        <button *ngFor="let c of classes" class="btn btn-sm" [ngClass]="classLevel === c ? 'btn-secondary' : 'btn-light'" (click)="pickClass(c)">
          Class {{ c }} <span class="badge badge-light ml-1">{{ table.last?.by_class?.[c] ?? 0 }}</span></button>
      </div>
    </div>

    <ul class="nav nav-tabs mb-0">
      <li class="nav-item" *ngFor="let s of statusTabs">
        <a href="" class="nav-link" [class.active]="status === s.key" (click)="$event.preventDefault(); status = s.key; table.filter()">
          {{ s.label }} <span class="badge badge-light ml-1">{{ s.key === 'ALL' ? total() : (table.last?.by_status?.[s.key] ?? 0) }}</span></a>
      </li>
    </ul>
    <div class="card"><div class="card-body">
      <p *ngIf="status === 'APPLIED'" class="small text-muted">Registered and waiting for the entrance test / selection. Mark who came to the test, then
        <strong>Select</strong> (they join classes, attendance and exams) or <strong>Not selected</strong>.</p>
      <div class="row mb-3">
        <div class="col-md-5 mb-2"><input class="form-control" placeholder="Search name, admission / test roll no., father, mobile, school" [(ngModel)]="q" (ngModelChange)="table.search()"></div>
        <div class="col-md-3 mb-2" *ngIf="!scoped">
          <select class="form-control" [(ngModel)]="centerId" (ngModelChange)="table.filter()" aria-label="Center">
            <option [ngValue]="null">All centers</option>
            <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
          </select>
        </div>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Student</th><th>Class</th><th>Session</th><th>{{ status === 'APPLIED' ? 'Test roll no.' : 'Adm. no.' }}</th><th>Father</th><th>Mobile</th>
          <th>School</th><th *ngIf="!scoped">Center</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="table.loading && !table.rows.length"><td colspan="9" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
          <tr *ngIf="!table.loading && !table.rows.length"><td colspan="9" class="text-center text-muted">No students found.</td></tr>
          <tr *ngFor="let r of table.rows">
            <td><strong>{{ r.name }}</strong> <span *ngIf="status === 'ALL' || r.status !== status" class="badge" [ngClass]="badge(r.status)">{{ statusLabel(r.status) }}</span></td>
            <td>{{ r.class_level }}</td>
            <td>{{ r.session_label || '-' }}</td>
            <td>{{ (status === 'APPLIED' ? r.test_roll_no : r.admission_no) || '-' }}</td>
            <td>{{ r.father_name || '-' }}<div class="small text-muted">{{ r.father_occupation }}</div></td>
            <td>{{ r.contact_mobile || '-' }}</td>
            <td>{{ r.school_name || '-' }}<div class="small text-muted" *ngIf="r.school_class">Class {{ r.school_class }}{{ r.school_roll_no ? ', roll ' + r.school_roll_no : '' }}</div></td>
            <td *ngIf="!scoped">{{ r.center_name }}</td>
            <td class="text-right text-nowrap">
              <ng-container *ngIf="canEdit && r.status === 'APPLIED'">
                <div class="btn-group btn-group-sm mr-1" title="Entrance test">
                  <button class="btn" [ngClass]="r.test_attendance === 'PRESENT' ? 'btn-success' : 'btn-outline-success'" (click)="admission(r, { testAttendance: r.test_attendance === 'PRESENT' ? '' : 'PRESENT' })">Present</button>
                  <button class="btn" [ngClass]="r.test_attendance === 'ABSENT' ? 'btn-danger' : 'btn-outline-danger'" (click)="admission(r, { testAttendance: r.test_attendance === 'ABSENT' ? '' : 'ABSENT' })">Absent</button>
                </div>
                <button class="btn btn-xs btn-success" (click)="admission(r, { decision: 'SELECTED' })">Select</button>
                <button class="btn btn-xs btn-outline-secondary ml-1" (click)="admission(r, { decision: 'NOT_SELECTED' })">Not selected</button>
              </ng-container>
              <button class="btn btn-xs btn-outline-primary ml-1" (click)="openEdit(r)">{{ canEdit ? 'Edit' : 'View' }}</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger ml-1" (click)="remove(r)" title="Delete (entered by mistake)"><i class="mdi mdi-delete"></i></button>
            </td>
          </tr>
        </tbody>
      </table></div>
      <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="closeForm()">
      <div class="modal-dialog modal-xl modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ editId ? form.name : 'Admission form' }}</h5>
            <button type="button" class="close" (click)="closeForm()" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <fieldset [disabled]="!canEdit">
              <div class="row">
                <div class="col-md-9">
                  <h6 class="mt-0">Admission</h6>
                  <div class="row">
                    <div class="col-md-4 form-group" *ngIf="!scoped"><label>Coaching center *</label>
                      <select class="form-control" [(ngModel)]="form.rccCenterId">
                        <option [ngValue]="null">-- Choose --</option>
                        <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
                      </select></div>
                    <div class="col-md-4 form-group"><label>Session</label>
                      <div class="d-flex align-items-center">
                        <input type="number" class="form-control" min="2000" max="2100" placeholder="2025" [(ngModel)]="form.sessionFrom" aria-label="Session from">
                        <span class="mx-1">–</span>
                        <input type="number" class="form-control" min="2000" max="2100" placeholder="2028" [(ngModel)]="form.sessionTo" aria-label="Session to">
                      </div></div>
                    <div class="col-md-2 form-group"><label>Applied for class *</label>
                      <select class="form-control" [(ngModel)]="form.classLevel"><option *ngFor="let c of classes" [ngValue]="c">{{ c }}</option></select></div>
                    <div class="col-md-2 form-group"><label>Date</label><input type="date" class="form-control" [(ngModel)]="form.admissionDate"></div>
                    <div class="col-md-4 form-group"><label>Admission no.</label><input class="form-control" maxlength="30" [(ngModel)]="form.admissionNo"></div>
                  </div>
                </div>
                <div class="col-md-3 text-center">
                  <img *ngIf="photoUrl" [src]="photoUrl" class="photo" alt="Student's photo">
                  <div *ngIf="!photoUrl" class="photo-empty mx-auto"><i class="mdi mdi-account"></i></div>
                  <div *ngIf="canEdit" class="mt-2">
                    <label class="btn btn-sm btn-outline-primary mb-0">{{ photoUrl ? 'Change photo' : 'Add photo' }}
                      <input type="file" accept="image/*" hidden (change)="pickPhoto($event)"></label>
                    <button *ngIf="editId && photoUrl && !photoFile" type="button" class="btn btn-sm btn-link text-danger" (click)="removePhoto()">Remove</button>
                    <div *ngIf="photoFile" class="small text-muted">Saved with the form</div>
                  </div>
                </div>
              </div>
              <h6>Student</h6>
              <div class="row">
                <div class="col-md-5 form-group"><label>Student's name *</label><input class="form-control" maxlength="150" [(ngModel)]="form.name"></div>
                <div class="col-md-3 form-group"><label>Mobile</label><input class="form-control" [(ngModel)]="form.studentMobile"></div>
                <div class="col-md-2 form-group"><label>Date of birth</label><input type="date" class="form-control" [(ngModel)]="form.dateOfBirth"></div>
                <div class="col-md-2 form-group"><label>Gender</label>
                  <select class="form-control" [(ngModel)]="form.gender"><option value="M">Male</option><option value="F">Female</option></select></div>
                <div class="col-md-3 form-group"><label>Aadhaar (last 4 digits)</label><input class="form-control" maxlength="4" inputmode="numeric" [(ngModel)]="form.aadhaarLast4"></div>
                <div class="col-md-5 form-group"><label>Email <span class="text-muted small">(for the scholarship invitation)</span></label>
                  <input type="email" class="form-control" maxlength="150" [(ngModel)]="form.email"></div>
              </div>
              <h6>Family</h6>
              <div class="row">
                <div class="col-md-5 form-group"><label>Father's name</label><input class="form-control" [(ngModel)]="form.fatherName"></div>
                <div class="col-md-3 form-group"><label>Father's mobile</label><input class="form-control" [(ngModel)]="form.fatherMobile"></div>
                <div class="col-md-4 form-group"><label>Parent / guardian's profession</label><input class="form-control" maxlength="100" [(ngModel)]="form.fatherOccupation"></div>
                <div class="col-md-5 form-group"><label>Mother's name</label><input class="form-control" [(ngModel)]="form.motherName"></div>
                <div class="col-md-3 form-group"><label>Mother's mobile</label><input class="form-control" [(ngModel)]="form.motherMobile"></div>
                <div class="col-md-4 form-group"><label>Family income from all sources (₹ / year)</label><input type="number" min="0" class="form-control" [(ngModel)]="form.familyIncome"></div>
                <div class="col-md-8 form-group"><label>Full residential address</label><textarea class="form-control" rows="2" maxlength="500" [(ngModel)]="form.address"></textarea></div>
                <div class="col-md-4 form-group"><label>Other guardian's mobile</label><input class="form-control" [(ngModel)]="form.guardianMobile"></div>
              </div>
              <h6>School</h6>
              <div class="row">
                <div class="col-md-6 form-group"><label>School's name</label><input class="form-control" maxlength="200" [(ngModel)]="form.schoolName"></div>
                <div class="col-md-2 form-group"><label>Class at school</label><input class="form-control" maxlength="20" [(ngModel)]="form.schoolClass" placeholder="e.g. VII"></div>
                <div class="col-md-2 form-group"><label>Roll no.</label><input class="form-control" maxlength="30" [(ngModel)]="form.schoolRollNo"></div>
                <div class="col-md-2 form-group"><label>Previous marks (%)</label><input type="number" min="0" max="100" class="form-control" [(ngModel)]="form.previousClassPercent"></div>
              </div>
              <h6>Declaration</h6>
              <div class="row">
                <div class="col-md-5 form-group"><label>Name of parent / guardian</label><input class="form-control" maxlength="150" [(ngModel)]="form.guardianName"></div>
                <div class="col-md-7 form-group d-flex align-items-end">
                  <div class="custom-control custom-checkbox mb-2">
                    <input type="checkbox" class="custom-control-input" id="decl" [(ngModel)]="form.declarationAccepted">
                    <label class="custom-control-label" for="decl">The parent / guardian declared that all the information given is true and correct (signed form)</label>
                  </div></div>
              </div>
              <h6>Entrance test & selection <span class="small text-muted">(office use)</span></h6>
              <div class="row">
                <div class="col-md-3 form-group"><label>Test roll no.</label><input class="form-control" maxlength="30" [(ngModel)]="form.testRollNo" placeholder="e.g. 8001"></div>
                <div class="col-md-3 form-group"><label>At the test</label>
                  <select class="form-control" [(ngModel)]="form.testAttendance"><option value="">Not marked</option><option value="PRESENT">Present</option><option value="ABSENT">Absent</option></select></div>
                <div class="col-md-3 form-group"><label>Status</label>
                  <select class="form-control" [(ngModel)]="form.status"><option *ngFor="let s of statusList" [value]="s.key">{{ s.label }}</option></select></div>
                <div class="col-md-12 form-group"><label>Remarks</label><input class="form-control" maxlength="500" [(ngModel)]="form.remarks"></div>
              </div>
            </fieldset>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="closeForm()">{{ canEdit ? 'Cancel' : 'Close' }}</button>
            <button *ngIf="canEdit" type="button" class="btn btn-primary" (click)="save()"
                    [disabled]="saving || !form.name.trim() || (!scoped && !form.rccCenterId)">{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoachingStudentsComponent implements OnInit, OnDestroy {
  readonly statusList = Object.entries(STATUS).map(([key, label]) => ({ key, label }));
  readonly statusTabs = [{ key: 'ACTIVE', label: 'Studying' }, { key: 'APPLIED', label: 'Applied' }, { key: 'NOT_SELECTED', label: 'Not selected' },
    { key: 'LEFT', label: 'Left' }, { key: 'ALL', label: 'All' }];
  q = '';
  centerId: number | null = null;
  classLevel: number | null = null;
  status = 'ACTIVE';
  readonly table = new ServerTable<Student, Page>(
    (p) => this.api.get<Page>('/admin/coaching/students', { q: this.q, centerId: this.centerId, classLevel: this.classLevel, status: this.status, ...p }),
    (e) => (this.error = errorText(e, 'Could not load the students.')));
  form: StudentForm | null = null;
  editId: number | null = null;
  /** The photo shown in the form (object URL), and a new one picked but not uploaded yet. */
  photoUrl: string | null = null;
  photoFile: File | null = null;
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
  ngOnDestroy(): void { this.table.destroy(); this.clearPhoto(); }

  total(): number { return Object.values(this.table.last?.by_status ?? {}).reduce((a, b) => a + b, 0); }
  statusLabel(s: string): string { return STATUS[s] ?? s; }
  badge(s: string): string {
    return ({ APPLIED: 'badge-info', ACTIVE: 'badge-success', NOT_SELECTED: 'badge-light border', LEFT: 'badge-light border' } as Record<string, string>)[s] ?? 'badge-light';
  }

  pickClass(c: number | null): void { this.classLevel = c; this.table.filter(); }

  openNew(): void {
    this.editId = null;
    this.formError = '';
    this.clearPhoto();
    const y = new Date().getFullYear();
    this.form = { ...EMPTY_FORM, rccCenterId: this.scoped ? this.centers[0]?.rcc_center_id ?? null : this.centerId,
      classLevel: this.classLevel ?? 7, sessionFrom: y, sessionTo: y + 3, admissionDate: new Date().toISOString().slice(0, 10) };
  }

  openEdit(r: Student): void {
    const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
    this.editId = r.id;
    this.formError = '';
    this.clearPhoto();
    this.form = {
      rccCenterId: r.rcc_center_id, classLevel: r.class_level, sessionFrom: r.session_from ?? '', sessionTo: r.session_to ?? '',
      admissionNo: s(r.admission_no), admissionDate: s(r.admission_date), name: r.name, gender: r.gender === 'F' ? 'F' : 'M',
      dateOfBirth: s(r.date_of_birth), studentMobile: s(r.student_mobile), email: s(r.email), aadhaarLast4: s(r.aadhaar_last4),
      schoolName: s(r.school_name), schoolClass: s(r.school_class), schoolRollNo: s(r.school_roll_no), previousClassPercent: s(r.previous_class_percent),
      fatherName: s(r.father_name), fatherMobile: s(r.father_mobile), motherName: s(r.mother_name), motherMobile: s(r.mother_mobile),
      guardianMobile: s(r.guardian_mobile), fatherOccupation: s(r.father_occupation), familyIncome: s(r.family_income), address: s(r.address),
      guardianName: s(r.guardian_name), declarationAccepted: !!r.declaration_accepted, testRollNo: s(r.test_roll_no),
      testAttendance: s(r.test_attendance), status: r.status, remarks: s(r.remarks)
    };
    if (r.photo_path) {
      this.api.blobUrl('/files/' + encodeURIComponent(r.photo_path.split('/').pop() ?? '')).subscribe({ next: (u) => (this.photoUrl = u) });
    }
  }

  closeForm(): void { this.form = null; this.clearPhoto(); }

  private clearPhoto(): void {
    if (this.photoUrl) URL.revokeObjectURL(this.photoUrl);
    this.photoUrl = null;
    this.photoFile = null;
  }

  pickPhoto(e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (!f) return;
    if (this.photoUrl) URL.revokeObjectURL(this.photoUrl);
    this.photoFile = f;
    this.photoUrl = URL.createObjectURL(f);
  }

  removePhoto(): void {
    if (!this.editId || !confirm('Remove the photo?')) return;
    this.api.delete(`/admin/coaching/students/${this.editId}/photo`).subscribe({
      next: () => { this.clearPhoto(); this.table.load(); }, error: (e) => (this.formError = errorText(e, 'Could not remove the photo.'))
    });
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    const req = this.editId ? this.api.put<Student>(`/admin/coaching/students/${this.editId}`, this.form)
      : this.api.post<Student>('/admin/coaching/students', this.form);
    req.subscribe({
      next: (saved) => {
        const done = () => { this.saving = false; this.message = this.editId ? 'Student updated.' : 'Admission form saved.'; this.closeForm(); this.table.load(); };
        if (!this.photoFile) { done(); return; }
        const fd = new FormData();
        fd.append('file', this.photoFile);
        this.api.post(`/admin/coaching/students/${saved.id ?? this.editId}/photo`, fd).subscribe({
          next: done,
          error: (e) => { this.saving = false; this.editId = saved.id ?? this.editId; this.formError = errorText(e, 'The form was saved but the photo could not be uploaded.'); }
        });
      },
      error: (e) => { this.saving = false; this.formError = errorText(e, 'Could not save the student.'); }
    });
  }

  admission(r: Student, change: Record<string, string>): void {
    this.api.put<{ message: string }>(`/admin/coaching/students/${r.id}/admission`, change).subscribe({
      next: (m) => { if (change['decision']) this.message = m.message; this.table.load(); },
      error: (e) => (this.error = errorText(e, 'Could not save.'))
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
