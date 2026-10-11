import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../../shared/pager/server-table';
import { CardTableDirective } from '../../shared/card-table.directive';
import { WebsiteMarkdownPipe } from './website-markdown.pipe';
import { WebsiteRevisionsComponent } from './website-revisions.component';
import { TranslatableField, WebsiteTranslationsComponent } from './website-translations.component';
import { RefList, WebsiteField, WebsiteResource, slugify, toLocalInput, websiteResource } from './website-resources';

type Row = Record<string, unknown> & { id?: number };
interface Option { id: number; name: string }
interface ImageOption { id: number; title: string; album?: string; url: string }
interface Lookups { projects: Option[]; chapters: Option[]; centers: Option[]; categories: Option[]; images: ImageOption[] }

const STATUS_LABELS: Record<string, string> = { PUBLISHED: 'Published', IN_REVIEW: 'Waiting for review', DRAFT: 'Draft' };
const MAX_PHOTO = 10 * 1024 * 1024;

/**
 * Admin > Website > Blog, Success Stories, Directory, Projects, News & Events, Messages and custom pages: one screen
 * that lists and edits the records described in website-resources.ts. The resource comes from the route
 * (data.resource) or from the [resource] input (Pages embeds the custom pages).
 */
@Component({
  selector: 'app-website-records',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, CardTableDirective, WebsiteMarkdownPipe,
    WebsiteRevisionsComponent, WebsiteTranslationsComponent],
  template: `
    <div class="row" *ngIf="!embedded"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ res.label }}</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ng-container *ngIf="draft as d; else listView">
      <div class="card">
        <div class="card-body">
          <h5 class="mt-0">{{ d.id ? (canEdit ? 'Edit' : 'View') : 'New' }} · {{ res.label }}</h5>
          <fieldset [disabled]="!canEdit">
            <div class="row">
              <ng-container *ngFor="let f of formFields">
                <ng-container [ngSwitch]="f.type">
                  <div *ngSwitchCase="'info'" class="col-md-6 form-group" [hidden]="!d[f.key]">
                    <label class="text-muted small d-block mb-0">{{ f.label }}</label><strong>{{ display(f, d[f.key]) }}</strong>
                  </div>
                  <div *ngSwitchCase="'bool'" class="col-12 form-group mb-2">
                    <div class="custom-control custom-checkbox">
                      <input type="checkbox" class="custom-control-input" [id]="'f-' + f.key" [(ngModel)]="d[f.key]">
                      <label class="custom-control-label" [for]="'f-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                    </div>
                  </div>
                  <div *ngSwitchCase="'textarea'" class="col-12 form-group">
                    <label [for]="'f-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                    <textarea class="form-control" rows="3" [id]="'f-' + f.key" [(ngModel)]="d[f.key]"></textarea>
                    <small *ngIf="f.hint" class="text-muted">{{ f.hint }}</small>
                  </div>
                  <div *ngSwitchCase="'markdown'" class="col-12 form-group">
                    <label [for]="'f-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                    <textarea class="form-control text-monospace" rows="14" [id]="'f-' + f.key" [(ngModel)]="d[f.key]" style="font-size: .85rem"></textarea>
                    <small *ngIf="f.hint" class="text-muted">{{ f.hint }}</small>
                    <details *ngIf="d[f.key]" class="mt-1"><summary class="small">Preview</summary>
                      <div class="border rounded p-3 website-prose" [innerHTML]="d[f.key] | websiteMarkdown"></div>
                    </details>
                    <app-website-revisions *ngIf="f.revisions && d.id && res.translate" [target]="'rec:' + res.translate!.type + ':' + d.id + ':' + f.key"
                                           (use)="d[f.key] = $event"></app-website-revisions>
                  </div>
                  <div *ngSwitchCase="'select'" class="col-md-6 form-group">
                    <label [for]="'f-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                    <select class="form-control" [id]="'f-' + f.key" [(ngModel)]="d[f.key]">
                      <option *ngIf="!f.required" [ngValue]="null">—</option>
                      <option *ngFor="let o of f.options" [ngValue]="o">{{ o }}</option>
                    </select>
                  </div>
                  <div *ngSwitchCase="'ref'" class="col-md-6 form-group">
                    <label [for]="'f-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                    <select class="form-control" [id]="'f-' + f.key" [(ngModel)]="d[f.key]">
                      <option *ngIf="!f.required || d[f.key] == null" [ngValue]="null" [disabled]="!!f.required">{{ f.required ? 'Choose…' : f.emptyLabel || '—' }}</option>
                      <option *ngFor="let o of options(f)" [ngValue]="o.id">{{ o.name }}</option>
                    </select>
                    <small *ngIf="f.hint" class="text-muted">{{ f.hint }}</small>
                  </div>
                  <div *ngSwitchCase="'image'" class="col-12 form-group">
                    <label [for]="'f-' + f.key">{{ f.label }}</label>
                    <div class="row">
                      <div class="col-md-6 mb-2">
                        <select class="form-control" [id]="'f-' + f.key" [ngModel]="d[f.key]" (ngModelChange)="d[f.key] = $event" aria-label="Pick a photo">
                          <option [ngValue]="null">— No image —</option>
                          <option *ngFor="let g of lookups.images" [ngValue]="g.url">{{ g.title }}{{ g.album ? ' (' + g.album + ')' : '' }}</option>
                        </select>
                      </div>
                      <div class="col-md-6 mb-2">
                        <input class="form-control" [(ngModel)]="d[f.key]" placeholder="…or paste an image address" aria-label="Image address">
                      </div>
                    </div>
                    <label class="btn btn-sm btn-outline-secondary mb-0" *ngIf="canEdit">
                      <i class="mdi mdi-upload mr-1"></i>{{ uploading ? 'Uploading…' : 'Upload photo' }}
                      <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden (change)="upload(f, d, $event)" [disabled]="uploading">
                    </label>
                    <small *ngIf="f.hint" class="text-muted d-block">{{ f.hint }}</small>
                    <img *ngIf="d[f.key]" [src]="d[f.key]" alt="Selected image" class="d-block mt-2 rounded border" style="max-height: 160px; max-width: 100%">
                  </div>
                  <div *ngSwitchCase="'file'" class="col-12 form-group">
                    <label>{{ f.label }}</label>
                    <div class="d-flex flex-wrap align-items-center">
                      <a *ngIf="d[f.key]" [href]="d[f.key]" target="_blank" rel="noopener" class="mr-3 mb-1"><i class="mdi mdi-file-pdf-box mr-1"></i>{{ (f.nameField && d[f.nameField]) || 'Open the PDF' }}</a>
                      <label class="btn btn-sm btn-outline-secondary mb-1 mr-2" *ngIf="canEdit">
                        <i class="mdi mdi-upload mr-1"></i>{{ uploading ? 'Uploading…' : d[f.key] ? 'Replace PDF' : 'Upload PDF' }}
                        <input type="file" accept="application/pdf" hidden (change)="uploadPdf(f, d, $event)" [disabled]="uploading">
                      </label>
                      <button *ngIf="d[f.key] && canEdit" type="button" class="btn btn-sm btn-link text-danger mb-1" (click)="d[f.key] = null">Remove</button>
                    </div>
                    <small *ngIf="f.hint" class="text-muted d-block">{{ f.hint }}</small>
                  </div>
                  <div *ngSwitchCase="'datetime'" class="col-md-6 form-group">
                    <label [for]="'f-' + f.key">{{ f.label }}</label>
                    <input type="datetime-local" class="form-control" [id]="'f-' + f.key" [(ngModel)]="d[f.key]">
                    <small *ngIf="f.hint" class="text-muted">{{ f.hint }}</small>
                  </div>
                  <div *ngSwitchDefault class="form-group" [class.col-12]="f.key === 'title' || f.key === 'name'" [class.col-md-6]="f.key !== 'title' && f.key !== 'name'">
                    <label [for]="'f-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                    <input class="form-control" [type]="f.type" [id]="'f-' + f.key" [(ngModel)]="d[f.key]"
                           [attr.list]="f.suggest ? 'suggest-' + f.key : null" (blur)="autoSlug(d, f)">
                    <datalist *ngIf="f.suggest" [id]="'suggest-' + f.key"><option *ngFor="let v of suggestions(f.key)" [value]="v"></option></datalist>
                    <small *ngIf="f.hint" class="text-muted">{{ f.hint }}</small>
                  </div>
                </ng-container>
              </ng-container>
            </div>
          </fieldset>
          <p *ngIf="d.id" class="small text-muted mb-3">
            Created by {{ who(d['createdBy']) }} on {{ when(d['createdAt']) }} · last changed by {{ who(d['updatedBy']) }} on {{ when(d['updatedAt']) }}
          </p>
          <div class="d-flex flex-wrap">
            <button *ngIf="canEdit" type="button" class="btn btn-primary mr-2 mb-2" (click)="save()" [disabled]="saving">{{ saving ? 'Saving…' : 'Save' }}</button>
            <button type="button" class="btn btn-light mr-2 mb-2" (click)="draft = null">{{ canEdit ? 'Cancel' : 'Close' }}</button>
            <button *ngIf="d.id && canEdit" type="button" class="btn btn-outline-danger mb-2 ml-auto" (click)="remove(d)">Delete</button>
          </div>
        </div>
      </div>
      <app-website-translations *ngIf="d.id && res.translate" [type]="res.translate!.type" [id]="d.id!" [fields]="translatable"
                                [english]="d" [canEdit]="canEdit"></app-website-translations>
    </ng-container>

    <ng-template #listView>
      <div class="card">
        <div class="card-body">
          <p class="text-muted">{{ res.intro }}<span *ngIf="!canEdit"> You can view but not change them.</span></p>
          <div class="row mb-3">
            <div class="col-12 col-md-6 mb-2 mb-md-0">
              <input class="form-control" placeholder="Search" aria-label="Search" [(ngModel)]="q" (ngModelChange)="table.search()">
            </div>
            <div class="col-12 col-md-6 text-md-right" *ngIf="res.canCreate && canEdit">
              <button type="button" class="btn btn-primary btn-responsive" (click)="startNew()"><i class="mdi mdi-plus mr-1"></i>New</button>
            </div>
          </div>
          <div class="table-responsive">
            <table class="table table-sm table-centered mb-0">
              <thead><tr><th *ngFor="let f of columns">{{ f.label }}</th><th></th></tr></thead>
              <tbody>
                <tr *ngIf="table.loading && !table.rows.length"><td [attr.colspan]="columns.length + 1" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
                <tr *ngIf="!table.loading && !table.rows.length"><td [attr.colspan]="columns.length + 1" class="text-center text-muted">Nothing here yet.</td></tr>
                <tr *ngFor="let row of table.rows">
                  <td *ngFor="let f of columns">
                    <span *ngIf="f.key === 'status'; else plain" class="badge" [ngClass]="statusBadge(row[f.key])">{{ display(f, row[f.key]) }}</span>
                    <ng-template #plain>{{ display(f, row[f.key]) }}</ng-template>
                  </td>
                  <td class="text-right text-nowrap">
                    <button *ngIf="canEdit && toggleAllowed && res.toggle" type="button" class="btn btn-xs btn-outline-secondary mr-1"
                            (click)="toggle(row)">{{ row[res.toggle!.key] ? res.toggle!.on : res.toggle!.off }}</button>
                    <button type="button" class="btn btn-xs btn-outline-primary" (click)="edit(row)">{{ canEdit ? 'Edit' : 'View' }}</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
        </div>
      </div>
    </ng-template>
  `
})
export class WebsiteRecordsComponent implements OnInit, OnChanges, OnDestroy {
  /** Set when embedded (Pages > Custom pages); otherwise taken from the route's data.resource. */
  @Input() resource?: string;
  @Input() embedded = false;

