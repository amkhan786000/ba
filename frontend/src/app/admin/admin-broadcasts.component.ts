import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, switchMap, of } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { RoleOption } from './admin-users.component';
import { uploadUrl } from '../shared/format';

type AudienceType = 'USERS' | 'ROLES' | 'CHAPTER_LEADS';

interface Person { id: number; user_id: string; name: string; email: string | null; role_name: string | null; has_email: boolean }
interface Preview { recipients: number; withEmail: number; withoutEmail: number; audience: string }
interface Broadcast {
  broadcast_id: number; subject: string; body: string; audience: string; recipients: number;
  sent: number; skipped: number; failed: number; status: 'SENDING' | 'SENT'; created_at: string | null; sent_by_name: string | null;
  attachments: { attachment_id: number; file_name: string; file_path: string; size_bytes: number }[];
}

/** Same limits as the server: mail servers reject larger emails. */
const MAX_FILES = 5;
const MAX_BYTES = 15 * 1024 * 1024;
const ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp,.gif,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.txt';

/**
 * Admin > Broadcast Messages: email one or more people, every user of some roles, or every chapter lead.
 * Users also get the message in their notifications. Sending happens in the background; the history shows
 * how many emails went out.
 */
@Component({
  selector: 'app-admin-broadcasts',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe],
  styles: [`
    .chip { display: inline-flex; align-items: center; background: #eef2f7; border-radius: 1rem; padding: .15rem .6rem; margin: 0 .35rem .35rem 0; font-size: .85rem; }
    .chip button { border: 0; background: none; margin-left: .35rem; line-height: 1; padding: 0; color: #6c757d; }
    .picker { position: relative; }
    .picker .results { position: absolute; z-index: 10; left: 0; right: 0; max-height: 260px; overflow-y: auto; background: #fff; border: 1px solid #dee2e6; border-radius: .25rem; box-shadow: 0 4px 12px rgba(0,0,0,.08); }
    .picker .results button { display: block; width: 100%; text-align: left; border: 0; background: none; padding: .45rem .75rem; }
    .picker .results button:hover { background: #f5f7fa; }
    .roles { columns: 2; }
    .body-cell { max-width: 360px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Broadcast Messages</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="canSend">
      <div class="card-body">
        <h4 class="header-title mb-3">New message</h4>
        <form #f="ngForm" (ngSubmit)="send()">
          <div class="form-group">
            <label class="d-block">Send to</label>
            <div class="btn-group btn-group-toggle flex-wrap">
              <button type="button" class="btn btn-sm" [ngClass]="type === 'USERS' ? 'btn-primary' : 'btn-outline-primary'" (click)="setType('USERS')">
                <i class="mdi mdi-account"></i> Specific people
              </button>
              <button type="button" class="btn btn-sm" [ngClass]="type === 'ROLES' ? 'btn-primary' : 'btn-outline-primary'" (click)="setType('ROLES')">
                <i class="mdi mdi-account-group"></i> Everyone with a role
              </button>
              <button type="button" class="btn btn-sm" [ngClass]="type === 'CHAPTER_LEADS' ? 'btn-primary' : 'btn-outline-primary'" (click)="setType('CHAPTER_LEADS')">
                <i class="mdi mdi-map-marker-multiple"></i> All chapter leads
              </button>
            </div>
          </div>

          <!-- Specific people -->
          <div class="form-group" *ngIf="type === 'USERS'">
            <div class="mb-1">
              <span *ngFor="let p of people" class="chip">
                {{ p.name }} <small class="text-muted ml-1">{{ p.user_id }}</small>
                <i *ngIf="!p.has_email" class="mdi mdi-email-off-outline text-warning ml-1" title="No email address: only an in-app notification"></i>
                <button type="button" (click)="removePerson(p)" [attr.aria-label]="'Remove ' + p.name">&times;</button>
              </span>
            </div>
            <div class="picker">
              <input class="form-control" name="personSearch" [(ngModel)]="search" (ngModelChange)="search$.next($event)" autocomplete="off"
                     placeholder="Type a name, email or user ID to add someone">
              <div class="results" *ngIf="results.length">
                <button type="button" *ngFor="let r of results" (click)="addPerson(r)">
                  <strong>{{ r.name }}</strong> <small class="text-muted">{{ r.user_id }} · {{ r.role_name || 'No role' }}</small>
                  <div class="small" [class.text-warning]="!r.has_email">{{ r.has_email ? r.email : 'No email address' }}</div>
                </button>
              </div>
            </div>
          </div>

          <!-- Roles -->
          <div class="form-group" *ngIf="type === 'ROLES'">
            <div class="roles">
              <div class="custom-control custom-checkbox" *ngFor="let r of roles">
                <input type="checkbox" class="custom-control-input" [id]="'role' + r.roleId" [checked]="roleIds.has(r.roleId)" (change)="toggleRole(r.roleId)">
                <label class="custom-control-label" [for]="'role' + r.roleId">All {{ r.roleName }}</label>
              </div>
            </div>
            <small class="text-muted">Inactive users are left out.</small>
          </div>

          <p *ngIf="type === 'CHAPTER_LEADS'" class="text-muted small">The lead email saved on each active chapter (Admin &gt; Chapters).</p>

          <div class="alert py-2" *ngIf="preview" [ngClass]="preview.withEmail ? 'alert-light border' : 'alert-warning'">
            <i class="mdi mdi-account-multiple-outline"></i>
            <strong>{{ preview.recipients }}</strong> recipient(s), <strong>{{ preview.withEmail }}</strong> with an email address.
            <span *ngIf="preview.withoutEmail"> {{ preview.withoutEmail }} without one will {{ type === 'CHAPTER_LEADS' ? 'be skipped' : 'only see it in their notifications' }}.</span>
          </div>

          <div class="form-group">
            <label for="subject">Subject</label>
            <input id="subject" name="subject" class="form-control" maxlength="200" [(ngModel)]="subject" required>
          </div>
          <div class="form-group">
            <label for="body">Message</label>
            <textarea id="body" name="body" class="form-control" rows="7" [(ngModel)]="body" required
                      placeholder="Each email starts with 'Dear <name>,' and ends with 'Regards, Rahbar - Bihar Anjuman'."></textarea>
          </div>
          <div class="form-group">
            <label class="d-block">Attachments <small class="text-muted">(optional: up to 5 files, 15 MB together)</small></label>
            <div class="mb-1">
              <span *ngFor="let file of files" class="chip">
                <i class="mdi mdi-paperclip mr-1"></i>{{ file.name }} <small class="text-muted ml-1">{{ size(file.size) }}</small>
                <button type="button" (click)="removeFile(file)" [attr.aria-label]="'Remove ' + file.name">&times;</button>
              </span>
            </div>
            <label class="btn btn-sm btn-outline-secondary mb-0" [class.disabled]="files.length >= maxFiles">
              <i class="mdi mdi-paperclip"></i> Add files
              <input type="file" multiple hidden [accept]="accept" (change)="addFiles($event)" [disabled]="files.length >= maxFiles">
            </label>
            <small class="text-muted ml-2" *ngIf="files.length">{{ size(totalSize) }} of 15 MB</small>
          </div>

          <button type="submit" class="btn btn-primary" [disabled]="f.invalid || sending || !preview?.recipients">
            <span *ngIf="sending" class="spinner-border spinner-border-sm mr-1"></span>
            <i *ngIf="!sending" class="mdi mdi-send"></i> Send{{ preview?.recipients ? ' to ' + preview?.recipients + ' recipient(s)' : '' }}
          </button>
        </form>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <h4 class="header-title mb-3">Sent messages</h4>
        <div class="table-responsive">
          <table class="table table-sm table-striped mb-0">
            <thead><tr><th>Sent</th><th>Subject</th><th>To</th><th>Delivery</th><th>By</th></tr></thead>
            <tbody>
              <tr *ngIf="!history.length"><td colspan="5" class="text-center text-muted">No messages sent yet.</td></tr>
              <tr *ngFor="let b of history | paginate: pg.page : pg.size">
                <td class="text-nowrap">{{ when(b.created_at) }}</td>
                <td class="body-cell" [title]="b.body">
                  <strong>{{ b.subject }}</strong>
                  <div class="small text-muted body-cell">{{ b.body }}</div>
                  <div *ngIf="b.attachments.length" class="small">
                    <a *ngFor="let a of b.attachments" [href]="link(a.file_path)" target="_blank" rel="noopener" class="mr-2">
                      <i class="mdi mdi-paperclip"></i>{{ a.file_name }}
                    </a>
                  </div>
                </td>
                <td><small>{{ b.audience }}</small></td>
                <td class="text-nowrap">
                  <span *ngIf="b.status === 'SENDING'" class="badge badge-info"><span class="spinner-border spinner-border-sm"></span> Sending to {{ b.recipients }}…</span>
                  <ng-container *ngIf="b.status === 'SENT'">
                    <span class="badge badge-success">{{ b.sent }} sent</span>
                    <span *ngIf="b.skipped" class="badge badge-light ml-1" title="No usable email address">{{ b.skipped }} no email</span>
                    <span *ngIf="b.failed" class="badge badge-danger ml-1" title="See the backend log for the reason">{{ b.failed }} failed</span>
                  </ng-container>
                </td>
                <td><small>{{ b.sent_by_name || '--' }}</small></td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [state]="pg" [total]="history.length"></app-pager>
      </div>
    </div>
  `
})
export class AdminBroadcastsComponent implements OnInit, OnDestroy {
  readonly pg = new PageState(10);
  type: AudienceType = 'USERS';
  roles: RoleOption[] = [];
  roleIds = new Set<number>();
  people: Person[] = [];
  results: Person[] = [];
  search = '';
  readonly search$ = new Subject<string>();
  private readonly preview$ = new Subject<void>();
  preview: Preview | null = null;
  subject = '';
  body = '';
  files: File[] = [];
  readonly maxFiles = MAX_FILES;
  readonly accept = ACCEPT;
  sending = false;
  history: Broadcast[] = [];
  message = '';
  error = '';
  private subs: Subscription[] = [];
  private poll?: ReturnType<typeof setInterval>;

