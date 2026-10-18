import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

interface Person { id: number; user_id: string | null; name: string; email: string | null; why?: string | null; role?: string; session_year?: number | null }
interface Options { roles: { role_id: number; role_name: string; accounts: number }[]; student_years: { year: number; students: number }[]; student_role_id: number; running: boolean }
interface Preview { role_name: string; years: number[]; who: string; to_reset: number; skipped: Person[] }
interface Status {
  state: 'none' | 'running' | 'finished'; role_name?: string; years?: number[]; total?: number; done?: number; reset?: number;
  failed?: Person[]; skipped?: Person[]; started_at?: string; finished_at?: string | null; changed_since?: number;
}
interface Temporary {
  by_role: { role: string; accounts: number; own_password: number; temporary: number }[];
  students_by_year: { session_year: number; students: number; own_password: number; temporary: number }[];
  still_temporary: Person[];
}

/**
 * Administration > Bulk Password Reset (Super Admin): new temporary passwords for a role (students: chosen session
 * years), emailed in the background; and who has set their own password since (still on a temporary one or not).
 */
@Component({
  selector: 'app-admin-bulk-password-reset',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  styles: [`
    .years { display: flex; flex-wrap: wrap; gap: 6px 14px; }
    .bar { height: 8px; border-radius: 4px; background: #e5e9e4; overflow: hidden; }
    .bar > div { height: 100%; background: #4f7a00; transition: width .3s; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Bulk Password Reset</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p *ngIf="!superAdmin" class="text-muted">Only the Super Admin can reset passwords in bulk.</p>

    <ng-container *ngIf="superAdmin">
      <div class="card"><div class="card-body">
        <h4 class="header-title">Reset passwords</h4>
        <p class="text-muted small">Everyone selected gets a new temporary password by email and must choose their own at the next
          sign-in. Accounts without a real email are skipped (they'd have no way to get the new password), and so are inactive
          accounts and your own. A password only changes once its email has gone out.</p>
        <div class="row">
          <div class="col-md-4 form-group">
            <label for="role">Role</label>
            <select id="role" class="form-control" [(ngModel)]="roleId" (ngModelChange)="preview = null">
              <option [ngValue]="null">-- Choose a role --</option>
              <option *ngFor="let r of options?.roles" [ngValue]="r.role_id">{{ r.role_name }} ({{ r.accounts }})</option>
            </select>
          </div>
          <div class="col-md-5 form-group">
            <label for="who">Who</label>
            <select id="who" class="form-control" [(ngModel)]="who" (ngModelChange)="preview = null">
              <option value="ALL">All users</option>
              <option value="TEMPORARY">Only those who haven't set their own password yet</option>
              <option value="OWN">Only those who already set their own password</option>
            </select>
          </div>
        </div>
        <div *ngIf="isStudents" class="form-group">
          <label>Session years</label>
          <div class="years">
            <div class="custom-control custom-checkbox" *ngFor="let y of options?.student_years">
              <input type="checkbox" class="custom-control-input" [id]="'y' + y.year" [checked]="years.has(y.year)" (change)="toggleYear(y.year)">
              <label class="custom-control-label" [for]="'y' + y.year">{{ y.year }} <span class="text-muted">({{ y.students }})</span></label>
            </div>
            <span *ngIf="!options?.student_years?.length" class="text-muted small">No students with a session year.</span>
          </div>
        </div>
        <button class="btn btn-outline-primary mr-2" (click)="check()" [disabled]="!ready || busy">Check who</button>

        <div *ngIf="preview" class="mt-3">
          <div class="alert" [ngClass]="preview.to_reset ? 'alert-warning' : 'alert-light border'">
            <strong>{{ preview.to_reset }}</strong> {{ preview.role_name }}{{ preview.years.length ? ' of ' + preview.years.join(', ') : '' }}
            ({{ preview.who.toLowerCase() }}) will get a new temporary password by email.
            <span *ngIf="preview.skipped.length"> {{ preview.skipped.length }} skipped (no real email).</span>
          </div>
          <details *ngIf="preview.skipped.length" class="mb-2"><summary class="small">Skipped ({{ preview.skipped.length }})</summary>
            <ul class="small mb-0"><li *ngFor="let p of preview.skipped">{{ p.name }} ({{ p.user_id }}) {{ p.email || 'no email' }}</li></ul>
          </details>
          <button class="btn btn-danger" (click)="start()" [disabled]="!preview.to_reset || busy || running">
            <i class="mdi mdi-lock-reset mr-1"></i>Reset {{ preview.to_reset }} password{{ preview.to_reset === 1 ? '' : 's' }}</button>
        </div>
      </div></div>

      <div class="card" *ngIf="status && status.state !== 'none'"><div class="card-body">
        <h4 class="header-title">{{ status.state === 'running' ? 'Resetting…' : 'Last reset' }}
          <small class="text-muted">{{ status.role_name }}{{ status.years?.length ? ' · ' + status.years!.join(', ') : '' }} · started {{ status.started_at | appDate: 'datetime' }}</small></h4>
        <div class="bar my-2"><div [style.width.%]="status.total ? (100 * (status.done || 0)) / status.total : 0"></div></div>
        <p class="mb-1">{{ status.done }} of {{ status.total }} done · <strong>{{ status.reset }}</strong> emailed a new password
          <span *ngIf="status.failed?.length" class="text-danger"> · {{ status.failed!.length }} email(s) failed (old password still works)</span>
          <span *ngIf="status.skipped?.length" class="text-muted"> · {{ status.skipped!.length }} skipped (no email)</span></p>
        <p *ngIf="status.state === 'finished' && status.reset" class="mb-1">
          <strong>{{ status.changed_since }}</strong> of {{ status.reset }} have set their own password since.</p>
        <details *ngIf="status.failed?.length"><summary class="small">Emails that failed</summary>
          <ul class="small mb-0"><li *ngFor="let p of status.failed">{{ p.name }} ({{ p.user_id }}) {{ p.email }}: {{ p.why }}</li></ul>
        </details>
      </div></div>

      <div class="card" *ngIf="temporary"><div class="card-body">
        <div class="d-flex justify-content-between align-items-center">
          <h4 class="header-title mb-0">Who has set their own password</h4>
          <button class="btn btn-sm btn-light" (click)="loadTemporary()"><i class="mdi mdi-refresh"></i> Refresh</button>
        </div>
        <p class="text-muted small">Active accounts. "Temporary" = still signing in with a password the office gave them (they must choose their own).</p>
        <div class="table-responsive"><table class="table table-sm mb-3">
          <thead><tr><th>Role</th><th class="text-right">Accounts</th><th class="text-right">Own password</th><th class="text-right">Temporary</th></tr></thead>
          <tbody><tr *ngFor="let r of temporary.by_role"><td>{{ r.role }}</td><td class="text-right">{{ r.accounts }}</td>
            <td class="text-right text-success">{{ r.own_password }}</td><td class="text-right" [class.text-danger]="r.temporary">{{ r.temporary }}</td></tr></tbody>
        </table></div>
        <ng-container *ngIf="temporary.students_by_year.length">
          <h5 class="mt-2">Students by session year</h5>
          <div class="table-responsive"><table class="table table-sm mb-3">
            <thead><tr><th>Session year</th><th class="text-right">Students</th><th class="text-right">Own password</th><th class="text-right">Temporary</th></tr></thead>
            <tbody><tr *ngFor="let y of temporary.students_by_year"><td>{{ y.session_year }}</td><td class="text-right">{{ y.students }}</td>
              <td class="text-right text-success">{{ y.own_password }}</td><td class="text-right" [class.text-danger]="y.temporary">{{ y.temporary }}</td></tr></tbody>
          </table></div>
        </ng-container>
        <details *ngIf="temporary.still_temporary.length"><summary>Still on a temporary password ({{ temporary.still_temporary.length }})</summary>
          <div class="table-responsive mt-2"><table class="table table-sm mb-0">
            <thead><tr><th>Name</th><th>User ID</th><th>Role</th><th>Session year</th><th>Email</th></tr></thead>
            <tbody><tr *ngFor="let p of temporary.still_temporary"><td>{{ p.name }}</td><td>{{ p.user_id }}</td><td>{{ p.role }}</td>
              <td>{{ p.session_year || '' }}</td><td>{{ p.email }}</td></tr></tbody>
          </table></div>
        </details>
      </div></div>
    </ng-container>
  `
})
export class AdminBulkPasswordResetComponent implements OnInit, OnDestroy {
  options: Options | null = null;
  roleId: number | null = null;
  who: 'ALL' | 'TEMPORARY' | 'OWN' = 'ALL';
  years = new Set<number>();
  preview: Preview | null = null;
  status: Status | null = null;
  temporary: Temporary | null = null;
  busy = false;
  message = '';
  error = '';
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(private api: ApiService, private auth: AuthService) {}

  get superAdmin(): boolean { return this.auth.isSuperAdmin(); }
  get isStudents(): boolean { return this.roleId !== null && this.roleId === this.options?.student_role_id; }
  get ready(): boolean { return this.roleId !== null && (!this.isStudents || this.years.size > 0); }
  get running(): boolean { return this.status?.state === 'running'; }

  ngOnInit(): void {
    if (!this.superAdmin) return;
    this.api.get<Options>('/admin/bulk-password-reset').subscribe({
      next: (o) => (this.options = o),
      error: (e) => (this.error = errorText(e, 'Could not load the roles.'))
    });
    this.loadStatus();
    this.loadTemporary();
  }

  ngOnDestroy(): void { this.stopPolling(); }

  toggleYear(y: number): void {
    if (this.years.has(y)) this.years.delete(y); else this.years.add(y);
    this.preview = null;
  }

  private body(): Record<string, unknown> {
    return { roleId: this.roleId, who: this.who, years: this.isStudents ? [...this.years] : [] };
  }

  check(): void {
    this.busy = true;
    this.error = '';
    this.api.post<Preview>('/admin/bulk-password-reset/preview', this.body()).subscribe({
      next: (p) => { this.busy = false; this.preview = p; },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not check who would be reset.'); }
    });
  }

  start(): void {
    const p = this.preview;
    if (!p || !confirm(`Email a new temporary password to ${p.to_reset} account(s)? Their current passwords stop working.`)) return;
    this.busy = true;
    this.api.post<Status>('/admin/bulk-password-reset', this.body()).subscribe({
      next: (s) => { this.busy = false; this.preview = null; this.status = s; this.message = 'The reset has started; emails are going out.'; this.poll(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not start the reset.'); }
    });
  }

  loadTemporary(): void {
    this.api.get<Temporary>('/admin/bulk-password-reset/temporary-passwords').subscribe({ next: (t) => (this.temporary = t) });
  }

  private loadStatus(): void {
    this.api.get<Status>('/admin/bulk-password-reset/status').subscribe({
      next: (s) => { this.status = s; if (s.state === 'running') this.poll(); }
    });
  }

  /** Follows a running reset every 2 seconds; refreshes the counts when it ends. */
  private poll(): void {
    this.stopPolling();
    this.timer = setInterval(() => {
      this.api.get<Status>('/admin/bulk-password-reset/status').subscribe({
        next: (s) => { this.status = s; if (s.state !== 'running') { this.stopPolling(); this.loadTemporary(); } }
      });
    }, 2000);
  }

  private stopPolling(): void { if (this.timer) { clearInterval(this.timer); this.timer = null; } }
}
