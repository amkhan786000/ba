import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';
import { AppDatePipe } from '../shared/app-date';

interface Last { at: string; channel: string; kind: string; by: string; note: string }
interface Row {
  sponsor_id: number; name: string; user_id: string | null; chapter: string | null; has_email: boolean; last_followup: Last | null;
  students?: number; overdue_count?: number; overdue_amount?: number; oldest_due?: string;
  since?: string | null; last_student?: string | null; never_sponsored?: boolean;
}

/**
 * Sponsors & Payments > Sponsor Follow-up: sponsors who stopped paying (installments overdue for a while) and
 * sponsors with no student now. Record a call or note, or send a personal email (both kept in the history, so nobody
 * is asked twice in a week). Sponsors show by name and ID only.
 */
@Component({
  selector: 'app-admin-sponsor-followup',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Sponsor Follow-up</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'LAPSED'" (click)="$event.preventDefault(); go('LAPSED')">Stopped paying ({{ lapsed.length }})</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'AVAILABLE'" (click)="$event.preventDefault(); go('AVAILABLE')">No student now ({{ available.length }})</a></li>
    </ul>

    <div class="card"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-end mb-2">
        <p class="text-muted mb-2 mr-3" *ngIf="tab === 'LAPSED'">Sponsors whose installments are unpaid more than
          <input type="number" min="0" class="form-control form-control-sm d-inline-block" style="width: 70px" [(ngModel)]="days" (change)="load()" aria-label="Days"> days after their due date.
          The automatic reminders still go out; this is for a personal call or note.</p>
        <p class="text-muted mb-2 mr-3" *ngIf="tab === 'AVAILABLE'">Active sponsors with no student at the moment: invite them to sponsor another student.</p>
        <button *ngIf="canEdit && selected.size" class="btn btn-primary btn-sm mb-2" (click)="compose = { message: '', force: false }">
          <i class="mdi mdi-email-outline mr-1"></i>Email {{ selected.size }} sponsor{{ selected.size === 1 ? '' : 's' }}</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr>
          <th *ngIf="canEdit"><input type="checkbox" [checked]="allChecked" (change)="toggleAll()" aria-label="Choose all"></th>
          <th>Sponsor</th>
          <ng-container *ngIf="tab === 'LAPSED'"><th>Students</th><th>Unpaid</th><th>Oldest due</th></ng-container>
          <ng-container *ngIf="tab === 'AVAILABLE'"><th>Last student</th><th>Without a student since</th></ng-container>
          <th>Last follow-up</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="7" class="text-center text-muted">Nobody here.</td></tr>
          <tr *ngFor="let r of rows">
            <td *ngIf="canEdit"><input type="checkbox" [checked]="selected.has(r.sponsor_id)" (change)="toggle(r.sponsor_id)" [disabled]="!r.has_email" [attr.aria-label]="'Choose ' + r.name"></td>
            <td><strong>{{ r.name }}</strong><div class="small text-muted">{{ r.user_id }}{{ r.chapter ? ' · ' + r.chapter : '' }}<span *ngIf="!r.has_email"> · no email</span></div></td>
            <ng-container *ngIf="tab === 'LAPSED'">
              <td>{{ r.students }}</td><td>{{ r.overdue_count }} · ₹{{ r.overdue_amount | number: '1.0-0' }}</td><td>{{ r.oldest_due | appDate }}</td>
            </ng-container>
            <ng-container *ngIf="tab === 'AVAILABLE'">
              <td>{{ r.never_sponsored ? 'Never had a student' : r.last_student }}</td><td>{{ r.since ? (r.since | appDate) : '--' }}</td>
            </ng-container>
            <td class="small"><ng-container *ngIf="r.last_followup as f">{{ f.at | date: 'd MMM' }} · {{ channels[f.channel] }}<span *ngIf="f.by"> by {{ f.by }}</span>
              <div class="text-muted text-truncate" style="max-width: 220px">{{ f.note }}</div></ng-container><span *ngIf="!r.last_followup" class="text-muted">Not yet</span></td>
            <td class="text-right text-nowrap">
              <button class="btn btn-xs btn-outline-secondary mr-1" (click)="openHistory(r)">History</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-primary" (click)="note = { sponsor: r, channel: 'CALL', text: '' }">Log a call / note</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="compose" class="modal fade show d-block" tabindex="-1" (click)="compose = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ tab === 'LAPSED' ? 'A gentle reminder' : 'Would you sponsor another student?' }}</h5>
            <button type="button" class="close" (click)="compose = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <p class="small text-muted">Sends the "{{ tab === 'LAPSED' ? 'Follow-up: sponsor\\'s payments overdue' : 'Follow-up: would you sponsor another student?' }}" email (wording in Email Templates)
              to {{ selected.size }} sponsor{{ selected.size === 1 ? '' : 's' }}.</p>
            <div class="form-group"><label for="m-text">A personal line (optional)</label><textarea id="m-text" class="form-control" rows="3" maxlength="1000" [(ngModel)]="compose.message"></textarea></div>
            <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="m-force" [(ngModel)]="compose.force">
              <label class="custom-control-label" for="m-force">Also send to sponsors contacted in the last 7 days</label></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="compose = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="send()" [disabled]="busy">{{ busy ? 'Sending…' : 'Send' }}</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="note" class="modal fade show d-block" tabindex="-1" (click)="note = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ note.sponsor.name }}</h5><button type="button" class="close" (click)="note = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="n-channel">What happened</label>
              <select id="n-channel" class="form-control" [(ngModel)]="note.channel"><option value="CALL">Phone call</option><option value="NOTE">Note</option></select></div>
            <div class="form-group mb-0"><label for="n-text">Note *</label><textarea id="n-text" class="form-control" rows="3" maxlength="1000" [(ngModel)]="note.text"
              placeholder="e.g. Will pay after Eid; wants a student in Patna"></textarea></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="note = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveNote()" [disabled]="busy || !note.text.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="hist" class="modal fade show d-block" tabindex="-1" (click)="hist = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Follow-up: {{ hist.name }}</h5><button type="button" class="close" (click)="hist = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <p *ngIf="!hist.rows.length" class="text-muted mb-0">Nothing yet.</p>
            <ul class="list-unstyled mb-0"><li *ngFor="let h of hist.rows" class="border-bottom py-2">
              <div class="small text-muted">{{ h.at | appDate: 'datetime' }} · {{ channels[h.channel] }} · {{ h.kind === 'LAPSED' ? 'stopped paying' : 'no student' }}<span *ngIf="h.by"> · {{ h.by }}</span></div>
              <div>{{ h.note }}</div></li></ul>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminSponsorFollowupComponent implements OnInit {
  readonly channels: Record<string, string> = { EMAIL: 'Email', CALL: 'Call', NOTE: 'Note' };
  tab: 'LAPSED' | 'AVAILABLE' = 'LAPSED';
  days = 30;
  lapsed: Row[] = [];
  available: Row[] = [];
  selected = new Set<number>();
  compose: { message: string; force: boolean } | null = null;
  note: { sponsor: Row; channel: string; text: string } | null = null;
  hist: { name: string; rows: Last[] } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('SPONSORSHIPS', 'EDIT'); }
  get rows(): Row[] { return this.tab === 'LAPSED' ? this.lapsed : this.available; }
  get allChecked(): boolean { const ok = this.rows.filter((r) => r.has_email); return ok.length > 0 && ok.every((r) => this.selected.has(r.sponsor_id)); }

  ngOnInit(): void { this.load(); }

  go(tab: 'LAPSED' | 'AVAILABLE'): void { this.tab = tab; this.selected.clear(); }

  load(): void {
    this.api.get<Row[]>('/admin/sponsor-followup/lapsed', { days: this.days }).subscribe({ next: (r) => (this.lapsed = r), error: (e) => (this.error = errorText(e, 'Could not load the sponsors.')) });
    this.api.get<Row[]>('/admin/sponsor-followup/available').subscribe({ next: (r) => (this.available = r), error: (e) => (this.error = errorText(e, 'Could not load the sponsors.')) });
  }

  toggle(id: number): void { if (this.selected.has(id)) this.selected.delete(id); else this.selected.add(id); }
  toggleAll(): void {
    const ok = this.rows.filter((r) => r.has_email);
    if (this.allChecked) ok.forEach((r) => this.selected.delete(r.sponsor_id)); else ok.forEach((r) => this.selected.add(r.sponsor_id));
  }

  send(): void {
    if (!this.compose) return;
    this.busy = true;
    this.api.post<{ message: string; skipped: string[] }>('/admin/sponsor-followup/emails',
      { sponsorIds: [...this.selected], kind: this.tab, message: this.compose.message, force: this.compose.force }).subscribe({
      next: (r) => {
        this.busy = false;
        this.compose = null;
        this.selected.clear();
        this.message = r.message + (r.skipped.length ? ' ' + r.skipped.join('; ') + '.' : '');
        this.load();
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send the emails.'); }
    });
  }

  saveNote(): void {
    if (!this.note) return;
    this.busy = true;
    this.api.post<{ message: string }>('/admin/sponsor-followup/notes',
      { sponsorId: this.note.sponsor.sponsor_id, kind: this.tab, channel: this.note.channel, note: this.note.text }).subscribe({
      next: (r) => { this.busy = false; this.note = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the note.'); }
    });
  }

  openHistory(r: Row): void {
    this.api.get<Last[]>(`/admin/sponsor-followup/${r.sponsor_id}/history`).subscribe({
      next: (rows) => (this.hist = { name: r.name, rows }), error: (e) => (this.error = errorText(e, 'Could not load the history.'))
    });
  }
}
