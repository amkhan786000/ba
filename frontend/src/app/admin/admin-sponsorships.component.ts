import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FEATURES } from '../core/models/features';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { StudyStatusService } from '../core/services/study-status.service';
import { ChapterService } from '../core/services/chapter.service';
import { Chapter } from './admin-chapters.component';
import { RccCenter } from './admin-rcc-centers.component';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BulkReport, BulkReportComponent } from '../shared/bulk-report.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';
import { CardTableDirective } from '../shared/card-table.directive';
import { AppDatePipe } from '../shared/app-date';
import { PreferenceOptions, SponsorPreferences, SponsorPreferencesComponent, preferencesFrom } from '../shared/sponsor-preferences.component';

interface SponsorRow {
  id: number;
  user_id: string;
  name: string;
  /** Who brought the sponsor ("c/o"). */
  care_of?: string | null;
  email: string | null;
  phone: string | null;
  chapter_id: number | null;
  chapter_name: string | null;
  role_name: string;
  student_count: number;
}

interface SponsorStudent {
  id: number;
  user_id: string;
  name: string;
  email: string | null;
  phone: string | null;
  status: string | null;
  year?: number | null;
  chapter_name?: string | null;
  study_status?: string | null;
  on_behalf_text?: string | null;
}

/** Port of templates/admin/manage_sponsorships.html (DataTable, bulk upload, edit-profile modal). Students map straight to sponsors. */
/** One student's fee schedule status (Sponsorships > Fee Schedule). */
interface FeeRow {
  student_id: number; student_code: string; student_name: string; session_year: number | null; payment_start_date: string | null;
  installments: number; paid: number; due: number; overdue: number; problem: string | null;
}

