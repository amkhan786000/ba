import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, switchMap } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { EmailTemplateRow } from './admin-email-templates.component';

/** Edit the wording of one kind of email, with the placeholders it can use and a live preview. */
@Component({
  selector: 'app-admin-email-template-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  styles: [`
    .chip { display: inline-block; background: #eef2f7; border-radius: 1rem; padding: .15rem .6rem; margin: 0 .35rem .35rem 0;
            font-size: .85rem; border: 0; font-family: monospace; }
    .chip.required { background: #fde8d7; }
    .preview { white-space: pre-wrap; font-family: inherit; background: #f8f9fa; border-radius: .25rem; padding: 1rem; margin: 0; min-height: 200px; }
    textarea { font-family: inherit; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Email Template: {{ t?.label }}</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row" *ngIf="t">
      <div class="col-lg-7">
        <div class="card">
          <div class="card-body">
            <p class="text-muted">{{ t.description }}</p>
            <div class="form-group">
              <label for="subject">Subject</label>
              <input id="subject" class="form-control" maxlength="300" [(ngModel)]="subject" (ngModelChange)="changed()" (focus)="field = 'subject'" [disabled]="!canEdit">
            </div>
            <div class="form-group">
              <label for="body">Text</label>
              <textarea id="body" class="form-control" rows="14" [(ngModel)]="body" (ngModelChange)="changed()" (focus)="field = 'body'" [disabled]="!canEdit"></textarea>
            </div>
            <div class="mb-3">
              <label class="d-block mb-1">Placeholders <small class="text-muted">(click to insert)</small></label>
              <button *ngFor="let p of t.placeholders" type="button" class="chip" [class.required]="t.required.includes(p)"
                      [title]="t.required.includes(p) ? 'Must stay in this email' : ''" (click)="insert(p)" [disabled]="!canEdit">{{ '{{' + p + '}}' }}</button>
              <small class="d-block text-muted">{{ '{{name}}' }} is the recipient's name. Orange ones must stay in the email.</small>
            </div>
            <div class="d-flex flex-wrap justify-content-between">
              <a routerLink="/admin/email-templates" class="btn btn-light mb-1">Back</a>
              <div>
                <button *ngIf="canEdit" type="button" class="btn btn-outline-secondary mr-1 mb-1" (click)="useDefault()"
                        title="Fill in the built-in wording (not saved until you click Save)">Start from built-in wording</button>
                <button *ngIf="isSuperAdmin && t.customised" type="button" class="btn btn-danger mr-1 mb-1" (click)="remove()">Delete</button>
                <button *ngIf="canEdit" type="button" class="btn btn-primary mb-1" (click)="save()" [disabled]="saving || !subject.trim() || !body.trim()">
                  {{ saving ? 'Saving…' : 'Save Template' }}
                </button>
              </div>
            </div>
            <small *ngIf="t.customised" class="d-block text-muted mt-2">Custom wording, last changed {{ t.updated_at | date: 'd MMM yyyy, h:mm a' }}{{ t.updated_by_name ? ' by ' + t.updated_by_name : '' }}.</small>
            <small *ngIf="!t.customised" class="d-block text-muted mt-2">This email uses the built-in wording. Saving creates a custom template.</small>
          </div>
        </div>
      </div>
      <div class="col-lg-5">
        <div class="card">
          <div class="card-body">
            <h5 class="header-title mb-2">Preview <small class="text-muted">(with sample values)</small></h5>
            <p class="mb-2"><strong>Subject:</strong> {{ preview?.subject }}</p>
            <pre class="preview">{{ preview?.body }}</pre>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminEmailTemplateEditComponent implements OnInit, OnDestroy {
  /** From the route parameter :key. */
  @Input() key?: string;

  t: EmailTemplateRow | null = null;
  subject = '';
  body = '';
  field: 'subject' | 'body' = 'body';
  preview: { subject: string; body: string } | null = null;
  saving = false;
  message = '';
  error = '';
  private readonly preview$ = new Subject<void>();
  private sub?: Subscription;

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  get canEdit(): boolean { return this.auth.can('EMAIL_TEMPLATES', 'EDIT'); }
  get isSuperAdmin(): boolean { return this.auth.currentUser()?.roleId === 1; }

  ngOnInit(): void {
    this.sub = this.preview$.pipe(
      debounceTime(300),
      switchMap(() => this.api.post<{ subject: string; body: string }>(`/admin/email-templates/${this.key}/preview`, { subject: this.subject, body: this.body }))
    ).subscribe({ next: (p) => (this.preview = p) });
    this.load();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  private load(): void {
    this.api.get<EmailTemplateRow>(`/admin/email-templates/${this.key}`).subscribe({
      next: (t) => { this.t = t; this.subject = t.subject; this.body = t.body; this.changed(); },
      error: (e) => (this.error = errorText(e, 'Could not load the template.'))
    });
  }

  changed(): void { this.preview$.next(); }

  /** Inserts {{placeholder}} at the cursor of the field last focused. */
  insert(p: string): void {
    const el = document.getElementById(this.field) as HTMLInputElement | HTMLTextAreaElement | null;
    const token = '{{' + p + '}}';
    const value = this.field === 'subject' ? this.subject : this.body;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    const next = value.slice(0, start) + token + value.slice(end);
    if (this.field === 'subject') this.subject = next; else this.body = next;
    this.changed();
    setTimeout(() => { el?.focus(); el?.setSelectionRange(start + token.length, start + token.length); });
  }

  useDefault(): void {
    if (!this.t) return;
    this.subject = this.t.default_subject;
    this.body = this.t.default_body;
    this.changed();
  }

  save(): void {
    this.saving = true;
    this.error = '';
    this.api.put<EmailTemplateRow>(`/admin/email-templates/${this.key}`, { subject: this.subject, body: this.body }).subscribe({
      next: (t) => { this.saving = false; this.t = t; this.message = 'Template saved. New emails of this kind use it from now on.'; },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the template.'); }
    });
  }

  remove(): void {
    if (!this.t || !confirm(`Delete the custom wording of "${this.t.label}"? The email goes back to the built-in wording.`)) return;
    this.api.delete(`/admin/email-templates/${this.key}`).subscribe({
      next: () => this.router.navigate(['/admin/email-templates']),
      error: (e) => (this.error = errorText(e, 'Could not delete the template.'))
    });
  }
}
