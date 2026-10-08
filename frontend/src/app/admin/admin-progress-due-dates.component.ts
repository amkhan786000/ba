import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface DueDate {
  due_id: number; title: string; due_date: string; note: string | null;
  submitted: number; pending: number; window_start: string;
}

interface Form { dueId: number | null; title: string; dueDate: string; note: string }

/**
 * Admin > Progress Due Dates: the dates students must upload their progress report (marks sheet) by.
 * Students get a reminder two weeks before each date and, until they upload, after it; students missing
 * bank details get a monthly reminder too. Reminders go out every morning; "Send reminders now" runs them at once.
 */
@Component({
  selector: 'app-admin-progress-due-dates',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
          <h4 class="page-title mb-2 mb-md-0">Progress Report Due Dates</h4>
          <div *ngIf="canEdit">
            <button class="btn btn-outline-primary mr-2" (click)="runReminders()" [disabled]="running">
              <i class="mdi mdi-send mr-1"></i>{{ running ? 'Sending…' : 'Send reminders now' }}
            </button>
            <button class="btn btn-primary" (click)="edit()"><i class="mdi mdi-plus mr-1"></i>Add due date</button>
          </div>
        </div>
      </div>
    </div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="form">
      <div class="card-body">
        <h4 class="header-title mb-3">{{ form.dueId ? 'Edit due date' : 'New due date' }}</h4>
        <div class="row">
          <div class="col-md-4 form-group">
            <label for="title">Title</label>
            <input id="title" class="form-control" [(ngModel)]="form.title" maxlength="150" placeholder="e.g. Semester 1 results">
          </div>
          <div class="col-md-3 form-group">
            <label for="dueDate">Due date</label>
            <input id="dueDate" type="date" class="form-control" [(ngModel)]="form.dueDate">
          </div>
          <div class="col-md-5 form-group">
            <label for="note">Note for students <span class="text-muted">(optional)</span></label>
            <input id="note" class="form-control" [(ngModel)]="form.note" maxlength="500" placeholder="Shown in the reminder">
          </div>
        </div>
        <button class="btn btn-primary mr-2" (click)="save()" [disabled]="saving || !form.title.trim() || !form.dueDate">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
        <button class="btn btn-light" (click)="form = null">Cancel</button>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <p class="text-muted">
          Current students (studying, not on hold) upload a progress report for each due date. A report counts for a date
          when it was uploaded after the previous due date. Reminders go out every morning: two weeks before a date,
          and after it until the report is uploaded (for up to 60 days). Students without bank details get a monthly reminder.
        </p>
        <div class="table-responsive">
          <table class="table table-centered mb-0">
            <thead><tr><th>Due date</th><th>Title</th><th>Counts uploads from</th><th>Submitted</th><th>Pending</th><th>Note</th><th *ngIf="canEdit"></th></tr></thead>
            <tbody>
              <tr *ngIf="loading"><td colspan="7" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !rows.length"><td colspan="7" class="text-center text-muted">No due dates yet.</td></tr>
              <tr *ngFor="let d of rows" [class.table-light]="isPast(d)">
                <td>
                  <strong>{{ d.due_date | date: 'd MMM yyyy' }}</strong>
                  <div class="small" [ngClass]="isPast(d) ? 'text-muted' : 'text-primary'">{{ when(d) }}</div>
                </td>
                <td>{{ d.title }}</td>
                <td>{{ d.window_start | date: 'd MMM yyyy' }}</td>
                <td><span class="badge badge-success">{{ d.submitted }}</span></td>
                <td><span class="badge" [ngClass]="d.pending ? (isPast(d) ? 'badge-danger' : 'badge-warning') : 'badge-light'">{{ d.pending }}</span></td>
                <td class="small">{{ d.note || '' }}</td>
                <td *ngIf="canEdit" class="text-nowrap">
                  <button class="btn btn-sm btn-primary" (click)="edit(d)">Edit</button>
                  <button class="btn btn-sm btn-danger ml-1" (click)="remove(d)">Delete</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class AdminProgressDueDatesComponent implements OnInit {
  rows: DueDate[] = [];
  form: Form | null = null;
  loading = false;
  saving = false;
  running = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('PROGRESS_DUE_DATES', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<DueDate[]>('/admin/progress-due-dates').subscribe({
      next: (r) => { this.loading = false; this.rows = r; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the due dates.'); }
    });
  }

  edit(d?: DueDate): void {
    this.form = d
      ? { dueId: d.due_id, title: d.title, dueDate: d.due_date, note: d.note ?? '' }
      : { dueId: null, title: '', dueDate: '', note: '' };
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    this.error = '';
    this.api.post('/admin/progress-due-dates', this.form).subscribe({
      next: () => { this.saving = false; this.form = null; this.message = 'Due date saved.'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the due date.'); }
    });
  }

  remove(d: DueDate): void {
    if (!confirm(`Delete the due date "${d.title}"? Students will no longer be reminded about it.`)) return;
    this.api.delete(`/admin/progress-due-dates/${d.due_id}`).subscribe({
      next: () => { this.message = 'Due date deleted.'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the due date.'))
    });
  }

  runReminders(): void {
    this.running = true;
    this.error = '';
    this.api.post<{ progressUpcoming: number; progressOverdue: number; bankDetailsMissing: number }>(
      '/admin/progress-due-dates/reminders/run', {}).subscribe({
      next: (r) => {
        this.running = false;
        const total = r.progressUpcoming + r.progressOverdue + r.bankDetailsMissing;
        this.message = total
          ? `Reminders sent: ${r.progressUpcoming} upcoming, ${r.progressOverdue} overdue, ${r.bankDetailsMissing} missing bank details.`
          : 'No new reminders to send (students are reminded once per due date and once a month for bank details).';
      },
      error: (e) => { this.running = false; this.error = errorText(e, 'Could not send reminders.'); }
    });
  }

  isPast(d: DueDate): boolean { return d.due_date < today(); }

  when(d: DueDate): string {
    const days = Math.round((new Date(d.due_date + 'T00:00').getTime() - new Date(today() + 'T00:00').getTime()) / 86400000);
    if (days === 0) return 'Today';
    return days > 0 ? `In ${days} day(s)` : `${-days} day(s) ago`;
  }
}

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