@Component({
  selector: 'app-admin-sponsorships',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, BulkReportComponent, CardTableDirective, SponsorPreferencesComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Sponsorships</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <div class="d-flex flex-column flex-md-row justify-content-between align-items-center mb-4">
              <h4 class="header-title mb-2 mb-md-0">Sponsors</h4>
              <div class="d-flex flex-column flex-sm-row align-items-center">
                <button *ngIf="canManage && bulkUpload" type="button" class="btn btn-success waves-effect waves-light" (click)="showUpload = true">
                  <i class="mdi mdi-file-upload"></i> Bulk Upload
                </button>
              </div>
            </div>

            <div class="row dt-toolbar mb-2">
              <div class="col-sm-6">
                <label>Show
                  <select class="form-control form-control-sm" [(ngModel)]="table.pg.size" (ngModelChange)="table.filter()">
                    <option [ngValue]="10">10</option><option [ngValue]="25">25</option><option [ngValue]="50">50</option><option [ngValue]="100">100</option>
                  </select> entries</label>
              </div>
              <div class="col-sm-6 text-sm-right">
                <label>Search:<input type="search" class="form-control form-control-sm" [(ngModel)]="search" (ngModelChange)="table.search()"></label>
              </div>
            </div>

            <div class="table-responsive">
              <table class="table table-bordered table-striped nowrap" style="width:100%">
                <thead><tr><th>Sponsor Name</th><th>Students</th><th *ngIf="showDetails">Contact Info</th><th *ngIf="showDetails">Chapter</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!table.loading && !table.rows.length"><td [attr.colspan]="showDetails ? 5 : 3" class="text-center text-muted">No matching records found</td></tr>
                  <tr *ngFor="let s of table.rows">
                    <td><strong>{{ s.name }}</strong><span *ngIf="s.care_of" class="small text-muted"> c/o {{ s.care_of }}</span><br><small class="text-muted">ID: {{ s.user_id }}</small></td>
                    <td>
                      <a *ngIf="s.student_count; else noStudents" href="" class="badge badge-light border student-count"
                         (click)="$event.preventDefault(); showStudents(s)" [title]="'See the students of ' + s.name">{{ s.student_count }}</a>
                      <ng-template #noStudents><span class="badge badge-light border">0</span></ng-template>
                    </td>
                    <td *ngIf="showDetails">
                      <span><i class="mdi mdi-email-outline"></i> {{ s.email || '-' }}</span><br>
                      <span><i class="mdi mdi-phone"></i> {{ s.phone || '-' }}</span>
                    </td>
                    <td *ngIf="showDetails">{{ s.chapter_name }}</td>
                    <td>
                      <div class="btn-group">
                        <button *ngIf="canEditProfile" class="btn btn-sm btn-info" (click)="openEdit(s.id)"><i class="mdi mdi-pencil"></i> Edit Profile</button>
                        <a *ngIf="canManage" [routerLink]="['/', section, 'sponsorships', s.id, 'map']" class="btn btn-sm btn-primary waves-effect waves-light">
                          <i class="mdi mdi-account-arrow-right"></i> Map Students
                        </a>
                        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="statement = { sponsor: s, year: lastYear, busy: false }">
                          <i class="mdi mdi-file-pdf-box"></i> Statement
                        </button>
                        <button *ngIf="canSeeFeeSchedule" type="button" class="btn btn-sm btn-outline-success" (click)="openFeeSchedule(s)">
                          <i class="mdi mdi-calendar-check"></i> Fee Schedule
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="table.pg" [total]="table.total" [sizes]="[]" (pageChange)="table.load()"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <app-bulk-report *ngIf="report" [report]="report" title="Sponsor upload results" createdLabel="New sponsors"
                     updatedLabel="Merged with existing" [showMappings]="true" (closed)="report = null"></app-bulk-report>

    <!-- Bulk upload modal -->
    <div *ngIf="showUpload" class="modal fade show d-block" tabindex="-1" (click)="showUpload = false">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-success text-white">
            <h5 class="modal-title text-white"><i class="mdi mdi-file-upload"></i> Bulk Import Sponsor Data</h5>
            <button type="button" class="close text-white" (click)="showUpload = false">&times;</button>
          </div>
          <form (ngSubmit)="upload()">
            <div class="modal-body">
              <div class="alert alert-info">
                <strong>Format Required:</strong><br>
                <small>Sponsor Name, Sponsor Email, Sponsor Mobile1, Sponsor Chapter, Student Assigned (comma-separated student IDs), and optionally Sponsor ID for new sponsors. Existing sponsors are matched by email, then phone, then name + chapter.</small>
                <div class="mt-2"><button type="button" class="btn btn-sm btn-outline-primary" (click)="template()"><i class="mdi mdi-download"></i> Download CSV template</button></div>
              </div>
              <div class="form-group">
                <label>Select CSV File</label>
                <input type="file" class="form-control-file" accept=".csv" (change)="onFile($event)" required>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="showUpload = false">Cancel</button>
              <button type="submit" class="btn btn-success" [disabled]="!file">Upload &amp; Process</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Edit sponsor modal -->
    <div *ngIf="edit" class="modal fade show d-block" tabindex="-1" (click)="edit = null">
      <div class="modal-dialog modal-xl" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-info text-white">
            <h4 class="modal-title text-white">Edit Sponsor Details: {{ editName }}</h4>
            <button type="button" class="close text-white" (click)="edit = null">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="editError" class="alert alert-danger">{{ editError }}</div>
            <h5 class="text-info border-bottom pb-2"><i class="mdi mdi-account-circle"></i> Master Account Details</h5>
            <div class="row">
              <div class="col-md-6 form-group"><label>Full Name</label><input type="text" class="form-control" [(ngModel)]="edit.name"></div>
              <div class="col-md-6 form-group"><label>c/o <span class="text-muted small">(who brought this sponsor, optional)</span></label>
                <input type="text" class="form-control" maxlength="150" [(ngModel)]="edit.careOf" placeholder="e.g. Imran Khan"></div>
              <div class="col-md-6 form-group"><label>Email Address</label><input type="email" class="form-control" [(ngModel)]="edit.email"></div>
              <div class="col-md-6 form-group"><label>Phone (Mobile 1)</label><input type="text" class="form-control" [(ngModel)]="edit.phone"></div>
              <div class="col-md-6 form-group"><label>Chapter</label>
                <select class="form-control" [(ngModel)]="edit.chapterId">
                  <option [ngValue]="null">No chapter</option>
                  <option *ngFor="let c of chapterOptions(edit.chapterId)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                </select>
              </div>
              <div class="col-12 form-group" *ngIf="centers">
                <label>Coaching centers they run / pay for <small class="text-muted">(tick all that apply)</small></label>
                <div *ngIf="!centers.length" class="small text-muted">No coaching centers yet.</div>
                <div class="d-flex flex-wrap" style="gap: 4px 16px">
                  <div class="custom-control custom-checkbox" *ngFor="let c of centers">
                    <input type="checkbox" class="custom-control-input" [id]="'sp-center-' + c.rccCenterId"
                           [checked]="hasCenter(c.rccCenterId)" (change)="toggleCenter(c.rccCenterId)">
                    <label class="custom-control-label" [for]="'sp-center-' + c.rccCenterId">{{ c.centerName }}</label>
                  </div>
                </div>
              </div>
            </div>
            <h5 class="text-success border-bottom pb-2 mt-4"><i class="mdi mdi-heart-outline"></i> Student preferences
              <small class="text-muted">(what kind of student this sponsor would like)</small></h5>
            <app-sponsor-preferences [(value)]="prefs" [options]="prefOptions"></app-sponsor-preferences>
            <h5 class="text-primary border-bottom pb-2 mt-4"><i class="mdi mdi-account-multiple"></i> Assigned Students</h5>
            <div class="table-responsive">
              <table class="table table-sm table-bordered">
                <thead class="bg-light"><tr><th>Student ID</th><th>Name</th><th>Email</th><th>Phone</th><th>Status</th></tr></thead>
                <tbody>
                  <tr *ngIf="!students.length"><td colspan="5" class="text-center">No students assigned to this sponsor.</td></tr>
                  <tr *ngFor="let st of students">
                    <td><strong>{{ st.user_id }}</strong></td>
                    <td>{{ st.name }}</td>
                    <td>{{ st.email || '-' }}</td>
                    <td>{{ st.phone || '-' }}</td>
                    <td>{{ st.status || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="edit = null">Cancel</button>
            <button type="button" class="btn btn-info" (click)="saveEdit()" [disabled]="saving">
              <i class="mdi" [ngClass]="saving ? 'mdi-loading mdi-spin' : 'mdi-content-save'"></i> {{ saving ? 'Saving...' : 'Update Profile' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- The students of one sponsor (click on the number of students) -->
    <div *ngIf="list" class="modal fade show d-block" tabindex="-1" (click)="list = null">
      <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Students of {{ list.sponsor.name }} <small class="text-muted">{{ list.sponsor.user_id }}</small></h5>
            <button type="button" class="close" (click)="list = null">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="list.loading" class="text-center py-4"><span class="spinner-border spinner-border-sm"></span></div>
            <div *ngIf="!list.loading" class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead class="thead-light"><tr><th>Student ID</th><th>Name</th><th>Session year</th><th>Chapter</th><th>Study status</th><th>Account</th></tr></thead>
                <tbody>
                  <tr *ngIf="!list.students.length"><td colspan="6" class="text-center text-muted">No students mapped to this sponsor.</td></tr>
                  <tr *ngFor="let st of list.students">
                    <td><strong>{{ st.user_id }}</strong></td>
                    <td>{{ st.name }}<div *ngIf="st.on_behalf_text" class="small text-info">{{ st.on_behalf_text }}</div></td>
                    <td>{{ st.year || '-' }}</td>
                    <td>{{ st.chapter_name || '-' }}</td>
                    <td>{{ studyLabel(st.study_status) }}</td>
                    <td>{{ st.status || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <small class="text-muted mr-auto">{{ list.students.length }} student{{ list.students.length === 1 ? '' : 's' }}</small>
            <a *ngIf="canManage" [routerLink]="['/', section, 'sponsorships', list.sponsor.id, 'map']" class="btn btn-primary">
              <i class="mdi mdi-account-arrow-right"></i> Map Students</a>
            <button type="button" class="btn btn-secondary" (click)="list = null">Close</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="uploading" class="loading-overlay">
      <div class="spinner-border text-success" role="status" style="width: 4rem; height: 4rem;"></div>
      <h3 class="mt-3 text-dark">Processing Sponsor Data...</h3>
      <p class="text-muted">Please wait while the system updates the records.</p>
    </div>

    <!-- Fee schedules (installments) of the sponsor's students -->
    <div *ngIf="fee" class="modal fade show d-block" tabindex="-1" (click)="fee = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Fee schedule: {{ fee.sponsor.name }}</h5><button type="button" class="close" (click)="fee = null">&times;</button></div>
          <div class="modal-body">
            <p class="text-muted small">
              Each student's installments are built from their course (semesters), the Payment Config of their session year and their
              payment start date. Generating creates what is missing and updates unpaid installments; paid ones never change.
            </p>
            <div *ngIf="fee.message" class="alert py-2" [ngClass]="fee.error ? 'alert-danger' : 'alert-success'">{{ fee.message }}</div>
            <div *ngIf="fee.loading" class="text-center my-3"><span class="spinner-border spinner-border-sm"></span></div>
            <div class="table-responsive" *ngIf="!fee.loading">
              <table class="table table-sm table-centered mb-0">
                <thead><tr><th>Student</th><th>Session year</th><th>Payments start</th><th>Installments</th><th>Status</th></tr></thead>
                <tbody>
                  <tr *ngIf="!fee.rows.length"><td colspan="5" class="text-center text-muted">No students are mapped to this sponsor.</td></tr>
                  <tr *ngFor="let r of fee.rows">
                    <td><strong>{{ r.student_name }}</strong><div class="small text-muted">{{ r.student_code }}</div></td>
                    <td>{{ r.session_year || '--' }}</td>
                    <td>{{ r.payment_start_date ? (r.payment_start_date | appDate) : '--' }}</td>
                    <td>
                      <strong>{{ r.installments }}</strong>
                      <div *ngIf="r.installments" class="small text-muted">
                        {{ r.paid }} paid · {{ r.due }} due<span *ngIf="r.overdue" class="text-danger font-weight-bold"> · {{ r.overdue }} overdue</span>
                      </div>
                    </td>
                    <td>
                      <span *ngIf="!r.problem" class="badge badge-success">Ready</span>
                      <span *ngIf="r.problem" class="text-danger small">{{ r.problem }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <small class="text-muted mr-auto" *ngIf="hasBlocked">Fix what is missing in the Student Directory (Academic tab), then generate again.</small>
            <button type="button" class="btn btn-light" (click)="fee = null">Close</button>
            <button *ngIf="canGenerateFeeSchedule" type="button" class="btn btn-success" (click)="generateFeeSchedule()" [disabled]="fee.busy || !fee.rows.length">
              <i class="mdi mdi-calendar-sync mr-1"></i>{{ fee.busy ? 'Generating…' : 'Generate fee schedule' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Yearly statement of one sponsor -->
    <div *ngIf="statement" class="modal fade show d-block" tabindex="-1" (click)="statement = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Yearly statement: {{ statement.sponsor.name }}</h5><button type="button" class="close" (click)="statement = null">&times;</button></div>
          <div class="modal-body">
            <p class="text-muted small">Installments due, paid and outstanding for the calendar year, with each student's latest progress report.</p>
            <label for="sy">Year</label>
            <select id="sy" class="form-control" [(ngModel)]="statement.year">
              <option *ngFor="let y of years" [ngValue]="y">{{ y }}</option>
            </select>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-primary" (click)="downloadStatement()" [disabled]="statement.busy"><i class="mdi mdi-download mr-1"></i>Download PDF</button>
            <button *ngIf="canEmailStatement" type="button" class="btn btn-primary" (click)="emailStatement()" [disabled]="statement.busy">
              <i class="mdi mdi-email-send-outline mr-1"></i>{{ statement.busy ? 'Working…' : 'Email to sponsor' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminSponsorshipsComponent implements OnInit, OnDestroy {
  /** Area this page is shown in ('admin' or 'office'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';

  /** Office Coordinator view: no contact info, no profile editing, no bulk upload - only Map Students. */
  /** Bulk upload of sponsors: Manage on Sponsorships. */
  get canManage(): boolean { return this.auth.can('SPONSORSHIPS', 'EDIT'); }
  /** Sponsors' contact info and chapter: View on Sponsor details (everyone sees name and user ID). */
  get showDetails(): boolean { return this.auth.can('SPONSOR_DETAILS'); }
  /** Edit Profile: Manage on Sponsor details. */
  get canEditProfile(): boolean { return this.auth.can('SPONSOR_DETAILS', 'EDIT'); }
  /** CSV bulk upload is switched off (core/models/features.ts). */
  readonly bulkUpload = FEATURES.bulkUpload;
  search = '';
  readonly table = new ServerTable<SponsorRow>(
    (p) => this.api.get<PagedResult<SponsorRow>>('/admin/sponsorships', { q: this.search, ...p }),
    (e) => (this.error = errorText(e, 'Could not load sponsors.')), 10);
  message = '';
  error = '';

  showUpload = false;
  file: File | null = null;
  uploading = false;

  edit: { id: number; name: string; careOf: string; email: string; phone: string; chapterId: number | null; coachingCenterIds: number[] } | null = null;
  /** Coaching centers to tick on Edit Sponsor (null until loaded, or when the user can't see them). */
  centers: RccCenter[] | null = null;

  hasCenter(id: number | undefined): boolean { return id != null && !!this.edit?.coachingCenterIds.includes(id); }

  toggleCenter(id: number | undefined): void {
    if (!this.edit || id == null) return;
    const ids = this.edit.coachingCenterIds;
    this.edit.coachingCenterIds = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
  }
  editError = '';
  chapters: Chapter[] = [];
  editName = '';
  /** Edit Sponsor > Student preferences, and the choices for it (loaded the first time the pop-up opens). */
  prefs: SponsorPreferences = preferencesFrom(null);
  prefOptions: PreferenceOptions | null = null;
  students: SponsorStudent[] = [];
  /** The popup listing one sponsor's students. */
  list: { sponsor: { id: number; user_id: string; name: string }; students: SponsorStudent[]; loading: boolean } | null = null;
  saving = false;

  readonly lastYear = new Date().getFullYear() - 1;
  readonly years = Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i);
  statement: { sponsor: { id: number; name: string }; year: number; busy: boolean } | null = null;

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService, private statuses: StudyStatusService) {}

  studyLabel(code: string | null | undefined): string { return code ? this.statuses.label(code) : '-'; }

  /** The popup with the sponsor's students (anyone who may view Sponsorships). */
  showStudents(s: { id: number; user_id: string; name: string }): void {
    this.list = { sponsor: s, students: [], loading: true };
    this.api.get<{ students: SponsorStudent[] }>(`/admin/sponsors/${s.id}`).subscribe({
      next: (r) => { if (this.list) { this.list.students = r.students ?? []; this.list.loading = false; } },
      error: (e) => { this.list = null; this.error = errorText(e, "Could not load the sponsor's students."); }
    });
  }

  get canEmailStatement(): boolean { return this.auth.can('SPONSORSHIPS', 'EDIT'); }

  // ---------------- fee schedules (Payment records permission: view to see, edit to generate)

  fee: { sponsor: { id: number; name: string }; rows: FeeRow[]; loading: boolean; busy: boolean; message: string; error: boolean } | null = null;

  get canSeeFeeSchedule(): boolean { return this.auth.can('PAYMENT_RECORDS'); }
  get canGenerateFeeSchedule(): boolean { return this.auth.can('PAYMENT_RECORDS', 'EDIT'); }
  get hasBlocked(): boolean { return !!this.fee?.rows.some((r) => r.problem); }

  openFeeSchedule(s: { id: number; name: string }): void {
    const fee = { sponsor: s, rows: [] as FeeRow[], loading: true, busy: false, message: '', error: false };
    this.fee = fee;
    this.api.get<FeeRow[]>(`/admin/sponsors/${s.id}/fee-schedule`).subscribe({
      next: (rows) => { fee.loading = false; fee.rows = rows; },
      error: (e) => { fee.loading = false; fee.error = true; fee.message = errorText(e, 'Could not load the fee schedules.'); }
    });
  }

  generateFeeSchedule(): void {
    const fee = this.fee;
    if (!fee) return;
    fee.busy = true;
    fee.message = '';
    this.api.post<{ message: string; rows: FeeRow[] }>(`/admin/sponsors/${fee.sponsor.id}/fee-schedule`, {}).subscribe({
      next: (r) => { fee.busy = false; fee.error = false; fee.message = r.message; fee.rows = r.rows; },
      error: (e) => { fee.busy = false; fee.error = true; fee.message = errorText(e, 'Could not generate the fee schedules.'); }
    });
  }

  downloadStatement(): void {
    const st = this.statement;
    if (!st) return;
    st.busy = true;
    this.api.download(`/admin/sponsors/${st.sponsor.id}/statement`, { year: st.year }, `Rahbar_statement_${st.year}.pdf`).subscribe({
      next: () => (st.busy = false),
      error: (e) => { st.busy = false; this.error = errorText(e, 'Could not create the statement.'); }
    });
  }

  emailStatement(): void {
    const st = this.statement;
    if (!st || !confirm(`Email the ${st.year} statement to ${st.sponsor.name}?`)) return;
    st.busy = true;
    this.api.post<{ sent: boolean; message: string }>(`/admin/sponsors/${st.sponsor.id}/statement/email?year=${st.year}`, {}).subscribe({
      next: (r) => { this.statement = null; if (r.sent) this.message = r.message; else this.error = r.message; },
      error: (e) => { st.busy = false; this.error = errorText(e, 'Could not email the statement.'); }
    });
  }



  /** Active chapters, plus the given one when it is inactive. */
  chapterOptions(currentId: number | null | undefined): Chapter[] {
    return ChapterService.options(this.chapters, currentId);
  }

  ngOnInit(): void {
    this.load();
    if (this.canEditProfile) this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
  }

  load(): void { this.table.load(); }

  ngOnDestroy(): void { this.table.destroy(); }

  /** Result of the last bulk upload (shown in a results window). */
  report: BulkReport | null = null;

  template(): void {
    this.api.download('/admin/templates/sponsors', undefined, 'sponsors_template.csv').subscribe({
      error: (e) => (this.error = errorText(e, 'Could not download the template.'))
    });
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload(): void {
    if (!this.canManage || !this.file) return;
    const form = new FormData();
    form.append('file', this.file);
    this.showUpload = false;
    this.uploading = true;
    this.api.post<{ message: string; report?: BulkReport }>('/admin/sponsors/bulk-upload', form).subscribe({
      next: (r) => { this.uploading = false; this.file = null; this.message = r.message; this.report = r.report ?? null; this.load(); },
      error: (e) => { this.uploading = false; this.error = errorText(e, 'Upload failed.'); }
    });
  }

  openEdit(id: number): void {
    if (!this.canEditProfile) return;
    if (!this.prefOptions) this.api.get<PreferenceOptions>('/sponsor-applications/preference-options').subscribe({ next: (o) => (this.prefOptions = o) });
    if (!this.centers) this.api.get<RccCenter[]>('/admin/rcc-centers').subscribe({ next: (c) => (this.centers = c), error: () => (this.centers = null) });
    this.api.get<{ profile: Record<string, any>; students: SponsorStudent[] }>(`/admin/sponsors/${id}`).subscribe({
      next: (res) => {
        const p = res.profile;
        this.prefs = preferencesFrom(p as Record<string, unknown>);
        this.editName = p['name'] ?? '';
        this.editError = '';
        this.edit = { id, name: p['name'] ?? '', careOf: p['care_of'] ?? '', email: p['email'] ?? '', phone: p['phone'] ?? '', chapterId: p['chapter_id'] === null || p['chapter_id'] === undefined ? null : Number(p['chapter_id']),
                      coachingCenterIds: ((p['coaching_center_ids'] ?? []) as unknown[]).map(Number) };
        this.students = res.students ?? [];
      },
      error: (e) => (this.error = errorText(e, 'Could not load sponsor details.'))
    });
  }

  saveEdit(): void {
    if (!this.edit) return;
    this.saving = true;
    this.editError = '';
    const { id, coachingCenterIds, ...rest } = this.edit;
    // the centers only when they could be shown (else the sponsor's centers stay as they are)
    const body = this.centers ? { ...rest, coachingCenterIds } : rest;
    this.api.put<{ message: string }>(`/admin/sponsors/${id}`, { ...body, preferences: this.prefs }).subscribe({
      next: () => { this.saving = false; this.edit = null; this.message = 'Profile Updated Successfully!'; this.load(); },
      // Shown inside the pop-up (the page's own alert is hidden behind it).
      error: (e) => { this.saving = false; this.editError = errorText(e, 'Update failed'); }
    });
  }
}