  res: WebsiteResource = websiteResource('blog');
  lookups: Lookups = { projects: [], chapters: [], centers: [], categories: [], images: [] };
  draft: Row | null = null;
  q = '';
  saving = false;
  uploading = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    if (!this.resource) {
      this.route.data.subscribe((data) => this.open(String(data['resource'] ?? 'blog')));
    }
  }

  ngOnChanges(): void {
    if (this.resource) this.open(this.resource);
  }

  private open(path: string): void {
    this.res = websiteResource(path);
    this.translatable = (this.res.translate?.fields ?? []).map((key) => {
      const f = this.res.fields.find((x) => x.key === key);
      return { key, label: f?.label ?? key, markdown: f?.type === 'markdown' || f?.type === 'textarea' };
    });
    this.draft = null;
    this.q = '';
    this.message = '';
    this.error = '';
    this.table.filter();
    this.api.get<Lookups>('/admin/website/lookups').subscribe({ next: (l) => (this.lookups = l), error: () => {} });
  }

  /** The blog: VIEW is enough to write (one's own posts); everything else needs EDIT. */
  get canEdit(): boolean {
    return this.res.section === 'WEBSITE_BLOG' ? this.auth.can('WEBSITE_BLOG', 'VIEW') : this.auth.can(this.res.section, 'EDIT');
  }

  /** Fields this user may see, e.g. "Published" only for publishers. */
  get formFields(): WebsiteField[] {
    return this.res.fields.filter((f) => !f.requires || this.auth.can(f.requires, 'EDIT'));
  }

  get columns(): WebsiteField[] { return this.formFields.filter((f) => f.column); }

  get toggleAllowed(): boolean {
    const t = this.res.toggle;
    return !t?.requires || this.auth.can(t.requires, 'EDIT');
  }

  /** Fields with Urdu / Hindi versions; built once per resource (a new array on every check would reload the panel). */
  translatable: TranslatableField[] = [];

  /** One page of records from the server (search across all of them there), plus the suggestions for the form. */
  readonly table = new ServerTable<Row, PagedResult<Row> & { names: Record<string, string>; suggestions?: Record<string, string[]> }>(
    (p) => this.api.get(`/admin/website/${this.res.path}`, {
      q: this.q, suggest: this.res.fields.filter((f) => f.suggest).map((f) => f.key).join(',') || null, ...p }),
    (e) => (this.error = errorText(e, 'Could not load the list.')));

  ngOnDestroy(): void { this.table.destroy(); }

  /** Names of the people who made changes to the records on this page. */
  get names(): Record<string, string> { return this.table.last?.names ?? {}; }

  private load(): void { this.table.load(); }

  options(f: WebsiteField): Option[] {
    return f.ref ? this.lookups[f.ref as RefList] : [];
  }

  display(f: WebsiteField, v: unknown): string {
    if (f.type === 'ref' && (v === null || v === undefined)) return f.emptyLabel ? 'Others' : '';
    if (v === null || v === undefined || v === '') return '';
    if (f.type === 'ref') return this.options(f).find((o) => o.id === v)?.name ?? String(v);
    if (f.key === 'status') return STATUS_LABELS[String(v)] ?? String(v);
    if (f.type === 'bool') return v ? 'Yes' : 'No';
    if (f.key === 'createdAt' || f.type === 'datetime') return this.when(v);
    return String(v);
  }

  statusBadge(v: unknown): string {
    return v === 'PUBLISHED' ? 'badge-success' : v === 'IN_REVIEW' ? 'badge-warning' : 'badge-light border';
  }

  /** Records sent from the website (or added by a migration) have no Rahbar user. */
  who(id: unknown): string { return (id != null && this.names[String(id)]) || '—'; }

  when(v: unknown): string { return v ? new Date(String(v)).toLocaleString() : '—'; }

  suggestions(key: string): string[] {
    return this.table.last?.suggestions?.[key] ?? [];
  }

  autoSlug(d: Row, f: WebsiteField): void {
    if ((f.key !== 'title' && f.key !== 'name') || !this.res.fields.some((x) => x.key === 'slug') || d['slug']) return;
    const source = String(d[f.key] ?? '');
    if (source) d['slug'] = slugify(source);
  }

  startNew(): void {
    this.message = '';
    this.error = '';
    const d: Row = { ...this.res.defaults };
    // a required dropdown starts on its first choice (e.g. the first blog category, Articles)
    for (const f of this.res.fields) {
      if (f.type === 'ref' && f.required && d[f.key] == null) d[f.key] = this.options(f)[0]?.id ?? null;
    }
    this.draft = d;
  }

  edit(row: Row): void {
    this.message = '';
    this.error = '';
    const d: Row = { ...row };
    for (const f of this.res.fields) if (f.type === 'datetime') d[f.key] = toLocalInput(d[f.key]);
    this.draft = d;
  }

  save(): void {
    const d = this.draft;
    if (!d) return;
    for (const f of this.res.fields) if (f.key === 'title' || f.key === 'name') this.autoSlug(d, f);
    const missing = this.formFields.filter((f) => f.required && f.type !== 'info'
      && (d[f.key] === undefined || d[f.key] === null || String(d[f.key]).trim() === '' || (f.type === 'bool' && d[f.key] !== true)));
    if (missing.length) {
      this.error = 'Please fill in: ' + missing.map((f) => f.label).join(', ');
      return;
    }
    this.persist(d, this.clean(d), 'Saved.', (saved) => (this.draft = d.id ? null : this.asDraft(saved)));
  }

  /** One-click publish / approve / handled from the list. */
  toggle(row: Row): void {
    const t = this.res.toggle;
    if (!t || !row.id) return;
    const body = this.clean(row);
    body[t.key] = !row[t.key];
    const name = row['title'] ?? row['name'] ?? row['fullName'] ?? row['subject'] ?? 'record';
    this.persist(row, body, `Updated “${name}”.`);
  }

  private asDraft(saved: Row): Row | null {
    // a new record with translations stays open, so the Urdu / Hindi versions can be added
    if (!this.res.translate || !saved?.id) return null;
    const d: Row = { ...saved };
    for (const f of this.res.fields) if (f.type === 'datetime') d[f.key] = toLocalInput(d[f.key]);
    return d;
  }

  private persist(d: Row, body: Row, notice: string, after?: (saved: Row) => void): void {
    this.saving = true;
    this.error = '';
    const path = `/admin/website/${this.res.path}`;
    const req = d.id ? this.api.put<Row>(`${path}/${d.id}`, body) : this.api.post<Row>(path, body);
    req.subscribe({
      next: (saved) => {
        this.saving = false;
        after?.(saved);
        this.message = this.res.translate && !d.id ? 'Saved. You can now add the Urdu and Hindi versions below.' : notice;
        this.load();
      },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save.'); }
    });
  }

  remove(d: Row): void {
    if (!d.id || !confirm('Delete this permanently?')) return;
    this.api.delete(`/admin/website/${this.res.path}/${d.id}`).subscribe({
      next: () => { this.draft = null; this.message = 'Deleted.'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete.'))
    });
  }

  /** A photo uploaded from the form; it is kept out of the public gallery. */
  upload(f: WebsiteField, d: Row, e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > MAX_PHOTO) {
      this.error = 'That photo is larger than 10 MB. Please choose a smaller one.';
      return;
    }
    const form = new FormData();
    form.append('file', file);
    form.append('title', `${this.res.label}: ${String(d['title'] ?? d['name'] ?? file.name)}`.slice(0, 200));
    this.uploading = true;
    this.api.post<{ url: string }>('/admin/website/photo', form).subscribe({
      next: (r) => { this.uploading = false; d[f.key] = r.url; },
      error: (err) => { this.uploading = false; this.error = errorText(err, 'Could not upload the photo.'); }
    });
  }

  /** A PDF for the post; also fills in the download button text from the file name. */
  uploadPdf(f: WebsiteField, d: Row, e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 20 * 1024 * 1024) {
      this.error = 'That PDF is larger than 20 MB.';
      return;
    }
    const form = new FormData();
    form.append('file', file);
    this.uploading = true;
    this.api.post<{ url: string; name: string }>('/admin/website/document', form).subscribe({
      next: (r) => {
        this.uploading = false;
        d[f.key] = r.url;
        if (f.nameField && !d[f.nameField]) d[f.nameField] = r.name;
      },
      error: (err) => { this.uploading = false; this.error = errorText(err, 'Could not upload the PDF.'); }
    });
  }

  /** Only the fields of the form; empty text becomes null; datetime-local values are sent as they are. */
  private clean(d: Row): Row {
    const out: Row = {};
    for (const f of this.res.fields) {
      if (f.type === 'info') continue;
      if (f.requires && !this.auth.can(f.requires, 'EDIT')) continue;
      let v = d[f.key];
      if (v === '') v = null;
      if (f.type === 'number' && v !== null && v !== undefined) v = Number(v);
      if (f.type === 'bool') v = !!v;
      out[f.key] = v ?? null;
    }
    return out;
  }
}
