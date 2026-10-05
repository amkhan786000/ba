import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { errorText } from '../shared/alerts/alerts.component';
import { AuthLayoutComponent } from '../shared/auth-layout/auth-layout.component';

interface Tracked {
  applicationId: number; name: string; courseApplied: string | null; rccName: string | null; submittedAt: string | null;
  status: string; interviewAt: string | null; interviewVenue: string | null;
  history: { status: string; comments: string | null; date: string | null }[];
  documents: { id: number; docType: string; fileName: string; uploadedAt: string | null }[];
  documentTypes: string[];
}

const STEPS = ['submitted', 'interviewing', 'accepted', 'admitted'];

/** Public "Track my application": status timeline, interview details and document upload. */
@Component({
  selector: 'app-track-application',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  template: `
    <app-auth-layout [heading]="app ? 'Application #' + app.applicationId : 'Track your application'"
                     [subheading]="app ? '' : 'Enter your application number and any mobile number you gave on the form.'"
                     [wide]="true">
      <div *ngIf="justApplied && app" class="alert alert-success">
        <i class="mdi mdi-check-circle-outline mr-1"></i>Application submitted. Note your application number
        <strong>{{ app.applicationId }}</strong> to check its progress later.
      </div>
      <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

      <form *ngIf="!app" (ngSubmit)="lookup()">
        <div class="row">
          <div class="col-sm-5 form-group">
            <label for="appId">Application number</label>
            <input id="appId" class="form-control" name="appId" [(ngModel)]="applicationId" inputmode="numeric" placeholder="e.g. 1024" required>
          </div>
          <div class="col-sm-7 form-group">
            <label for="mobile">Mobile number</label>
            <input id="mobile" class="form-control" name="mobile" type="tel" [(ngModel)]="mobile" placeholder="10-digit mobile" required>
          </div>
        </div>
        <button class="btn btn-primary btn-block" type="submit" [disabled]="loading || !applicationId || !mobile">
          {{ loading ? 'Looking up…' : 'Check status' }}
        </button>
      </form>

      <ng-container *ngIf="app">
        <div class="track-head">
          <div><span>Student</span><strong>{{ app.name }}</strong></div>
          <div><span>Course</span><strong>{{ app.courseApplied || '--' }}</strong></div>
          <div><span>Submitted</span><strong>{{ app.submittedAt ? (app.submittedAt | date: 'd MMM yyyy') : '--' }}</strong></div>
        </div>

        <div class="stepper" [class.rejected]="app.status === 'rejected'">
          <div *ngFor="let s of steps; let i = index" class="step" [class.done]="i <= stepIndex" [class.now]="i === stepIndex">
            <span class="dot"><i class="mdi" [ngClass]="i < stepIndex ? 'mdi-check' : 'mdi-circle-small'"></i></span>
            <small>{{ s }}</small>
          </div>
        </div>
        <p class="text-center mb-3">Current status: <strong class="text-capitalize">{{ app.status }}</strong></p>

        <div *ngIf="app.interviewAt" class="alert alert-info">
          <i class="mdi mdi-calendar-clock mr-1"></i><strong>Interview:</strong>
          {{ app.interviewAt | date: 'EEEE d MMM yyyy, h:mm a' }} · {{ app.interviewVenue }}
        </div>

        <h6 class="mt-4">Documents</h6>
        <p class="small text-muted">Upload your marksheet, ID proof, income certificate and photo (PDF, image or Word).</p>
        <ul class="list-unstyled small mb-3" *ngIf="app.documents.length">
          <li *ngFor="let d of app.documents" class="mb-1"><i class="mdi mdi-paperclip text-muted mr-1"></i><strong>{{ d.docType }}</strong> · {{ d.fileName }}</li>
        </ul>
        <form class="row align-items-end" (ngSubmit)="upload()">
          <div class="col-sm-5 form-group">
            <label for="docType">Type</label>
            <select id="docType" class="form-control" name="docType" [(ngModel)]="docType">
              <option *ngFor="let t of app.documentTypes" [value]="t">{{ t }}</option>
            </select>
          </div>
          <div class="col-sm-7 form-group">
            <label for="docFile">File</label>
            <input id="docFile" type="file" class="form-control-file" accept=".pdf,.jpg,.jpeg,.png,.webp,.doc,.docx" (change)="pick($event)">
          </div>
          <div class="col-12">
            <button class="btn btn-outline-primary btn-block" type="submit" [disabled]="!docFile || uploading">
              <i class="mdi mdi-upload"></i> {{ uploading ? 'Uploading…' : 'Upload document' }}
            </button>
            <div *ngIf="uploaded" class="small text-success mt-2"><i class="mdi mdi-check"></i> {{ uploaded }}</div>
          </div>
        </form>

        <h6 class="mt-4">History</h6>
        <ul class="timeline">
          <li *ngFor="let h of app.history; let first = first" [class.current]="first">
            <strong class="text-capitalize">{{ h.status }}</strong>
            <small class="text-muted ml-1">{{ h.date ? (h.date | date: 'd MMM yyyy') : '' }}</small>
            <p *ngIf="h.comments" class="mb-0 small">{{ h.comments }}</p>
          </li>
        </ul>
        <div class="auth-links center"><a href="#" (click)="reset($event)">Check another application</a></div>
      </ng-container>

      <div class="auth-links center">
        <a routerLink="/apply" *ngIf="!app">Apply for a scholarship</a>
        <a routerLink="/login"><i class="mdi mdi-arrow-left mr-1"></i>Back to sign in</a>
      </div>
    </app-auth-layout>
  `
})
export class TrackApplicationComponent implements OnInit {
  readonly steps = STEPS;
  applicationId = '';
  mobile = '';
  app: Tracked | null = null;
  justApplied = false;
  loading = false;
  error = '';
  docType = 'Marksheet';
  docFile: File | null = null;
  uploading = false;
  uploaded = '';

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.applicationId = q.get('id') ?? '';
    this.mobile = q.get('mobile') ?? '';
    this.justApplied = q.get('new') === '1';
    if (this.applicationId && this.mobile) this.lookup();
  }

  get stepIndex(): number {
    const s = (this.app?.status ?? '').toLowerCase();
    if (s === 'provisional admission letter') return 2;
    const i = STEPS.indexOf(s);
    return i >= 0 ? i : 0;
  }

  lookup(): void {
    this.loading = true;
    this.error = '';
    this.api.post<Tracked>('/public/track-application', { applicationId: this.applicationId, mobile: this.mobile }).subscribe({
      next: (r) => { this.loading = false; this.app = r; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not find that application.'); }
    });
  }

  pick(event: Event): void { this.docFile = (event.target as HTMLInputElement).files?.[0] ?? null; }

  upload(): void {
    if (!this.app || !this.docFile) return;
    const form = new FormData();
    form.append('file', this.docFile);
    form.append('docType', this.docType);
    form.append('mobile', this.mobile);
    this.uploading = true;
    this.uploaded = '';
    this.api.post(`/public/applications/${this.app.applicationId}/documents`, form).subscribe({
      next: () => { this.uploading = false; this.uploaded = this.docType + ' uploaded.'; this.docFile = null; this.lookup(); },
      error: (e) => { this.uploading = false; this.error = errorText(e, 'Could not upload the document.'); }
    });
  }

  reset(event: Event): void {
    event.preventDefault();
    this.app = null;
    this.justApplied = false;
    this.applicationId = '';
    this.mobile = '';
  }
}
