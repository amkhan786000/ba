import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { uploadUrl } from '../shared/format';

interface Progress { year: string | number | null; session: string | null; marks: string | number | null; file_path: string | null; created_at: string | null }

/** Port of templates/student/student_progress.html */
@Component({
  selector: 'app-student-progress',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Academic Progress</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12 col-md-4">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Submit New Result</h4>
            <p class="text-muted">Upload your latest marksheet here.</p>
            <form #f="ngForm" (ngSubmit)="submit(f)">
              <div class="form-group">
                <label for="year">Year (e.g., 1, 2, 3)</label>
                <input type="number" class="form-control" id="year" name="year" placeholder="Current Year" [(ngModel)]="form.year" required>
              </div>
              <div class="form-group">
                <label for="session">Academic Session</label>
                <input type="text" class="form-control" id="session" name="session" placeholder="e.g. 2024-2025" [(ngModel)]="form.session" required>
              </div>
              <div class="form-group">
                <label for="marks">Marks / CGPA</label>
                <input type="text" maxlength="4" class="form-control" id="marks" name="marks" placeholder="e.g. 85.5" [(ngModel)]="form.marks" required>
                <small class="text-muted">Max 4 characters allowed.</small>
              </div>
              <div class="form-group">
                <label for="file">Upload Marksheet (PDF/JPG)</label>
                <input type="file" class="form-control-file" id="file" (change)="file = fileOf($event)" required>
              </div>
              <button type="submit" class="btn btn-primary btn-block waves-effect waves-light" [disabled]="f.invalid || !file || saving">Submit Progress</button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-md-8">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Submission History</h4>
            <div class="table-responsive mt-4">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>Year</th><th>Session</th><th>Marks</th><th>File</th><th>Date Submitted</th></tr></thead>
                <tbody>
                  <tr *ngIf="!rows.length"><td colspan="5" class="text-center text-muted">No progress data found.</td></tr>
                  <tr *ngFor="let p of rows">
                    <td>Year {{ p.year }}</td>
                    <td>{{ p.session }}</td>
                    <td><span class="badge badge-primary">{{ p.marks }}</span></td>
                    <td><a *ngIf="p.file_path" [href]="url(p.file_path)" target="_blank" class="btn btn-xs btn-outline-primary waves-effect waves-light">View File</a></td>
                    <td>{{ p.created_at ? (p.created_at | date: 'yyyy-MM-dd') : '--' }}</td>
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
export class StudentProgressComponent implements OnInit {
  rows: Progress[] = [];
  form = { year: null as number | null, session: '', marks: '' };
  file: File | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Progress[]>('/student/progress').subscribe({
      next: (r) => (this.rows = r),
      error: (e) => (this.error = errorText(e, 'Could not load your progress.'))
    });
  }

  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }
  url(p: string): string { return uploadUrl(p) ?? '#'; }

  submit(f: { resetForm: () => void }): void {
    if (!this.file) return;
    const fd = new FormData();
    fd.append('year', String(this.form.year ?? ''));
    fd.append('session', this.form.session);
    fd.append('marks', this.form.marks);
    fd.append('file', this.file);
    this.saving = true;
    this.api.post<{ message: string }>('/student/progress', fd).subscribe({
      next: (r) => { this.saving = false; this.message = r.message; this.file = null; f.resetForm(); this.form = { year: null, session: '', marks: '' }; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not submit your progress.'); }
    });
  }
}
