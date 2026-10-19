import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

interface Row {
  id: number; kind_label: string; message: string; status: 'PENDING' | 'APPROVED' | 'RETURNED'; written_at: string;
  reviewed_at: string | null; review_note: string | null; reviewed_by: string | null;
  student_ref_id: string; student_name: string; chapter: string | null; sponsor_id: string | null; sponsor_name: string | null;
}
interface ListBody { rows: Row[]; counts: Record<string, number> }

/** Sponsors & Payments > Notes to sponsors: notes students write to their sponsors; approve (the sponsor gets it) or return with a reason. */
@Component({
  selector: 'app-admin-student-notes',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Notes to Sponsors</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item" *ngFor="let t of tabs"><a class="nav-link" href="" [class.active]="status === t.key" (click)="$event.preventDefault(); status = t.key; load()">
        {{ t.label }} ({{ counts[t.key] || 0 }})</a></li>
    </ul>

    <div class="card"><div class="card-body">
      <p class="text-muted small">Students write short updates or thank-you notes to their sponsors. Check each one (no phone numbers,
        addresses or requests for money outside Rahbar), then approve it so the sponsor sees it and gets an email, or return it with a reason.</p>
      <p *ngIf="!rows.length" class="text-muted mb-0">Nothing here.</p>
      <div *ngFor="let r of rows" class="border rounded p-3 mb-2">
        <div class="d-flex flex-wrap justify-content-between">
          <div><strong>{{ r.student_name }}</strong> <small class="text-muted">{{ r.student_ref_id }}{{ r.chapter ? ' · ' + r.chapter : '' }}</small>
            <div class="small text-muted">To {{ r.sponsor_name || '—' }} ({{ r.sponsor_id }}) · {{ r.kind_label }} · written {{ r.written_at | appDate }}</div></div>
          <div class="small text-muted text-right" *ngIf="r.reviewed_at">{{ r.status === 'APPROVED' ? 'Approved' : 'Returned' }} {{ r.reviewed_at | appDate }}<span *ngIf="r.reviewed_by"> by {{ r.reviewed_by }}</span></div>
        </div>
        <p class="mt-2 mb-2" style="white-space: pre-line">{{ r.message }}</p>
        <p *ngIf="r.status === 'RETURNED'" class="small text-warning mb-0">Reason: {{ r.review_note }}</p>
        <div *ngIf="r.status === 'PENDING' && canEdit" class="d-flex flex-wrap align-items-center">
          <button class="btn btn-sm btn-success mr-2 mb-1" (click)="review(r, 'APPROVE')" [disabled]="busy">Approve</button>
          <input class="form-control form-control-sm mr-2 mb-1" style="max-width: 360px" placeholder="Reason to return it (shown to the student)" [(ngModel)]="reasons[r.id]">
          <button class="btn btn-sm btn-outline-warning mb-1" (click)="review(r, 'RETURN')" [disabled]="busy || !(reasons[r.id] || '').trim()">Return</button>
        </div>
      </div>
    </div></div>
  `
})
export class AdminStudentNotesComponent implements OnInit {
  readonly tabs = [{ key: 'PENDING', label: 'To check' }, { key: 'APPROVED', label: 'Approved' }, { key: 'RETURNED', label: 'Returned' }];
  status = 'PENDING';
  rows: Row[] = [];
  counts: Record<string, number> = {};
  reasons: Record<number, string> = {};
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('STUDENT_NOTES', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<ListBody>('/admin/student-notes', { status: this.status }).subscribe({
      next: (r) => { this.rows = r.rows; this.counts = r.counts; },
      error: (e) => (this.error = errorText(e, 'Could not load the notes.'))
    });
  }

  review(r: Row, decision: 'APPROVE' | 'RETURN'): void {
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/student-notes/${r.id}/review`, { decision, note: this.reasons[r.id] ?? '' }).subscribe({
      next: (res) => { this.busy = false; this.message = res.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }
}
