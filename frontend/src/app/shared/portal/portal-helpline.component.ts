import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AlertsComponent, errorText } from '../alerts/alerts.component';
import { AppDatePipe } from '../app-date';
import { HELPLINE_STATUS, statusBadge } from '../../admin/helpline.component';

type Row = Record<string, any>;

/** Student and sponsor portals > Helpline: raise an issue with the office and follow the conversation. */
@Component({
  selector: 'app-portal-helpline',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  styles: [`
    .msg { border-radius: 8px; padding: 8px 12px; margin-bottom: 8px; max-width: 85%; }
    .msg.me { background: #e8f3dd; margin-left: auto; }
    .msg.office { background: #f1f3f5; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex justify-content-between align-items-center">
      <h4 class="page-title">Helpline</h4>
      <button *ngIf="!form && !t" class="btn btn-primary btn-sm" (click)="newIssue()"><i class="mdi mdi-plus"></i> Raise an issue</button>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="form"><div class="card-body">
      <h4 class="header-title">Raise an issue</h4>
      <p class="small text-muted">Tell the Rahbar office what's wrong. You'll get a reply here (and a notification).</p>
      <div class="form-group"><label>What is it about?</label>
        <select class="form-control" [(ngModel)]="form['category']"><option *ngFor="let c of categoryList" [value]="c.key">{{ c.label }}</option></select></div>
      <div class="form-group"><label>Subject *</label><input class="form-control" maxlength="200" [(ngModel)]="form['subject']"></div>
      <div class="form-group"><label>Tell us more *</label><textarea class="form-control" rows="5" maxlength="4000" [(ngModel)]="form['message']"></textarea></div>
      <div class="custom-control custom-checkbox mb-3">
        <input type="checkbox" class="custom-control-input" id="conf" [(ngModel)]="form['confidential']">
        <label class="custom-control-label" for="conf">Confidential: only the head of the office should see this</label>
        <div *ngIf="form['category'] === 'HARASSMENT'" class="small text-muted">Reports of harassment are always confidential and treated as urgent.</div>
      </div>
      <button class="btn btn-light mr-2" (click)="form = null">Cancel</button>
      <button class="btn btn-primary" (click)="raise()" [disabled]="busy || !form['subject']?.trim() || !form['message']?.trim()">Send</button>
    </div></div>

    <div class="card" *ngIf="t"><div class="card-body">
      <div class="d-flex"><h4 class="header-title mr-auto">#{{ t['id'] }} {{ t['subject'] }}</h4>
        <button class="btn btn-sm btn-light" (click)="t = null; load()">Back</button></div>
      <p class="small"><span class="badge" [ngClass]="badge(t['status'])">{{ statuses[t['status']] }}</span> · {{ t['category_label'] }} · raised {{ t['created_at'] | appDate }}</p>
      <div class="d-flex flex-column mb-3">
        <div *ngFor="let m of t['messages']" class="msg" [ngClass]="m['from_raiser'] ? 'me' : 'office'">
          <div class="small text-muted">{{ m['from_raiser'] ? 'You' : m['author'] }} · {{ m['created_at'] | appDate: 'datetime' }}</div>
          <div style="white-space: pre-line">{{ m['body'] }}</div>
        </div>
      </div>
      <ng-container *ngIf="t['status'] !== 'CLOSED'">
        <textarea class="form-control mb-2" rows="3" maxlength="4000" [(ngModel)]="reply" placeholder="Write a reply…"></textarea>
        <button class="btn btn-primary btn-sm" (click)="send(false)" [disabled]="busy || !reply.trim()">Send</button>
        <button *ngIf="t['status'] !== 'RESOLVED'" class="btn btn-outline-success btn-sm ml-2" (click)="send(true)" [disabled]="busy">It's sorted, thank you</button>
      </ng-container>
    </div></div>

    <div class="card" *ngIf="!form && !t"><div class="card-body">
      <p *ngIf="d && !d.rows.length" class="text-muted mb-0">You haven't raised any issues. If something is wrong, tell us with "Raise an issue".</p>
      <table class="table table-sm table-hover mb-0" *ngIf="d?.rows?.length"><tbody>
        <tr *ngFor="let r of d!.rows" style="cursor: pointer" (click)="open(r['id'])">
          <td><strong>#{{ r['id'] }} {{ r['subject'] }}</strong><div class="small text-muted">{{ r['category_label'] }} · {{ r['created_at'] | appDate }}</div></td>
          <td class="text-right"><span class="badge" [ngClass]="badge(r['status'])">{{ statuses[r['status']] }}</span></td>
        </tr>
      </tbody></table>
    </div></div>
  `
})
export class PortalHelplineComponent implements OnInit {
  readonly statuses = HELPLINE_STATUS;
  readonly badge = statusBadge;
  d: { rows: Row[]; categories: Record<string, string> } | null = null;
  t: Row | null = null;
  form: Row | null = null;
  reply = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  get categoryList() { return Object.entries(this.d?.categories ?? {}).map(([key, label]) => ({ key, label })); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<typeof this.d>('/portal/helpline').subscribe({ next: (d) => (this.d = d), error: (e) => (this.error = errorText(e, 'Could not load.')) });
  }

  newIssue(): void { this.form = { category: this.categoryList[0]?.key ?? 'OTHER', subject: '', message: '', confidential: false }; }

  raise(): void {
    this.busy = true;
    this.api.post<{ id: number; message: string }>('/portal/helpline', this.form).subscribe({
      next: (r) => { this.busy = false; this.form = null; this.message = r.message; this.open(r.id); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send.'); }
    });
  }

  open(id: number): void {
    this.reply = '';
    this.api.get<Row>(`/portal/helpline/${id}`).subscribe({ next: (t) => (this.t = t), error: (e) => (this.error = errorText(e, 'Could not open.')) });
  }

  send(solved: boolean): void {
    if (!this.t) return;
    const id = this.t['id'];
    this.busy = true;
    this.api.post<{ message: string }>(`/portal/helpline/${id}`, solved ? { solved: true } : { message: this.reply }).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.open(id); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send.'); }
    });
  }
}
