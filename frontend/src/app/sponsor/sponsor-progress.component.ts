import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { uploadUrl } from '../shared/format';
import { ProgressReviewComponent } from '../shared/review/progress-review.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';

interface Progress {
  progress_id: number; review_status?: string | null; review_comment?: string | null; reviewed_at?: string | null;
  grantee_name: string; grantee_id: string | null; marks: string | number | null;
  session: string | null; year: string | number | null; file_path: string | null; created_at: string | null;
}

/** Port of templates/sponsor/student_progress.html */
@Component({
  selector: 'app-sponsor-progress',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, ProgressReviewComponent, PagerComponent, PaginatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Student Progress Reports</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Academic Records</h4>
            <p class="text-muted">Review the marks and uploaded progress files of your beneficiaries. Approve a report, or send it back with a comment.</p>
            <div class="mb-3" *ngIf="pending"><span class="badge badge-warning">{{ pending }} awaiting your review</span></div>
            <form class="mb-4" (ngSubmit)="apply()">
              <div class="row">
                <div class="col-12 col-md-3 mb-2"><input type="text" name="name" class="form-control" placeholder="Student Name" [(ngModel)]="f.name"></div>
                <div class="col-12 col-md-2 mb-2"><input type="text" name="ref" class="form-control" placeholder="Student ID" [(ngModel)]="f.ref"></div>
                <div class="col-12 col-md-2 mb-2"><input type="text" name="session" class="form-control" placeholder="Session" [(ngModel)]="f.session"></div>
                <div class="col-12 col-md-2 mb-2"><input type="text" name="year" class="form-control" placeholder="Year" [(ngModel)]="f.year"></div>
                <div class="col-12 col-md-3">
                  <button type="submit" class="btn btn-primary mr-1 waves-effect waves-light">Search</button>
                  <button type="button" class="btn btn-secondary waves-effect waves-light" (click)="clear()">Clear</button>
                </div>
              </div>
            </form>
            <div class="table-responsive">
              <table class="table table-centered table-striped mb-0">
                <thead><tr><th>Student Name</th><th>Student ID</th><th>Progress (%)</th><th>Session</th><th>Year</th><th>File</th><th>Uploaded On</th><th>Review</th></tr></thead>
                <tbody>
                  <tr *ngIf="!shown.length"><td colspan="8" class="text-center text-muted">{{ rows.length ? 'No matching records found.' : 'No progress data found.' }}</td></tr>
                  <tr *ngFor="let p of shown | paginate: pg.page : pg.size">
                    <td><strong>{{ p.grantee_name }}</strong></td>
                    <td><span class="badge badge-light border">{{ p.grantee_id }}</span></td>
                    <td>{{ p.marks }}%</td>
                    <td>{{ p.session }}</td>
                    <td>{{ p.year }}</td>
                    <td><a *ngIf="p.file_path" [href]="file(p.file_path)" target="_blank" class="btn btn-xs btn-outline-info">View File</a></td>
                    <td>{{ p.created_at ? (p.created_at | date: 'yyyy-MM-dd') : 'N/A' }}</td>
                    <td><app-progress-review [row]="p" [canReview]="true"></app-progress-review></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="shown.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SponsorProgressComponent implements OnInit {
  readonly pg = new PageState();
  rows: Progress[] = [];
  shown: Progress[] = [];
  f = { name: '', ref: '', session: '', year: '' };
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<Progress[]>('/sponsor/student-progress').subscribe({
      next: (r) => { this.rows = r; this.shown = r; },
      error: (e) => (this.error = errorText(e, 'Could not load student progress.'))
    });
  }

  apply(): void {
    const has = (v: unknown, q: string) => !q.trim() || String(v ?? '').toLowerCase().includes(q.trim().toLowerCase());
    this.shown = this.rows.filter((p) => has(p.grantee_name, this.f.name) && has(p.grantee_id, this.f.ref) && has(p.session, this.f.session) && has(p.year, this.f.year));
  }

  get pending(): number { return this.rows.filter((p) => !p.review_status || p.review_status === 'Pending').length; }

  clear(): void { this.f = { name: '', ref: '', session: '', year: '' }; this.shown = this.rows; }

  file(p: string): string { return uploadUrl(p) ?? '#'; }
}
