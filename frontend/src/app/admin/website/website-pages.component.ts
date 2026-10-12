import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { WebsiteMarkdownPipe } from './website-markdown.pipe';
import { WebsiteRevisionsComponent } from './website-revisions.component';
import { WebsiteRecordsComponent } from './website-records.component';
import { SLOT_GROUPS, SLOTS, Slot } from './website-content-registry';
import { AppDatePipe } from '../../shared/app-date';

interface ContentRow { key: string; lang: string; value?: string; changedBy?: string; changedAt?: string }

const LANGS = [
  { code: 'en', label: 'English', dir: 'ltr' },
  { code: 'ur', label: 'Urdu · اردو', dir: 'rtl' },
  { code: 'hi', label: 'Hindi · हिन्दी', dir: 'ltr' }
] as const;
type Lang = (typeof LANGS)[number]['code'];

/**
 * Admin > Website > Pages: the text of the website's fixed pages (home, about, ...) in English, Urdu and Hindi, and
 * the custom pages added to the website.
 */
@Component({
  selector: 'app-website-pages',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent, WebsiteMarkdownPipe, WebsiteRevisionsComponent, WebsiteRecordsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Pages</h4></div></div></div>
    <ul class="nav nav-tabs mb-3">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="mode === 'text'" (click)="$event.preventDefault(); mode = 'text'">Page text</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="mode === 'pages'" (click)="$event.preventDefault(); mode = 'pages'">Custom pages</a></li>
    </ul>

    <app-website-records *ngIf="mode === 'pages'" resource="pages" [embedded]="true"></app-website-records>

    <ng-container *ngIf="mode === 'text'">
      <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
      <div class="card">
        <div class="card-body">
          <p class="text-muted mb-2">The text of the website's fixed pages. {{ lang === 'en' ? 'Empty means the built-in text is shown.' : 'Empty means the English text is shown.' }}
            In one-line texts, *word* shows in the gold accent style.</p>
          <div class="row">
            <div class="col-12 col-md-6 mb-2">
              <select class="form-control" [(ngModel)]="groupId" (ngModelChange)="drafts = {}" aria-label="Page">
                <option *ngFor="let g of groups" [ngValue]="g.id">{{ g.label }}</option>
              </select>
            </div>
            <div class="col-12 col-md-6 mb-2">
              <div class="btn-group" role="group" aria-label="Language">
                <button *ngFor="let l of langs" type="button" class="btn btn-sm" [class.btn-primary]="lang === l.code"
                        [class.btn-outline-primary]="lang !== l.code" (click)="lang = l.code; drafts = {}">{{ l.label }}</button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <p *ngIf="loading" class="text-center"><span class="spinner-border spinner-border-sm"></span></p>
      <div class="card" *ngFor="let s of group.slots; trackBy: byKey">
        <div class="card-body">
          <div class="d-flex flex-wrap justify-content-between mb-1">
            <strong>{{ s.label }}</strong>
            <span class="small text-muted">{{ typeLabel(s) }} · {{ s.key }}</span>
          </div>
          <fieldset [disabled]="!canEdit">
            <input *ngIf="s.type === 'inline'" class="form-control" [dir]="dir" [ngModel]="value(s.key)" (ngModelChange)="edit(s.key, $event)"
                   [placeholder]="fallback(s)" [attr.aria-label]="s.label">
            <textarea *ngIf="s.type !== 'inline'" class="form-control" [rows]="s.type === 'markdown' ? 10 : s.type === 'list' ? 8 : 3" [dir]="dir"
                      [ngModel]="value(s.key)" (ngModelChange)="edit(s.key, $event)" [placeholder]="fallback(s)" [attr.aria-label]="s.label"></textarea>
          </fieldset>
          <details *ngIf="s.type === 'markdown' && (value(s.key) || fallback(s))" class="mt-1"><summary class="small">Preview</summary>
            <div class="border rounded p-2 website-prose" [dir]="dir" [innerHTML]="(value(s.key) || fallback(s)) | websiteMarkdown"></div>
          </details>
          <div class="d-flex flex-wrap align-items-center justify-content-between mt-2">
            <span class="small text-muted">
              <ng-container *ngIf="saved(s.key) as r; else builtIn">Changed by {{ r.changedBy || '—' }} on {{ r.changedAt | appDate: 'datetime' }}</ng-container>
              <ng-template #builtIn>{{ lang === 'en' ? 'Built-in text' : 'Not translated: English is shown' }}</ng-template>
            </span>
            <span *ngIf="canEdit">
              <button *ngIf="saved(s.key)" type="button" class="btn btn-xs btn-outline-secondary mr-1" (click)="save(s.key, '')">{{ lang === 'en' ? 'Use built-in text' : 'Remove translation' }}</button>
              <button type="button" class="btn btn-xs btn-primary" [disabled]="!isDirty(s.key) || busy" (click)="save(s.key, value(s.key))">Save</button>
            </span>
          </div>
          <app-website-revisions [target]="'site:' + s.key" [lang]="lang" (use)="edit(s.key, $event)"></app-website-revisions>
        </div>
      </div>
    </ng-container>
  `
})
export class WebsitePagesComponent implements OnInit {
  readonly groups = SLOT_GROUPS;
  readonly langs = LANGS;
  mode: 'text' | 'pages' = 'text';
  groupId = SLOT_GROUPS[0].id;
  lang: Lang = 'en';
  rows: ContentRow[] = [];
  /** Unsaved edits, keyed "lang:key". */
  drafts: Record<string, string> = {};
  loading = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('WEBSITE_PAGES', 'EDIT'); }
  get group() { return SLOT_GROUPS.find((g) => g.id === this.groupId) ?? SLOT_GROUPS[0]; }
  get dir(): string { return LANGS.find((l) => l.code === this.lang)!.dir; }

  byKey(_: number, s: Slot): string { return s.key; }

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.loading = true;
    this.api.get<ContentRow[]>('/admin/website/content').subscribe({
      next: (r) => { this.loading = false; this.rows = r; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the page text.'); }
    });
  }

  typeLabel(s: Slot): string {
    return { inline: 'One line', text: 'Paragraph', markdown: 'Formatted text', list: 'List: one item per line' }[s.type];
  }

  saved(key: string, lang: string = this.lang): ContentRow | undefined {
    return this.rows.find((r) => r.lang === lang && r.key === key);
  }

  value(key: string): string {
    return this.drafts[`${this.lang}:${key}`] ?? this.saved(key)?.value ?? '';
  }

  /** What the website shows while this field is empty. */
  fallback(s: Slot): string {
    return this.lang === 'en' ? s.default : this.saved(s.key, 'en')?.value ?? s.default;
  }

  isDirty(key: string): boolean {
    const d = this.drafts[`${this.lang}:${key}`];
    return d !== undefined && d !== (this.saved(key)?.value ?? '');
  }

  edit(key: string, v: string): void {
    this.drafts = { ...this.drafts, [`${this.lang}:${key}`]: v };
  }

  save(key: string, value: string): void {
    this.message = '';
    this.error = '';
    this.busy = true;
    const lang = this.lang;
    this.api.put('/admin/website/content', { key, lang, value }).subscribe({
      next: () => {
        this.busy = false;
        const d = { ...this.drafts };
        delete d[`${lang}:${key}`];
        this.drafts = d;
        this.message = `Saved “${SLOTS[key]?.label ?? key}”. The website shows it within a minute.`;
        this.load();
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }
}
