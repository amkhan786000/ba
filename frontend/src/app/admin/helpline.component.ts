import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

type Row = Record<string, any>;
export const HELPLINE_STATUS: Record<string, string> = { OPEN: 'New', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', CLOSED: 'Closed' };
export function statusBadge(s: string): string {
  return ({ OPEN: 'badge-warning', IN_PROGRESS: 'badge-info', RESOLVED: 'badge-success', CLOSED: 'badge-light border' } as Record<string, string>)[s] ?? 'badge-light';
}

/**
 * Communication > Helpline: issues raised by students and sponsors. Assign, reply (or add a staff-only note) and
 * resolve. Confidential issues (and harassment reports) are seen only by the Super Admin and the assignee.
 */
@Component({
  selector: 'app-helpline',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  styles: [`
    .msg { border-radius: 8px; padding: 8px 12px; margin-bottom: 8px; max-width: 85%; }
    .msg.raiser { background: #f1f3f5; }
    .msg.staff { background: #e8f3dd; margin-left: auto; }
    .msg.internal { background: #fff4cc; margin-left: auto; border: 1px dashed #d9b84a; }
    tr.pick { cursor: pointer; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Helpline</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <select class="form-control form-control-sm w-auto" [(ngModel)]="status" (ngModelChange)="load()" aria-label="Status">
        <option value="ACTIVE">Needs attention ({{ (d?.counts?.['OPEN'] ?? 0) + (d?.counts?.['IN_PROGRESS'] ?? 0) }})</option>
        <option value="">All</option><option *ngFor="let s of statusList" [value]="s.key">{{ s.label }} ({{ d?.counts?.[s.key] ?? 0 }})</option>
      </select>
      <select class="form-control form-control-sm w-auto" [(ngModel)]="category" (ngModelChange)="load()" aria-label="About">
        <option value="">Any topic</option><option *ngFor="let c of categoryList" [value]="c.key">{{ c.label }}</option>
      </select>
      <div class="custom-control custom-checkbox align-self-center">
        <input type="checkbox" class="custom-control-input" id="mineOnly" [(ngModel)]="mine" (ngModelChange)="load()">
        <label class="custom-control-label" for="mineOnly">Assigned to me</label></div>
      <input class="form-control form-control-sm w-auto" placeholder="Search #, subject, name" [(ngModel)]="q" (ngModelChange)="load()">
    </div>

    <div class="row">
      <div [ngClass]="t ? 'col-lg-5' : 'col-12'"><div class="card"><div class="card-body">
        <p *ngIf="d && !d.rows.length" class="text-muted mb-0">No issues here.</p>
        <div class="table-responsive" *ngIf="d?.rows?.length"><table class="table table-sm table-hover mb-0">
          <tbody><tr *ngFor="let r of d!.rows" class="pick" (click)="open(r['id'])" [class.table-active]="t?.['id'] === r['id']">
            <td><strong>#{{ r['id'] }} {{ r['subject'] }}</strong>
              <i *ngIf="r['confidential']" class="mdi mdi-lock text-danger ml-1" title="Confidential"></i>
              <span *ngIf="r['priority'] === 'URGENT'" class="badge badge-danger ml-1">Urgent</span>
              <div class="small text-muted">{{ r['raiser_name'] }} ({{ r['raiser_kind'] === 'SPONSOR' ? 'sponsor' : 'student' }}) · {{ r['category_label'] }}</div></td>
            <td class="text-right small"><span class="badge" [ngClass]="badge(r['status'])">{{ statuses[r['status']] }}</span>
              <div class="text-muted">{{ r['last_activity_at'] | appDate }}</div><div class="text-muted">{{ r['assigned_name'] || 'Unassigned' }}</div></td>
          </tr></tbody>
        </table></div>
      </div></div></div>

      <div class="col-lg-7" *ngIf="t"><div class="card"><div class="card-body">
        <div class="d-flex"><h4 class="header-title mr-auto">#{{ t['id'] }} {{ t['subject'] }}
          <i *ngIf="t['confidential']" class="mdi mdi-lock text-danger" title="Confidential"></i></h4>
          <button class="btn btn-sm btn-light" (click)="t = null" aria-label="Close"><i class="mdi mdi-close"></i></button></div>
        <p class="small text-muted">{{ t['raiser_name'] }} {{ t['raiser_code'] ? '(' + t['raiser_code'] + ')' : '' }} · {{ t['raiser_kind'] === 'SPONSOR' ? 'Sponsor' : 'Student' }}
          {{ t['raiser_chapter'] ? '· ' + t['raiser_chapter'] : '' }} {{ t['raiser_phone'] ? '· ' + t['raiser_phone'] : '' }} · {{ t['category_label'] }} · raised {{ t['created_at'] | appDate: 'datetime' }}</p>

        <div class="row" *ngIf="canEdit">
          <div class="col-md-4 form-group"><label class="small mb-0">Status</label>
            <select class="form-control form-control-sm" [(ngModel)]="t['status']" (ngModelChange)="update({ status: $event })">
              <option *ngFor="let s of statusList" [value]="s.key">{{ s.label }}</option></select></div>
          <div class="col-md-4 form-group"><label class="small mb-0">Assigned to</label>
            <select class="form-control form-control-sm" [(ngModel)]="t['assigned_to']" (ngModelChange)="update({ assignedTo: $event })">
              <option [ngValue]="null">Nobody</option><option *ngFor="let s of d?.staff" [ngValue]="s['id']">{{ s['name'] }}</option></select></div>
          <div class="col-md-4 form-group"><label class="small mb-0">Priority</label>
            <select class="form-control form-control-sm" [(ngModel)]="t['priority']" (ngModelChange)="update({ priority: $event })">
              <option value="NORMAL">Normal</option><option value="URGENT">Urgent</option></select></div>
        </div>

        <div class="d-flex flex-column mb-3">
          <div *ngFor="let m of t['messages']" class="msg" [ngClass]="m['from_raiser'] ? 'raiser' : m['internal'] ? 'internal' : 'staff'">
            <div class="small text-muted">{{ m['author'] }}{{ m['internal'] ? ' · staff-only note' : '' }} · {{ m['created_at'] | appDate: 'datetime' }}</div>
            <div style="white-space: pre-line">{{ m['body'] }}</div>
          </div>
        </div>
        <ng-container *ngIf="canEdit">
          <textarea class="form-control mb-2" rows="3" maxlength="4000" [(ngModel)]="reply" placeholder="Write a reply…"></textarea>
          <div class="d-flex align-items-center" style="gap: 8px">
            <div class="custom-control custom-checkbox mr-auto"><input type="checkbox" class="custom-control-input" id="internal" [(ngModel)]="internal">
              <label class="custom-control-label" for="internal">Staff-only note (the {{ t['raiser_kind'] === 'SPONSOR' ? 'sponsor' : 'student' }} won't see it)</label></div>
            <button class="btn btn-sm btn-outline-success" (click)="send('RESOLVED')" [disabled]="busy || !reply.trim() || internal">Reply and resolve</button>
            <button class="btn btn-sm btn-primary" (click)="send(null)" [disabled]="busy || !reply.trim()">{{ internal ? 'Add note' : 'Send reply' }}</button>
          </div>
        </ng-container>
      </div></div></div>
    </div>
  `
})
export class HelplineComponent implements OnInit {
  readonly statuses = HELPLINE_STATUS;
  readonly statusList = Object.entries(HELPLINE_STATUS).map(([key, label]) => ({ key, label }));
  readonly badge = statusBadge;
  d: { rows: Row[]; counts: Record<string, number>; categories: Record<string, string>; staff: Row[] } | null = null;
  t: Row | null = null;
  status = 'ACTIVE';
  category = '';
  mine = false;
  q = '';
  reply = '';
  internal = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('HELPLINE', 'EDIT'); }
  get categoryList() { return Object.entries(this.d?.categories ?? {}).map(([key, label]) => ({ key, label })); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<typeof this.d>('/admin/helpline', { status: this.status, category: this.category, mine: this.mine || null, q: this.q }).subscribe({
      next: (d) => (this.d = d), error: (e) => (this.error = errorText(e, 'Could not load the helpline.'))
    });
  }

  open(id: number): void {
    this.reply = '';
    this.internal = false;
    this.api.get<Row>(`/admin/helpline/${id}`).subscribe({ next: (t) => (this.t = t), error: (e) => (this.error = errorText(e, 'Could not open the issue.')) });
  }

  update(change: Row): void {
    if (!this.t) return;
    const id = this.t['id'];
    this.api.put(`/admin/helpline/${id}`, change).subscribe({
      next: () => { this.open(id); this.load(); }, error: (e) => { this.error = errorText(e, 'Could not save.'); this.open(id); }
    });
  }

  send(status: string | null): void {
    if (!this.t) return;
    const id = this.t['id'];
    const body: Row = { message: this.reply, internal: this.internal };
    if (status) body['status'] = status;
    this.busy = true;
    this.api.put(`/admin/helpline/${id}`, body).subscribe({
      next: () => { this.busy = false; this.open(id); this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send.'); }
    });
  }
}
