import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { tap } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';
import { CardTableDirective } from '../shared/card-table.directive';

interface RccStudent {
  id: number; user_id: string | null; name: string; sex: string | null; email: string | null; phone: string | null;
  status: string | null; rcc_center_id: number | null; rcc_center_name: string | null; address: string | null;
  father_name: string | null; mother_name: string | null; father_profession: string | null; mother_profession: string | null;
  father_mobile: string | null; mother_mobile: string | null; average_annual_salary: number | null;
}

interface Center { rcc_center_id: number; center_name: string }

interface StudentForm {
  refId: string; name: string; sex: string; email: string; phone: string; address: string; rccCenterId: number | null;
  fatherName: string; motherName: string; fatherProfession: string; motherProfession: string;
  fatherMobile: string; motherMobile: string; averageAnnualSalary: string;
}

/**
 * Admin > Coaching Center Students: students studying at an RCC / coaching center (personal and family details).
 * A coaching center lead sees, adds and edits only their own center's students; the office sees every center and
 * assigns the reference ID after the student's application.
 */
@Component({
  selector: 'app-admin-rcc-students',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coaching Center Students</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row mb-3" *ngIf="canEdit">
      <div class="col-12">
        <button class="btn btn-primary btn-responsive" (click)="openNew()" [disabled]="!centers.length"><i class="mdi mdi-plus mr-1"></i>Add Student</button>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <p class="text-muted">
          Students studying at {{ scoped ? 'your coaching center' : 'the RCC / coaching centers' }}: their personal and family details.
          The office assigns the reference ID after the student's application.
        </p>
        <div class="row mb-3">
          <div class="col-12 col-md-5 mb-2 mb-md-0">
            <input class="form-control" placeholder="Search name, reference ID, phone, father's name" [(ngModel)]="q" (ngModelChange)="table.search()">
          </div>
          <div class="col-12 col-md-4" *ngIf="!scoped">
            <select class="form-control" [(ngModel)]="centerId" (ngModelChange)="table.filter()">
              <option [ngValue]="null">All centers</option>
              <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
            </select>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-0">
            <thead><tr><th>Student</th><th>Reference ID</th><th>Phone</th><th>Father</th><th *ngIf="!scoped">Center</th><th></th></tr></thead>
            <tbody>
              <tr *ngIf="table.loading && !table.rows.length"><td colspan="6" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!table.loading && !table.rows.length"><td colspan="6" class="text-center text-muted">No students found.</td></tr>
              <tr *ngFor="let r of table.rows">
                <td><strong>{{ r.name }}</strong><div class="small text-muted">{{ r.email }}</div></td>
                <td><span *ngIf="r.user_id; else pending">{{ r.user_id }}</span><ng-template #pending><span class="badge badge-light border">Not assigned</span></ng-template></td>
                <td>{{ r.phone || '--' }}</td>
                <td>{{ r.father_name || '--' }}<div class="small text-muted">{{ r.father_mobile }}</div></td>
                <td *ngIf="!scoped">{{ r.rcc_center_name }}</td>
                <td class="text-right"><button class="btn btn-xs btn-outline-primary" (click)="openEdit(r)">{{ canEdit ? 'Edit' : 'View' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
      </div>
    </div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ editId ? form.name : 'Add Student' }}</h5>
            <button type="button" class="close" (click)="form = null">&times;</button>
          </div>
          <div class="modal-body">
            <fieldset [disabled]="!canEdit">
              <h5 class="mt-0">Personal details</h5>
              <div class="row">
                <div class="col-md-6 form-group"><label>Full name *</label><input class="form-control" [(ngModel)]="form.name"></div>
                <div class="col-md-6 form-group"><label>Gender</label>
                  <select class="form-control" [(ngModel)]="form.sex"><option value="M">Male</option><option value="F">Female</option></select>
                </div>
                <div class="col-md-6 form-group"><label>Phone</label><input class="form-control" [(ngModel)]="form.phone"></div>
                <div class="col-md-6 form-group"><label>Email</label><input type="email" class="form-control" [(ngModel)]="form.email"></div>
                <div class="col-12 form-group"><label>Address</label><textarea class="form-control" rows="2" [(ngModel)]="form.address"></textarea></div>
                <div class="col-md-6 form-group" *ngIf="!scoped"><label>RCC / coaching center *</label>
                  <select class="form-control" [(ngModel)]="form.rccCenterId">
                    <option [ngValue]="null">-- Select center --</option>
                    <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
                  </select>
                </div>
                <div class="col-md-6 form-group" *ngIf="!scoped && editId"><label>Reference ID</label>
                  <input class="form-control" [(ngModel)]="form.refId" placeholder="Assigned after the application">
                </div>
              </div>
              <h5>Family details</h5>
              <div class="row">
                <div class="col-md-6 form-group"><label>Father's name</label><input class="form-control" [(ngModel)]="form.fatherName"></div>
                <div class="col-md-6 form-group"><label>Mother's name</label><input class="form-control" [(ngModel)]="form.motherName"></div>
                <div class="col-md-6 form-group"><label>Father's profession</label><input class="form-control" [(ngModel)]="form.fatherProfession"></div>
                <div class="col-md-6 form-group"><label>Mother's profession</label><input class="form-control" [(ngModel)]="form.motherProfession"></div>
                <div class="col-md-6 form-group"><label>Father's mobile</label><input class="form-control" [(ngModel)]="form.fatherMobile"></div>
                <div class="col-md-6 form-group"><label>Mother's mobile</label><input class="form-control" [(ngModel)]="form.motherMobile"></div>
                <div class="col-md-6 form-group"><label>Average annual family income (₹)</label><input type="number" min="0" class="form-control" [(ngModel)]="form.averageAnnualSalary"></div>
              </div>
            </fieldset>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">{{ canEdit ? 'Cancel' : 'Close' }}</button>
            <button *ngIf="canEdit" type="button" class="btn btn-primary" (click)="save()" [disabled]="saving || !form.name.trim() || (!scoped && !form.rccCenterId)">
              {{ saving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminRccStudentsComponent implements OnInit, OnDestroy {
  q = '';
  centerId: number | null = null;
  readonly table = new ServerTable<RccStudent, PagedResult<RccStudent> & { centers: Center[]; scoped: boolean }>(
    (p) => this.api.get<PagedResult<RccStudent> & { centers: Center[]; scoped: boolean }>('/admin/rcc-students', { q: this.q, centerId: this.centerId, ...p })
      .pipe(tap((r) => { if (r.scoped && !r.centers.length) this.error = 'Your account is not linked to an RCC center. Ask the office to set it.'; })),
    (e) => (this.error = errorText(e, 'Could not load the students.')));
  saving = false;
  message = '';
  error = '';
  form: StudentForm | null = null;
  editId: number | null = null;

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('RCC_STUDENTS', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  ngOnDestroy(): void { this.table.destroy(); }

  load(): void { this.table.load(); }

  /** The centers this user may pick, and whether they are tied to their own center (from the last answer). */
  get centers(): Center[] { return this.table.last?.centers ?? []; }
  get scoped(): boolean { return this.table.last?.scoped ?? false; }

  openNew(): void {
    this.editId = null;
    this.form = {
      refId: '', name: '', sex: 'M', email: '', phone: '', address: '',
      rccCenterId: this.scoped ? this.centers[0]?.rcc_center_id ?? null : this.centerId,
      fatherName: '', motherName: '', fatherProfession: '', motherProfession: '', fatherMobile: '', motherMobile: '', averageAnnualSalary: ''
    };
  }

  openEdit(r: RccStudent): void {
    const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
    this.editId = r.id;
    this.form = {
      refId: s(r.user_id), name: s(r.name), sex: r.sex === 'F' ? 'F' : 'M', email: s(r.email), phone: s(r.phone), address: s(r.address),
      rccCenterId: r.rcc_center_id, fatherName: s(r.father_name), motherName: s(r.mother_name),
      fatherProfession: s(r.father_profession), motherProfession: s(r.mother_profession),
      fatherMobile: s(r.father_mobile), motherMobile: s(r.mother_mobile), averageAnnualSalary: s(r.average_annual_salary)
    };
  }

  save(): void {
    if (!this.form) return;
    const body: Record<string, unknown> = { ...this.form };
    if (this.scoped || !this.editId) delete body['refId']; // only the office assigns it, after the application
    this.saving = true;
    const req = this.editId
      ? this.api.put<{ message: string }>(`/admin/rcc-students/${this.editId}`, body)
      : this.api.post<{ message: string }>('/admin/rcc-students', body);
    req.subscribe({
      next: (r) => { this.saving = false; this.form = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the student.'); }
    });
  }
}