  constructor(private api: ApiService, private auth: AuthService) {}

  get canSend(): boolean { return this.auth.can('MESSAGES', 'EDIT'); }

  ngOnInit(): void {
    this.loadHistory();
    if (!this.canSend) return;
    this.api.get<RoleOption[]>('/admin/roles').subscribe({ next: (r) => (this.roles = r) });
    this.subs.push(this.search$.pipe(
      debounceTime(250),
      switchMap((q) => q.trim().length < 2 ? of([] as Person[]) : this.api.get<Person[]>('/admin/broadcasts/recipients', { q }))
    ).subscribe({ next: (r) => (this.results = r.filter((p) => !this.people.some((x) => x.id === p.id))) }));
    this.subs.push(this.preview$.pipe(
      debounceTime(200),
      switchMap(() => this.hasAudience ? this.api.post<Preview>('/admin/broadcasts/preview', this.audience) : of(null))
    ).subscribe({ next: (p) => (this.preview = p), error: () => (this.preview = null) }));
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    if (this.poll) clearInterval(this.poll);
  }

  private get audience(): Record<string, unknown> {
    return { type: this.type, userIds: this.people.map((p) => p.id), roleIds: [...this.roleIds] };
  }

  private get hasAudience(): boolean {
    return this.type === 'CHAPTER_LEADS' || (this.type === 'USERS' ? this.people.length > 0 : this.roleIds.size > 0);
  }

