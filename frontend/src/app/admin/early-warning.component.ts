import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { SessionYearSelectComponent } from '../shared/session-year-select.component';

interface Signal { code: string; label: string; detail: string | null }
interface FollowUp { followup_date: string; method: string; outcome: string; note: string | null; next_date: string | null }
interface Row {
  kind: 'SCHOLAR' | 'COACHING'; id: number; name: string; code: string | null; place: string | null; programme: string;
  phone: string | null; signals: Signal[]; last_follow_up: FollowUp | null; needs_follow_up: boolean;
}
interface Answer { rows: Row[]; needs_follow_up: number; followed_up: number; by_signal: Record<string, number>; coaching: boolean; years?: number[] }

const SIGNALS: { code: string; label: string }[] = [
  { code: 'LOW_MARKS', label: 'Low marks' }, { code: 'FALLING_MARKS', label: 'Marks falling' },
  { code: 'REPORT_OVERDUE', label: 'Progress report not sent' }, { code: 'INSTALLMENT_OVERDUE', label: 'Installment overdue' },
  { code: 'ON_HOLD', label: 'Studies on hold' }, { code: 'MISSED_EXAMS', label: 'Missed exams' },
  { code: 'LOW_ATTENDANCE', label: 'Low attendance' }, { code: 'NO_CONTACT', label: 'No follow-up' }
];

/**
 * Students > Early Warning: scholarship and coaching students showing signs they may drop out, and a quick way to
 * record the follow-up call or visit. Chapter leads see their chapter, coaching coordinators their center.
 */
