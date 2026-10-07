import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { uploadUrl } from '../shared/format';
import { APPLICATION_STATUSES, ApplicationRow } from './admin-applications.component';

interface ApplicationDetail extends ApplicationRow {
  user_id: number | null;
  user_code: string | null;
  address: string | null;
  father_profession: string | null;
  mother_profession: string | null;
  student_mobile: string | null;
  father_mobile: string | null;
  mother_mobile: string | null;
  rahbar_alumnus: string | null;
  created_at: string | null;
  status_date: string | null;
  interview_at: string | null;
  interview_venue: string | null;
}
interface HistoryRow { status: string; comments: string | null; date: string | null }
interface DocumentRow { id: number; docType: string; fileName: string; filePath: string; uploadedAt: string | null }
interface Details { application: ApplicationDetail; history: HistoryRow[]; documents: DocumentRow[]; documentTypes: string[] }

/** One application: details, status history, interview scheduling, documents and status update. */
@Component({
  selector: 'app-admin-application-details',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
          <div>
            <a [routerLink]="['/' + section, 'applications']" class="small font-weight-bold"><i class="mdi mdi-arrow-left"></i> Applications</a>
            <h4 class="page-title mt-1">
              {{ app?.name || 'Application' }}
              <span *ngIf="app" class="badge ml-2 align-middle" [ngClass]="badge(app.status)">{{ app.status || 'no status' }}</span>
            </h4>
          </div>
          <span class="text-muted mt-2 mt-md-0" *ngIf="app">Application #{{ app.grantee_detail_id }} · submitted {{ app.created_at ? (app.created_at | date: 'd MMM yyyy') : '--' }}</span>
        </div>
      </div>
    </div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row" *ngIf="app">
      <div class="col-12 col-xl-7">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Applicant</h4>
            <div class="detail-grid">
              <div><span>Student</span><strong>{{ app.name }}</strong></div>
              <div><span>Student mobile</span><strong>{{ app.student_mobile || '--' }}</strong></div>
              <div><span>Course applied</span><strong>{{ app.course_applied || '--' }}</strong></div>
              <div><span>RCC center</span><strong>{{ app.rcc_name || '--' }}</strong></div>
              <div><span>Father</span><strong>{{ app.father_name || '--' }}</strong><small>{{ app.father_profession }} {{ app.father_mobile }}</small></div>
              <div><span>Mother</span><strong>{{ app.mother_name || '--' }}</strong><small>{{ app.mother_profession }} {{ app.mother_mobile }}</small></div>
              <div><span>Annual income</span><strong>{{ app.average_annual_salary != null ? '₹' + (app.average_annual_salary | number: '1.0-2') : 'N/A' }}</strong></div>
              <div><span>Address</span><strong>{{ app.address || '--' }}</strong></div>
              <div><span>Student account</span><strong>{{ app.user_code || 'Not linked yet' }}</strong></div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center">
              <h4 class="header-title mb-0">Documents</h4>
              <span class="text-muted small">{{ docs.length }} file(s)</span>
            </div>
            <div class="doc-list mt-3">
              <p *ngIf="!docs.length" class="text-muted mb-0">No documents uploaded yet.</p>
              <a *ngFor="let d of docs" class="doc-item" [href]="file(d.filePath)" target="_blank" rel="noopener">
                <i class="mdi" [ngClass]="docIcon(d.fileName)"></i>
                <span><strong>{{ d.docType }}</strong><small>{{ d.fileName }} · {{ d.uploadedAt ? (d.uploadedAt | date: 'd MMM yyyy') : '' }}</small></span>
                <i class="mdi mdi-open-in-new ml-auto text-muted"></i>
              </a>
            </div>
            <form class="row align-items-end mt-3" (ngSubmit)="upload()">
              <div class="col-12 col-md-4 form-group mb-md-0">
                <label for="docType">Type</label>
                <select id="docType" class="form-control" name="docType" [(ngModel)]="docType">
                  <option *ngFor="let t of docTypes" [value]="t">{{ t }}</option>
                </select>
              </div>
              <div class="col-12 col-md-5 form-group mb-md-0">
                <label for="docFile">File (PDF, image or Word)</label>
                <input id="docFile" type="file" class="form-control-file" accept=".pdf,.jpg,.jpeg,.png,.webp,.doc,.docx" (change)="pick($event)">
              </div>
              <div class="col-12 col-md-3">
                <button class="btn btn-outline-primary btn-block" type="submit" [disabled]="!docFile || uploading">
                  <i class="mdi mdi-upload"></i> {{ uploading ? 'Uploading…' : 'Upload' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-xl-5">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Update status</h4>
            <form (ngSubmit)="save()">
              <div class="form-group">
                <label for="status">Status</label>
                <select name="status" id="status" class="form-control" [(ngModel)]="status" required>
                  <option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
                </select>
              </div>
              <div class="form-group">
                <label for="comments">Comment</label>
                <textarea name="comments" id="comments" class="form-control" rows="2" [(ngModel)]="comments"></textarea>
              </div>
              <button type="submit" class="btn btn-primary btn-block" [disabled]="saving || !status">Update status</button>
            </form>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Interview</h4>
            <div *ngIf="app.interview_at" class="alert alert-info">
              <i class="mdi mdi-calendar-clock mr-1"></i>
              {{ app.interview_at | date: 'EEEE d MMM yyyy, h:mm a' }} · {{ app.interview_venue }}
            </div>
            <form (ngSubmit)="schedule()">
              <div class="row">
                <div class="col-sm-6 form-group">
                  <label for="interviewAt">Date &amp; time</label>
                  <input id="interviewAt" type="datetime-local" class="form-control" name="interviewAt" [(ngModel)]="interviewAt" required>
                </div>
                <div class="col-sm-6 form-group">
                  <label for="venue">Venue</label>
                  <input id="venue" class="form-control" name="venue" [(ngModel)]="venue" placeholder="Office, address or video link" required>
                </div>
              </div>
              <button class="btn btn-outline-primary btn-block" type="submit" [disabled]="scheduling || !interviewAt || !venue.trim()">
                {{ app.interview_at ? 'Reschedule interview' : 'Schedule interview' }}
              </button>
            </form>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title">History</h4>
            <p *ngIf="!history.length" class="text-muted mb-0">No status changes yet.</p>
            <ul class="timeline">
              <li *ngFor="let h of history; let first = first" [class.current]="first">
                <span class="badge" [ngClass]="badge(h.status)">{{ h.status }}</span>
                <small class="text-muted ml-1">{{ h.date ? (h.date | date: 'd MMM yyyy, HH:mm') : '' }}</small>
                <p *ngIf="h.comments" class="mb-0 mt-1">{{ h.comments }}</p>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminApplicationDetailsComponent implements OnInit {
  @Input() id = '';
  /** Which area this page is shown in ('admin', 'coordinator' or 'convenor'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';
  readonly statuses = APPLICATION_STATUSES;

  app: ApplicationDetail | null = null;
  history: HistoryRow[] = [];
  docs: DocumentRow[] = [];
  docTypes: string[] = [];
  status = '';
  comments = '';
  interviewAt = '';
  venue = '';
  docType = 'Marksheet';
  docFile: File | null = null;
  saving = false;
  scheduling = false;
  uploading = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Details>(`/admin/applications/${this.id}/details`).subscribe({
      next: (d) => {
        this.app = d.application;
        this.history = d.history;
        this.docs = d.documents;
        this.docTypes = d.documentTypes;
        this.status = d.application.status ?? '';
        this.comments = '';
        this.interviewAt = d.application.interview_at ? String(d.application.interview_at).substring(0, 16) : '';
        this.venue = d.application.interview_venue ?? '';
      },
      error: (e) => (this.error = errorText(e, 'Could not load the application.'))
    });
  }

  badge(status: string | null): string {
    const s = (status ?? '').toLowerCase();
    if (s.includes('accepted') || s.includes('admitted')) return 'badge-success';
    if (s.includes('rejected')) return 'badge-danger';
    if (s.includes('submitted') || s.includes('interviewing')) return 'badge-primary';
    if (s.includes('on hold')) return 'badge-warning';
    return 'badge-info';
  }

  save(): void {
    this.saving = true;
    this.api.post<{ message: string }>(`/admin/applications/${this.id}/status`, { status: this.status, comments: this.comments }).subscribe({
      next: () => { this.saving = false; this.message = 'Application status updated.'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Error updating application.'); }
    });
  }

  schedule(): void {
    this.scheduling = true;
    this.api.post<{ message: string }>(`/admin/applications/${this.id}/interview`, { interviewAt: this.interviewAt, venue: this.venue }).subscribe({
      next: () => { this.scheduling = false; this.message = 'Interview scheduled.'; this.load(); },
      error: (e) => { this.scheduling = false; this.error = errorText(e, 'Could not schedule the interview.'); }
    });
  }

  pick(event: Event): void { this.docFile = (event.target as HTMLInputElement).files?.[0] ?? null; }

  upload(): void {
    if (!this.docFile) return;
    const form = new FormData();
    form.append('file', this.docFile);
    form.append('docType', this.docType);
    this.uploading = true;
    this.api.post(`/admin/applications/${this.id}/documents`, form).subscribe({
      next: () => { this.uploading = false; this.docFile = null; this.message = 'Document uploaded.'; this.load(); },
      error: (e) => { this.uploading = false; this.error = errorText(e, 'Could not upload the document.'); }
    });
  }

  file(path: string): string { return uploadUrl(path) ?? '#'; }

  docIcon(name: string): string {
    const n = (name ?? '').toLowerCase();
    if (n.endsWith('.pdf')) return 'mdi-file-pdf-outline';
    if (/\.(jpe?g|png|webp)$/.test(n)) return 'mdi-file-image-outline';
    if (/\.docx?$/.test(n)) return 'mdi-file-word-outline';
    return 'mdi-file-outline';
  }
}