  setType(t: AudienceType): void { this.type = t; this.refreshPreview(); }

  addPerson(p: Person): void {
    this.people = [...this.people, p];
    this.results = [];
    this.search = '';
    this.refreshPreview();
  }

  removePerson(p: Person): void { this.people = this.people.filter((x) => x.id !== p.id); this.refreshPreview(); }

  toggleRole(id: number): void {
    if (this.roleIds.has(id)) this.roleIds.delete(id); else this.roleIds.add(id);
    this.refreshPreview();
  }

  private refreshPreview(): void {
    if (!this.hasAudience) { this.preview = null; return; }
    this.preview$.next();
  }

  get totalSize(): number { return this.files.reduce((sum, f) => sum + f.size, 0); }

  addFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const picked = Array.from(input.files ?? []);
    input.value = ''; // so the same file can be picked again after removing it
    const allowed = ACCEPT.split(',');
    for (const file of picked) {
      const ext = '.' + (file.name.split('.').pop() ?? '').toLowerCase();
      if (!allowed.includes(ext)) { this.error = `"${file.name}" can't be attached (allowed: ${ACCEPT.replace(/\./g, '').replace(/,/g, ', ')}).`; continue; }
      if (this.files.length >= MAX_FILES) { this.error = `You can attach at most ${MAX_FILES} files.`; break; }
      if (this.totalSize + file.size > MAX_BYTES) { this.error = 'Attachments can be at most 15 MB together.'; continue; }
      this.files = [...this.files, file];
    }
  }

  removeFile(file: File): void { this.files = this.files.filter((f) => f !== file); }

  size(bytes: number): string {
    return bytes < 1024 * 1024 ? Math.max(1, Math.round(bytes / 1024)) + ' KB' : (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  link(path: string): string { return uploadUrl(path) ?? '#'; }

  send(): void {
    if (!this.preview?.recipients) return;
    const extra = this.files.length ? ` with ${this.files.length} attachment(s)` : '';
    if (!confirm(`Send "${this.subject}"${extra} to ${this.preview.recipients} recipient(s)?`)) return;
    this.sending = true;
    this.error = '';
    const form = new FormData();
    form.append('subject', this.subject);
    form.append('body', this.body);
    form.append('audience', JSON.stringify(this.audience));
    this.files.forEach((f) => form.append('files', f, f.name));
    this.api.post<Broadcast>('/admin/broadcasts', form).subscribe({
      next: () => {
        this.sending = false;
        this.message = 'Your message is being sent. The list below shows when it has gone out.';
        this.subject = '';
        this.body = '';
        this.files = [];
        this.people = [];
        this.roleIds.clear();
        this.preview = null;
        this.loadHistory();
      },
      error: (e) => { this.sending = false; this.error = errorText(e, 'Could not send the message.'); }
    });
  }

  /** Reloads the history; keeps checking every few seconds while a message is still being sent. */
  private loadHistory(): void {
    this.api.get<Broadcast[]>('/admin/broadcasts').subscribe({
      next: (h) => {
        this.history = h;
        const sending = h.some((b) => b.status === 'SENDING');
        if (sending && !this.poll) this.poll = setInterval(() => this.loadHistory(), 4000);
        if (!sending && this.poll) { clearInterval(this.poll); this.poll = undefined; }
      },
      error: (e) => (this.error = errorText(e, 'Could not load sent messages.'))
    });
  }

  when(v: string | null): string {
    if (!v) return '--';
    const d = new Date(v);
    return isNaN(d.getTime()) ? v : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  }
}
