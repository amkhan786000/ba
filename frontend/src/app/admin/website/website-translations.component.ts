import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { errorText } from '../../shared/alerts/alerts.component';
import { WebsiteMarkdownPipe } from './website-markdown.pipe';
import { WebsiteRevisionsComponent } from './website-revisions.component';

interface Row { lang: string; field: string; value?: string }
export interface TranslatableField { key: string; label: string; markdown: boolean }

const LANGS = [
  { code: 'ur', label: 'Urdu · اردو', dir: 'rtl' },
  { code: 'hi', label: 'Hindi · हिन्दी', dir: 'ltr' }
] as const;

/** Urdu / Hindi versions of a record's texts. An empty field shows the English text on the website. */
@Component({
  selector: 'app-website-translations',
  standalone: true,
  imports: [CommonModule, FormsModule, WebsiteMarkdownPipe, WebsiteRevisionsComponent],
  template: `
    <div class="card">
      <div class="card-body">
        <h5 class="mt-0">Translations</h5>
        <p class="text-muted small">Leave a field empty to show the English text in that language.</p>
        <div class="btn-group mb-3" role="tablist">
          <button *ngFor="let l of langs" type="button" class="btn btn-sm" [class.btn-primary]="lang === l.code"
                  [class.btn-outline-primary]="lang !== l.code" (click)="lang = l.code">{{ l.label }}</button>
        </div>
        <p *ngIf="loading" class="text-muted">Loading…</p>
        <fieldset [disabled]="!canEdit">
          <div class="form-group" *ngFor="let f of fields">
            <label>{{ f.label }}</label>
            <textarea *ngIf="f.markdown" class="form-control" rows="8" [dir]="dir" [ngModel]="draft[key(f.key)] ?? ''"
                      (ngModelChange)="draft[key(f.key)] = $event" [placeholder]="english[f.key] || ''"></textarea>
            <input *ngIf="!f.markdown" class="form-control" [dir]="dir" [ngModel]="draft[key(f.key)] ?? ''"
                   (ngModelChange)="draft[key(f.key)] = $event" [placeholder]="english[f.key] || ''">
            <small class="text-muted d-block">English: {{ short(english[f.key]) }}</small>
            <details *ngIf="f.markdown && draft[key(f.key)]" class="mt-1"><summary class="small">Preview</summary>
              <div class="border rounded p-2 website-prose" [dir]="dir" [innerHTML]="draft[key(f.key)] | websiteMarkdown"></div>
            </details>
            <app-website-revisions *ngIf="f.markdown" [target]="'tr:' + type + ':' + id + ':' + f.key" [lang]="lang"
                                   (use)="draft[key(f.key)] = $event"></app-website-revisions>
          </div>
        </fieldset>
        <p *ngIf="error" class="text-danger">{{ error }}</p>
        <p *ngIf="notice" class="text-success">{{ notice }}</p>
        <button *ngIf="canEdit" type="button" class="btn btn-primary" [disabled]="busy || !dirty" (click)="save()">
          {{ busy ? 'Saving…' : 'Save ' + langLabel }}
        </button>
      </div>
    </div>
  `
})
export class WebsiteTranslationsComponent implements OnChanges {
  @Input({ required: true }) type = '';
  @Input({ required: true }) id = 0;
  @Input({ required: true }) fields: TranslatableField[] = [];
  /** The English record, shown as a reference. */
  @Input({ required: true }) english: Record<string, unknown> = {};
  @Input() canEdit = true;

  readonly langs = LANGS;
  lang: 'ur' | 'hi' = 'ur';
  draft: Record<string, string | undefined> = {};
  private saved: Record<string, string> = {};
  loading = false;
  busy = false;
  error = '';
  notice = '';

  constructor(private api: ApiService) {}

  get dir(): string { return LANGS.find((l) => l.code === this.lang)!.dir; }
  get langLabel(): string { return LANGS.find((l) => l.code === this.lang)!.label.split(' ·')[0]; }
  get dirty(): boolean {
    return this.fields.some((f) => (this.draft[this.key(f.key)] ?? '') !== (this.saved[this.key(f.key)] ?? ''));
  }

  /** Loads again only for another record (not when the English text being edited changes). */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['type'] || changes['id']) this.load();
  }

  key(field: string): string { return `${this.lang}:${field}`; }

  short(v: unknown): string {
    const s = v == null ? '' : String(v);
    return s ? (s.length > 120 ? s.slice(0, 120) + '…' : s) : '(empty)';
  }

  private load(): void {
    if (!this.id) return;
    this.loading = true;
    this.api.get<Row[]>('/admin/website/translations', { type: this.type, id: this.id }).subscribe({
      next: (rows) => {
        this.loading = false;
        const m: Record<string, string> = {};
        for (const r of rows) if (r.value) m[`${r.lang}:${r.field}`] = r.value;
        this.saved = m;
        this.draft = { ...m };
      },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the translations.'); }
    });
  }

  save(): void {
    this.error = '';
    this.notice = '';
    const changed = this.fields.filter((f) => (this.draft[this.key(f.key)] ?? '') !== (this.saved[this.key(f.key)] ?? ''));
    if (!changed.length) return;
    this.busy = true;
    forkJoin(changed.map((f) => this.api.put('/admin/website/translations', {
      type: this.type, id: this.id, lang: this.lang, field: f.key, value: this.draft[this.key(f.key)] ?? ''
    }))).subscribe({
      next: () => { this.busy = false; this.notice = `${this.langLabel} saved.`; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save.'); }
    });
  }
}
