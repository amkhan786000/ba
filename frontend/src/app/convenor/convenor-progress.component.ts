import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { uploadUrl } from '../shared/format';
import { ProgressReviewComponent } from '../shared/review/progress-review.component';

interface Progress { progress_id: number; review_status?: string | null; review_comment?: string | null; grantee_name: string; marks: number | string | null; session: string | null; year: string | number | null; file_path: string | null; created_at: string | null }

/** Port of templates/convenor/view_student_progress.html */
@Component({
  selector: 'app-convenor-progress',
  standalone: true,
  imports: [CommonModule, AlertsComponent, ProgressReviewComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Student Progress</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Student Progress</h4>
            <div class="mt-3 table-responsive">
              <h5>Progress for All Student in {{ region }}</h5>
              <table class="table table-striped">
                <thead><tr><th>Student Name</th><th>Marks</th><th>Session</th><th>Year</th><th>File</th><th>Date</th><th>Review</th></tr></thead>
                <tbody>
                  <tr *ngIf="!rows.length"><td colspan="7" class="text-center text-muted">No progress uploaded yet.</td></tr>
                  <tr *ngFor="let p of rows">
                    <td>{{ p.grantee_name }}</td>
                    <td [ngClass]="low(p.marks) ? 'text-danger' : 'text-success'">{{ p.marks }}</td>
                    <td>{{ p.session }}</td>
                    <td>{{ p.year }}</td>
                    <td><a *ngIf="p.file_path; else noFile" [href]="file(p.file_path)" target="_blank">View File</a><ng-template #noFile>No file uploaded</ng-template></td>
                    <td>{{ p.created_at ? (p.created_at | date: 'yyyy-MM-dd HH:mm:ss') : '' }}</td>
                    <td><app-progress-review [row]="p" [canReview]="true"></app-progress-review></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ConvenorProgressComponent implements OnInit {
  rows: Progress[] = [];
  region = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<Progress[]>('/convenor/student-progress').subscribe({
      next: (r) => (this.rows = r),
      error: (e) => (this.error = errorText(e, 'Could not load student progress.'))
    });
    this.api.get<{ convenor: { region: string | null } }>('/convenor/dashboard').subscribe({
      next: (d) => (this.region = d.convenor.region ?? ''),
      error: () => { /* region missing: shown on the dashboard */ }
    });
  }

  low(marks: unknown): boolean { return parseFloat(String(marks ?? 0)) < 50; }
  file(p: string): string { return uploadUrl(p) ?? '#'; }
}