@Component({
  selector: 'app-early-warning',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, SessionYearSelectComponent],
  styles: [`
    .sig { display: inline-block; font-size: 12px; padding: 2px 8px; border-radius: 10px; margin: 0 4px 4px 0; background: #fdecea; color: #8a1c12; }
    .sig.NO_CONTACT { background: #fff4cc; color: #6b4e00; }
    .sig.ON_HOLD, .sig.REPORT_OVERDUE { background: #eef1f4; color: #39424e; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Early Warning</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p class="text-muted">Students showing signs they may drop out. Call or visit them, then record what happened; they leave the
      "Needs a follow-up" list for 14 days, or until the next follow-up date you set.</p>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <div class="btn-group">
        <button class="btn btn-sm" [ngClass]="view === 'NEEDS' ? 'btn-danger' : 'btn-light'" (click)="setView('NEEDS')">Needs a follow-up <span class="badge badge-light ml-1">{{ d?.needs_follow_up ?? 0 }}</span></button>
        <button class="btn btn-sm" [ngClass]="view === 'FOLLOWED' ? 'btn-secondary' : 'btn-light'" (click)="setView('FOLLOWED')">Followed up <span class="badge badge-light ml-1">{{ d?.followed_up ?? 0 }}</span></button>
        <button class="btn btn-sm" [ngClass]="view === 'ALL' ? 'btn-secondary' : 'btn-light'" (click)="setView('ALL')">All</button>
      </div>
      <select *ngIf="d?.coaching" class="form-control form-control-sm w-auto" [(ngModel)]="kind" (ngModelChange)="load()" aria-label="Students">
        <option value="">Scholarship and coaching</option><option value="SCHOLAR">Scholarship students</option><option value="COACHING">Coaching students</option>
      </select>
      <select class="form-control form-control-sm w-auto" [(ngModel)]="signal" (ngModelChange)="load()" aria-label="Warning sign">
        <option value="">Any sign</option>
        <option *ngFor="let s of signals" [value]="s.code">{{ s.label }} ({{ d?.by_signal?.[s.label] ?? 0 }})</option>
      </select>
      <app-session-year-select [years]="d?.years" [(value)]="sessionYear" (valueChange)="load()"
                               title="Scholarship students of this session year (coaching students have none)"></app-session-year-select>
      <input class="form-control form-control-sm w-auto" placeholder="Search name, ID, chapter / center" [(ngModel)]="q" (ngModelChange)="load()">
    </div>

    <div class="card"><div class="card-body">
      <p *ngIf="d && !d.rows.length" class="text-muted mb-0">{{ view === 'NEEDS' ? 'Nobody needs a follow-up right now.' : 'No students here.' }}</p>
      <div class="table-responsive" *ngIf="d?.rows?.length"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Student</th><th>Warning signs</th><th>Last follow-up</th><th></th></tr></thead>
        <tbody><tr *ngFor="let r of d!.rows">
          <td><strong>{{ r.name }}</strong> <span class="small text-muted">{{ r.code }}</span>
            <div class="small text-muted">{{ r.programme }}{{ r.place ? ' · ' + r.place : '' }}</div>
            <div class="small" *ngIf="r.phone"><i class="mdi mdi-phone"></i> <a [href]="'tel:' + r.phone">{{ r.phone }}</a></div></td>
          <td><span *ngFor="let s of r.signals" class="sig" [ngClass]="s.code" [title]="s.detail || ''">{{ s.label }}<ng-container *ngIf="s.detail">: {{ s.detail }}</ng-container></span></td>
          <td class="small">
            <ng-container *ngIf="r.last_follow_up as f; else never">
              {{ f.followup_date | appDate }} · {{ methodLabel(f.method) }} · <strong>{{ outcomeLabel(f.outcome) }}</strong>
              <div class="text-muted" *ngIf="f.note">{{ f.note }}</div>
              <div *ngIf="f.next_date" [class.text-danger]="r.needs_follow_up">Next: {{ f.next_date | appDate }}</div>
            </ng-container>
            <ng-template #never><span class="text-muted">Never</span></ng-template>
          </td>
          <td class="text-right text-nowrap">
            <button class="btn btn-xs btn-outline-secondary" (click)="openHistory(r)" title="All follow-ups"><i class="mdi mdi-history"></i></button>
            <button *ngIf="canEdit" class="btn btn-xs btn-primary ml-1" (click)="openForm(r)"><i class="mdi mdi-phone-plus"></i> Record follow-up</button>
          </td>
        </tr></tbody>
      </table></div>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Follow-up: {{ form.row.name }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <div class="row">
              <div class="col-6 form-group"><label>Date</label><input type="date" class="form-control" [max]="today" [(ngModel)]="form.date"></div>
              <div class="col-6 form-group"><label>How</label>
                <select class="form-control" [(ngModel)]="form.method">
                  <option value="CALL">Phone call</option><option value="VISIT">Home visit</option><option value="MEETING">Meeting</option><option value="OTHER">Other</option>
                </select></div>
              <div class="col-12 form-group"><label>What happened</label>
                <textarea class="form-control" rows="3" maxlength="1000" [(ngModel)]="form.note" placeholder="e.g. Father unwell, she missed classes; returning next week"></textarea></div>
              <div class="col-6 form-group"><label>Outcome</label>
                <select class="form-control" [(ngModel)]="form.outcome">
                  <option value="MONITOR">Keep watching</option><option value="RESOLVED">Resolved</option><option value="ESCALATE">Needs the office / more help</option>
                </select></div>
              <div class="col-6 form-group"><label>Follow up again on</label><input type="date" class="form-control" [min]="form.date" [(ngModel)]="form.nextDate"></div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="saving">{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="history" class="modal fade show d-block" tabindex="-1" (click)="history = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Follow-ups: {{ history.row.name }}</h5>
            <button type="button" class="close" (click)="history = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <p *ngIf="!history.items.length" class="text-muted mb-0">No follow-ups yet.</p>
            <table class="table table-sm mb-0" *ngIf="history.items.length">
              <thead class="thead-light"><tr><th>Date</th><th>How</th><th>Outcome</th><th>Note</th><th>Next</th></tr></thead>
              <tbody><tr *ngFor="let f of history.items">
                <td>{{ f.followup_date | appDate }}</td><td>{{ methodLabel(f.method) }}</td><td>{{ outcomeLabel(f.outcome) }}</td>
                <td>{{ f.note || '' }}</td><td>{{ f.next_date ? (f.next_date | appDate) : '' }}</td>
              </tr></tbody>
            </table>
          </div>
          <div class="modal-footer"><button type="button" class="btn btn-light" (click)="history = null">Close</button></div>
        </div>
      </div>
    </div>
  `
})
export class EarlyWarningComponent implements OnInit {
  readonly today = new Date().toISOString().slice(0, 10);
  readonly signals = SIGNALS;
  d: Answer | null = null;
  view: 'NEEDS' | 'FOLLOWED' | 'ALL' = 'NEEDS';
  kind = '';
  signal = '';
  q = '';
  /** Scholarship students' session year ('' all; picking one leaves coaching students out). */
  sessionYear = '';
  form: { row: Row; date: string; method: string; outcome: string; note: string; nextDate: string } | null = null;
  formError = '';
  history: { row: Row; items: FollowUp[] } | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('EARLY_WARNING', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  setView(v: 'NEEDS' | 'FOLLOWED' | 'ALL'): void { this.view = v; this.load(); }

  load(): void {
    this.api.get<Answer>('/admin/early-warning', { view: this.view, kind: this.kind, signal: this.signal, q: this.q, sessionYear: this.sessionYear }).subscribe({
      next: (d) => (this.d = d),
      error: (e) => (this.error = errorText(e, 'Could not load the list.'))
    });
  }

  methodLabel(m: string): string { return ({ CALL: 'Call', VISIT: 'Home visit', MEETING: 'Meeting', OTHER: 'Other' } as Record<string, string>)[m] ?? m; }
  outcomeLabel(o: string): string { return ({ RESOLVED: 'Resolved', MONITOR: 'Keep watching', ESCALATE: 'Needs more help' } as Record<string, string>)[o] ?? o; }

  openForm(r: Row): void {
    this.formError = '';
    const inTwoWeeks = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
    this.form = { row: r, date: this.today, method: 'CALL', outcome: 'MONITOR', note: '', nextDate: inTwoWeeks };
  }

  save(): void {
    const f = this.form;
    if (!f) return;
    this.saving = true;
    this.api.post<{ message: string }>('/admin/early-warning/followups', {
      kind: f.row.kind, studentId: f.row.id, date: f.date, method: f.method, outcome: f.outcome, note: f.note,
      nextDate: f.outcome === 'RESOLVED' ? null : f.nextDate
    }).subscribe({
      next: (m) => { this.saving = false; this.form = null; this.message = m.message; this.load(); },
      error: (e) => { this.saving = false; this.formError = errorText(e, 'Could not save the follow-up.'); }
    });
  }

  openHistory(r: Row): void {
    this.api.get<FollowUp[]>('/admin/early-warning/followups', { kind: r.kind, studentId: r.id }).subscribe({
      next: (items) => (this.history = { row: r, items }),
      error: (e) => (this.error = errorText(e, 'Could not load the follow-ups.'))
    });
  }
}
