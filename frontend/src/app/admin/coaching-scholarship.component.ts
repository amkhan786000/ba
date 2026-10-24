import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { FunnelChartComponent, Stage } from '../shared/charts/funnel-chart.component';
import { CoachingCenter, CoachingScope } from './coaching-students.component';

interface Row {
  id: number; name: string; father_name: string | null; center_name: string | null; guardian_mobile: string | null;
  student_mobile: string | null; email: string | null; average_percent: number | null; exams_sat: number; exams_held: number;
  attendance_percent: number | null; invited_at: string | null; invite_method: string | null; invited_by: string | null;
  application_id: number | null; application_status: string | null; stage: 'READY' | 'INVITED' | 'APPLIED' | 'ACCEPTED';
}
type Answer = CoachingScope & { applications_open: boolean; apply_url: string; rows: Row[]; funnel: Stage[] };

/**
 * Coaching Center > Ready for Scholarship: Class 10 coaching students, best exam results first, to invite to apply
 * for the Rahbar scholarship (call them, or email when they have an address), and whether they then applied.
 */
@Component({
  selector: 'app-coaching-scholarship',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, FunnelChartComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Ready for Scholarship</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ng-container *ngIf="d">
      <div class="alert" [ngClass]="d.applications_open ? 'alert-success' : 'alert-light border'">
        <ng-container *ngIf="d.applications_open">Scholarship applications are <strong>open</strong>. Invite these students to apply at
          <a [href]="d.apply_url" target="_blank" rel="noopener">{{ d.apply_url }}</a>.</ng-container>
        <ng-container *ngIf="!d.applications_open">Scholarship applications aren't open right now. You can still get the list ready;
          invite them once the application period starts.</ng-container>
      </div>

      <div class="row">
        <div class="col-lg-8"><div class="card"><div class="card-body">
          <div class="d-flex flex-wrap align-items-center mb-2" style="gap: 8px">
            <h4 class="header-title mb-0 mr-auto">Class 10 students <small class="text-muted">· best results first</small></h4>
            <select *ngIf="!d.scoped" class="form-control form-control-sm w-auto" [(ngModel)]="centerId" (ngModelChange)="load()" aria-label="Center">
              <option [ngValue]="null">All centers</option>
              <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
            </select>
            <select class="form-control form-control-sm w-auto" [(ngModel)]="stage" aria-label="Stage">
              <option value="">Everyone</option><option value="READY">Not invited yet</option><option value="INVITED">Invited, not applied</option>
              <option value="APPLIED">Applied</option><option value="ACCEPTED">Accepted</option>
            </select>
          </div>
          <p *ngIf="!shown.length" class="text-muted mb-0">No Class 10 students here.</p>
          <div class="table-responsive" *ngIf="shown.length"><table class="table table-sm mb-0">
            <thead class="thead-light"><tr><th>Student</th><th class="text-right">Exam average</th><th class="text-right">Attendance</th><th>Contact</th><th>Status</th><th></th></tr></thead>
            <tbody><tr *ngFor="let r of shown">
              <td><strong>{{ r.name }}</strong><div class="small text-muted">{{ r.father_name }}<span *ngIf="!d.scoped && r.center_name"> · {{ r.center_name }}</span></div></td>
              <td class="text-right">{{ r.average_percent != null ? r.average_percent + '%' : '-' }}
                <div class="small text-muted">{{ r.exams_sat }} of {{ r.exams_held }} exams</div></td>
              <td class="text-right">{{ r.attendance_percent != null ? r.attendance_percent + '%' : '-' }}<div class="small text-muted">90 days</div></td>
              <td class="small">
                <div *ngIf="r.guardian_mobile"><i class="mdi mdi-phone"></i> <a [href]="'tel:' + r.guardian_mobile">{{ r.guardian_mobile }}</a> <span class="text-muted">parent</span></div>
                <div *ngIf="r.student_mobile"><i class="mdi mdi-cellphone"></i> <a [href]="'tel:' + r.student_mobile">{{ r.student_mobile }}</a></div>
                <div *ngIf="r.email"><i class="mdi mdi-email-outline"></i> {{ r.email }}</div>
              </td>
              <td class="small">
                <span class="badge" [ngClass]="badge(r.stage)">{{ stageLabel(r.stage) }}</span>
                <div *ngIf="r.invited_at" class="text-muted">{{ r.invite_method === 'EMAIL' ? 'Emailed' : r.invite_method === 'CALL' ? 'Called' : 'Invited' }}
                  {{ r.invited_at | appDate }}{{ r.invited_by ? ' by ' + r.invited_by : '' }}</div>
                <div *ngIf="r.application_id" class="text-muted">Application #{{ r.application_id }}: {{ r.application_status }}</div>
              </td>
              <td class="text-right text-nowrap">
                <ng-container *ngIf="canEdit && !r.application_id">
                  <button *ngIf="!r.invited_at" class="btn btn-xs btn-outline-primary" (click)="invite(r, 'CALL')" title="I called them"><i class="mdi mdi-phone-check"></i> Called</button>
                  <button *ngIf="r.email" class="btn btn-xs btn-outline-primary ml-1" (click)="email(r)" [disabled]="busy" title="Email the invitation"><i class="mdi mdi-email-send-outline"></i> Email</button>
                  <button *ngIf="r.invited_at" class="btn btn-xs btn-light ml-1" (click)="invite(r, '')" title="Clear the invite">Undo</button>
                </ng-container>
              </td>
            </tr></tbody>
          </table></div>
        </div></div></div>
        <div class="col-lg-4"><div class="card"><div class="card-body">
          <h4 class="header-title">From coaching to scholarship</h4>
          <app-funnel-chart [data]="d.funnel" emptyText="No Class 10 students yet."></app-funnel-chart>
          <p class="small text-muted mt-2 mb-0">A student counts as applied when a scholarship application has their name and one of their mobile numbers.</p>
        </div></div></div>
      </div>
    </ng-container>
  `
})
export class CoachingScholarshipComponent implements OnInit {
  d: Answer | null = null;
  centerId: number | null = null;
  stage = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('RCC_STUDENTS', 'EDIT'); }
  get centers(): CoachingCenter[] { return this.d?.centers ?? []; }
  get shown(): Row[] { return (this.d?.rows ?? []).filter((r) => !this.stage || r.stage === this.stage); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Answer>('/admin/coaching/pipeline', { centerId: this.centerId }).subscribe({
      next: (d) => (this.d = d),
      error: (e) => (this.error = errorText(e, 'Could not load the students.'))
    });
  }

  stageLabel(s: string): string {
    return ({ READY: 'Not invited', INVITED: 'Invited', APPLIED: 'Applied', ACCEPTED: 'Accepted' } as Record<string, string>)[s] ?? s;
  }

  badge(s: string): string {
    return ({ READY: 'badge-light border', INVITED: 'badge-info', APPLIED: 'badge-primary', ACCEPTED: 'badge-success' } as Record<string, string>)[s] ?? 'badge-light';
  }

  invite(r: Row, method: string): void {
    this.api.put<{ message: string }>(`/admin/coaching/pipeline/${r.id}/invited`, { method }).subscribe({
      next: (m) => { this.message = m.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not save.'))
    });
  }

  email(r: Row): void {
    if (!confirm(`Email the scholarship invitation to ${r.email}?`)) return;
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/coaching/pipeline/${r.id}/email-invite`, {}).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send the email.'); }
    });
  }
}
