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
  chapters: Chapter[] = [];
  editName = '';
  students: SponsorStudent[] = [];
  saving = false;

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService) {}



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
        this.edit = { id, name: p['name'] ?? '', email: p['email'] ?? '', phone: p['phone'] ?? '', chapterId: p['chapter_id'] === null || p['chapter_id'] === undefined ? null : Number(p['chapter_id']) };
        this.students = res.students ?? [];
      },
      error: (e) => (this.error = errorText(e, 'Could not load sponsor details.'))
    });
  }

  saveEdit(): void {
    if (!this.edit) return;
    this.saving = true;
    const { id, ...body } = this.edit;
    this.api.put<{ message: string }>(`/admin/sponsors/${id}`, body).subscribe({
      next: () => { this.saving = false; this.edit = null; this.message = 'Profile Updated Successfully!'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Update failed'); }
    });
  }
}
