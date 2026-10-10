import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { ChapterService } from '../core/services/chapter.service';
import { Chapter } from './admin-chapters.component';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BulkReport, BulkReportComponent } from '../shared/bulk-report.component';
import { PagerComponent, pageOf } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface SponsorRow {
  id: number;
  user_id: string;
  name: string;
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
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, BulkReportComponent, CardTableDirective],
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
                <button *ngIf="canManage" type="button" class="btn btn-success waves-effect waves-light" (click)="showUpload = true">
                  <i class="mdi mdi-file-upload"></i> Bulk Upload
                </button>
              </div>
            </div>

            <div class="row dt-toolbar mb-2">
              <div class="col-sm-6">
                <label>Show
                  <select class="form-control form-control-sm" [(ngModel)]="pageSize" (ngModelChange)="page = 1">
                    <option [ngValue]="10">10</option><option [ngValue]="25">25</option><option [ngValue]="50">50</option><option [ngValue]="100000">All</option>
                  </select> entries</label>
              </div>
              <div class="col-sm-6 text-sm-right">
                <label>Search:<input type="search" class="form-control form-control-sm" [(ngModel)]="search" (ngModelChange)="page = 1"></label>
              </div>
            </div>

            <div class="table-responsive">
              <table class="table table-bordered table-striped nowrap" style="width:100%">
                <thead><tr><th>Sponsor Name</th><th>Students</th><th *ngIf="showDetails">Contact Info</th><th *ngIf="showDetails">Chapter</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!filtered.length"><td [attr.colspan]="showDetails ? 5 : 3" class="text-center text-muted">No matching records found</td></tr>
                  <tr *ngFor="let s of rows">
                    <td><strong>{{ s.name }}</strong><br><small class="text-muted">ID: {{ s.user_id }}</small></td>
                    <td><span class="badge badge-light border">{{ s.student_count }}</span></td>
                    <td *ngIf="showDetails">
                      <span><i class="mdi mdi-email-outline"></i> {{ s.email || '-' }}</span><br>
                      <span><i class="mdi mdi-phone"></i> {{ s.phone || '-' }}</span>
                    </td>
                    <td *ngIf="showDetails">{{ s.chapter_name }}</td>
                    <td>
                      <div class="btn-group">
                        <button *ngIf="canEditProfile" class="btn btn-sm btn-info" (click)="openEdit(s.id)"><i class="mdi mdi-pencil"></i> Edit Profile</button>
                        <a [routerLink]="['/', section, 'sponsorships', s.id, 'map']" class="btn btn-sm btn-primary waves-effect waves-light">
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
            <app-pager [total]="filtered.length" [page]="page" [pageSize]="pageSize" (pageChange)="page = $event"></app-pager>
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
              <div class="col-md-6 form-group"><label>Email Address</label><input type="email" class="form-control" [(ngModel)]="edit.email"></div>
              <div class="col-md-6 form-group"><label>Phone (Mobile 1)</label><input type="text" class="form-control" [(ngModel)]="edit.phone"></div>
              <div class="col-md-6 form-group"><label>Chapter</label>
                <select class="form-control" [(ngModel)]="edit.chapterId">
                  <option [ngValue]="null">No chapter</option>
                  <option *ngFor="let c of chapterOptions(edit.chapterId)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                </select>
              </div>
            </div>
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
                    <td>{{ r.payment_start_date ? (r.payment_start_date | date: 'd MMM yyyy') : '--' }}</td>
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
            <p class="text-muted small">Installments due, payments made and outstanding amounts for the calendar year, with each student's latest progress report.</p>
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
export class AdminSponsorshipsComponent implements OnInit {
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
  sponsors: SponsorRow[] = [];
  search = '';
  page = 1;
  pageSize = 10;
  message = '';
  error = '';

  showUpload = false;
  file: File | null = null;
  uploading = false;

  edit: { id: number; name: string; email: string; phone: string; chapterId: number | null } | null = null;
  editError = '';
  chapters: Chapter[] = [];
  editName = '';
  students: SponsorStudent[] = [];
  saving = false;

  readonly lastYear = new Date().getFullYear() - 1;
  readonly years = Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i);
  statement: { sponsor: { id: number; name: string }; year: number; busy: boolean } | null = null;

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService) {}

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

  load(): void {
    this.api.get<SponsorRow[]>('/admin/sponsorships').subscribe({
      next: (s) => (this.sponsors = s),
      error: (e) => (this.error = errorText(e, 'Could not load sponsors.'))
    });
  }

  /** Same as the DataTable's global search: any cell containing the text. */
  get filtered(): SponsorRow[] {
    const f = this.search.trim().toLowerCase();
    if (!f) return this.sponsors;
    return this.sponsors.filter((s) =>
      [s.name, s.user_id, s.email, s.phone, s.chapter_name].some((v) => (v ?? '').toLowerCase().includes(f)));
  }
  get rows(): SponsorRow[] { return pageOf(this.filtered, this.page, this.pageSize); }

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
    this.api.get<{ profile: Record<string, string | null>; students: SponsorStudent[] }>(`/admin/sponsors/${id}`).subscribe({
      next: (res) => {
        const p = res.profile;
        this.editName = p['name'] ?? '';
        this.editError = '';
        this.edit = { id, name: p['name'] ?? '', email: p['email'] ?? '', phone: p['phone'] ?? '', chapterId: p['chapter_id'] === null || p['chapter_id'] === undefined ? null : Number(p['chapter_id']) };
        this.students = res.students ?? [];
      },
      error: (e) => (this.error = errorText(e, 'Could not load sponsor details.'))
    });
  }

  saveEdit(): void {
    if (!this.edit) return;
    this.saving = true;
    this.editError = '';
    const { id, ...body } = this.edit;
    this.api.put<{ message: string }>(`/admin/sponsors/${id}`, body).subscribe({
      next: () => { this.saving = false; this.edit = null; this.message = 'Profile Updated Successfully!'; this.load(); },
      // Shown inside the pop-up (the page's own alert is hidden behind it).
      error: (e) => { this.saving = false; this.editError = errorText(e, 'Update failed'); }
    });
  }
}
