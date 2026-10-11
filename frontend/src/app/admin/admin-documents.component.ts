import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { ROLE } from '../core/models/user.model';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';
import { WebsiteMarkdownPipe } from './website/website-markdown.pipe';

interface DocRow {
  id: number; category_id: number; title: string; effective_date: string | null; archived: boolean;
  updated_at: string | null; updated_by_name: string | null; earlier_versions?: number; visibility?: string; readers?: string; snippet?: string;
}
interface Doc {
  id: number; category_id: number; category_name: string | null; title: string; content: string; effective_date: string | null;
  archived: boolean; created_at: string | null; created_by_name: string | null; updated_at: string | null; updated_by_name: string | null;
  visibility?: string; visible_roles?: number[]; readers?: string;
}
interface Category { id: number; name: string; description: string | null; display_order: number; documents: number }
interface Revision { id: number; title: string; content: string; effective_date: string | null; created_at: string; replaced_by_name: string | null }

/**
 * Admin > Documents: the organisation's by-laws and other documents, kept as text in categories. The text is
 * pasted in (not uploaded); line breaks are kept and Markdown is shown formatted. Every change keeps the earlier
 * version, which can be viewed and restored. Documents are archived, not deleted (only the Super Admin deletes).
 * Who may read a document is chosen on it: everyone (every signed-in user, also in the portals) or some roles;
 * people who edit documents see them all. The same page is the read-only Documents page of the portals.
 */
@Component({
  selector: 'app-admin-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective, WebsiteMarkdownPipe],
  styles: [`
    .doc-list { max-height: 70vh; overflow-y: auto; }
    .doc-list .list-group-item { cursor: pointer; }
    .doc-text { font-size: .95rem; line-height: 1.6; overflow-wrap: anywhere; }
    .doc-text :is(h1, h2, h3) { font-size: 1.15rem; margin-top: 1.1rem; }
    textarea.doc-editor { font-size: .9rem; min-height: 50vh; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Documents</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <ul class="nav nav-tabs mb-3" *ngIf="canCategories">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'documents'" (click)="$event.preventDefault(); tab = 'documents'">Documents</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'categories'" (click)="$event.preventDefault(); tab = 'categories'">Categories</a></li>
    </ul>

    <!-- documents -->
    <div class="row" *ngIf="tab === 'documents'">
      <div class="col-lg-4">
        <div class="card"><div class="card-body">
          <button *ngIf="canEdit" class="btn btn-primary btn-block mb-3" (click)="newDoc()" [disabled]="!categories.length">
            <i class="mdi mdi-plus mr-1"></i>Add document</button>
          <p *ngIf="canEdit && !categories.length" class="small text-muted">{{ canCategories ? 'Add a category first (Categories tab).' : 'An admin needs to add a category first.' }}</p>
          <input class="form-control mb-2" placeholder="Search titles and text" [(ngModel)]="q" (ngModelChange)="search()" aria-label="Search documents">
          <p *ngIf="hits" class="small text-muted mb-2">{{ hits.length }} document{{ hits.length === 1 ? '' : 's' }} contain “{{ q.trim() }}”.</p>
          <select class="form-control mb-2" [(ngModel)]="categoryId" aria-label="Category">
            <option [ngValue]="null">All categories</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
          </select>
          <div class="custom-control custom-checkbox mb-2" *ngIf="canEdit">
            <input type="checkbox" class="custom-control-input" id="show-archived" [(ngModel)]="showArchived">
            <label class="custom-control-label" for="show-archived">Show archived</label>
          </div>
          <div class="doc-list">
            <p *ngIf="!groups.length" class="text-muted small mb-0">{{ docs.length ? 'No documents match.' : 'No documents yet.' }}</p>
            <div *ngFor="let g of groups" class="mb-2">
              <div class="small text-uppercase text-muted font-weight-bold mb-1">{{ g.name }}</div>
              <div class="list-group">
                <a *ngFor="let d of g.docs" class="list-group-item list-group-item-action py-2" [class.active]="d.id === current?.id"
                   tabindex="0" (click)="open(d.id)" (keydown.enter)="open(d.id)">
                  <div>{{ d.title }} <span *ngIf="d.archived" class="badge badge-secondary ml-1">Archived</span></div>
                  <small [class.text-muted]="d.id !== current?.id">{{ d.effective_date ? 'Effective ' + (d.effective_date | date: 'd MMM yyyy') : 'Updated ' + (d.updated_at | date: 'd MMM yyyy') }}</small>
                  <div *ngIf="d.snippet" class="small font-italic mt-1" [class.text-muted]="d.id !== current?.id">{{ d.snippet }}</div>
                </a>
              </div>
            </div>
          </div>
        </div></div>
      </div>

      <div class="col-lg-8">
        <!-- editor -->
        <div class="card" *ngIf="edit">
          <div class="card-body">
            <h4 class="header-title">{{ edit.id ? 'Edit document' : 'Add document' }}</h4>
            <div class="row">
              <div class="col-md-8 form-group"><label for="d-title">Title *</label><input id="d-title" class="form-control" [(ngModel)]="edit.title" maxlength="200"></div>
              <div class="col-md-4 form-group"><label for="d-date">Effective date</label><input id="d-date" type="date" class="form-control" [(ngModel)]="edit.effectiveDate"></div>
              <div class="col-md-8 form-group"><label for="d-cat">Category *</label>
                <select id="d-cat" class="form-control" [(ngModel)]="edit.categoryId">
                  <option [ngValue]="null">-- Choose --</option>
                  <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
                </select></div>
            </div>
            <div class="form-group">
              <span class="d-block mb-1">Who can read it *</span>
              <div class="custom-control custom-radio custom-control-inline">
                <input type="radio" id="v-public" name="visibility" class="custom-control-input" value="PUBLIC" [(ngModel)]="edit.visibility">
                <label class="custom-control-label" for="v-public">Everyone (all signed-in users)</label></div>
              <div class="custom-control custom-radio custom-control-inline">
                <input type="radio" id="v-roles" name="visibility" class="custom-control-input" value="ROLES" [(ngModel)]="edit.visibility">
                <label class="custom-control-label" for="v-roles">Only these roles</label></div>
              <div *ngIf="edit.visibility === 'ROLES'" class="border rounded p-2 mt-2">
                <div class="custom-control custom-checkbox" *ngFor="let r of roleOptions">
                  <input type="checkbox" class="custom-control-input" [id]="'v-role-' + r.id" [checked]="edit.roles.includes(r.id)" (change)="toggleRole(r.id)">
                  <label class="custom-control-label" [for]="'v-role-' + r.id">{{ r.name }}</label></div>
                <small class="text-muted">People who can edit documents always see every document.</small>
              </div>
            </div>
            <div class="d-flex justify-content-between align-items-end mb-1">
              <label for="d-content" class="mb-0">Text *</label>
              <div class="btn-group btn-group-sm">
                <button type="button" class="btn" [ngClass]="preview ? 'btn-light' : 'btn-secondary'" (click)="preview = false">Write</button>
                <button type="button" class="btn" [ngClass]="preview ? 'btn-secondary' : 'btn-light'" (click)="preview = true" [disabled]="!edit.content">Preview</button>
              </div>
            </div>
            <textarea *ngIf="!preview" id="d-content" class="form-control doc-editor" [(ngModel)]="edit.content"
                      placeholder="Paste the text of the document here."></textarea>
            <div *ngIf="preview" class="border rounded p-3 doc-text website-prose" [innerHTML]="edit.content | websiteMarkdown"></div>
            <small class="text-muted d-block mt-1">Paste the text as it is: line breaks are kept. Optional formatting: <code>## Heading</code>, <code>**bold**</code>, lines starting with <code>-</code> or <code>1.</code> become lists.</small>
            <p *ngIf="edit.id" class="small text-muted mt-2 mb-0">Saving keeps the current text as an earlier version.</p>
            <div class="d-flex justify-content-end mt-3">
              <button class="btn btn-light mr-2" (click)="cancelEdit()">Cancel</button>
              <button class="btn btn-primary" (click)="save()" [disabled]="busy || !edit.title.trim() || !edit.categoryId || !edit.content.trim() || (edit.visibility === 'ROLES' && !edit.roles.length)">{{ busy ? 'Saving…' : 'Save' }}</button>
            </div>
          </div>
        </div>

        <!-- reader -->
        <div class="card" *ngIf="!edit && current">
          <div class="card-body">
            <div class="d-flex flex-wrap justify-content-between align-items-start">
              <div class="mb-2 mr-2" style="min-width: 0">
                <span class="badge badge-light border mb-1">{{ current.category_name }}</span>
                <span *ngIf="current.archived" class="badge badge-secondary ml-1">Archived</span>
                <h3 class="mt-1 mb-1">{{ current.title }}</h3>
                <div class="small text-muted">
                  <span *ngIf="current.effective_date">Effective {{ current.effective_date | date: 'd MMMM yyyy' }} · </span>
                  Last changed {{ current.updated_at | date: 'd MMM yyyy, h:mm a' }}<span *ngIf="current.updated_by_name"> by {{ current.updated_by_name }}</span>
                </div>
                <div *ngIf="current.readers" class="small mt-1"><i class="mdi mdi-eye-outline mr-1"></i>Can read: {{ current.readers }}</div>
              </div>
              <div class="d-flex flex-wrap">
                <button class="btn btn-sm btn-outline-secondary mr-1 mb-1" (click)="copy()"><i class="mdi mdi-content-copy mr-1"></i>{{ copied ? 'Copied' : 'Copy text' }}</button>
                <button class="btn btn-sm btn-outline-secondary mr-1 mb-1" (click)="openVersions()" *ngIf="currentRow?.earlier_versions">
                  <i class="mdi mdi-history mr-1"></i>Earlier versions ({{ currentRow?.earlier_versions }})</button>
                <button *ngIf="canEdit" class="btn btn-sm btn-primary mr-1 mb-1" (click)="editDoc()">Edit</button>
                <button *ngIf="canEdit" class="btn btn-sm btn-outline-secondary mr-1 mb-1" (click)="archive(!current.archived)"><i class="mdi mdi-archive mr-1"></i>{{ current.archived ? 'Un-archive' : 'Archive' }}</button>
                <button *ngIf="canEdit && superAdmin" class="btn btn-sm btn-outline-danger mb-1" (click)="remove()">Delete</button>
              </div>
            </div>
            <hr>
            <div class="doc-text website-prose" [innerHTML]="current.content | websiteMarkdown"></div>
          </div>
        </div>

        <div class="card" *ngIf="!edit && !current">
          <div class="card-body text-center text-muted py-5">
            <i class="mdi mdi-file-document-box-multiple-outline" style="font-size: 2.5rem"></i>
            <p class="mb-0">Choose a document to read it{{ canEdit ? ', or add one' : '' }}.</p>
          </div>
        </div>
      </div>
    </div>

    <!-- categories -->
    <div class="card" *ngIf="tab === 'categories'"><div class="card-body">
      <div class="d-flex justify-content-between align-items-start flex-wrap mb-2">
        <p class="text-muted mb-2 mr-3">Every document belongs to a category, e.g. By-laws, Policies, Minutes of meetings, Forms.</p>
        <button *ngIf="canEditCategories" class="btn btn-primary btn-sm" (click)="cat = { id: null, name: '', description: '', displayOrder: categories.length + 1 }">
          <i class="mdi mdi-plus mr-1"></i>Add category</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Order</th><th>Category</th><th>Documents</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!categories.length"><td colspan="4" class="text-center text-muted">No categories yet.</td></tr>
          <tr *ngFor="let c of categories">
            <td>{{ c.display_order }}</td>
            <td><strong>{{ c.name }}</strong><div class="small text-muted">{{ c.description }}</div></td>
            <td>{{ c.documents }}</td>
            <td class="text-right text-nowrap">
              <button *ngIf="canEditCategories" class="btn btn-xs btn-outline-primary mr-1" (click)="cat = { id: c.id, name: c.name, description: c.description || '', displayOrder: c.display_order }">Edit</button>
              <button *ngIf="canEditCategories && !c.documents" class="btn btn-xs btn-outline-danger" (click)="deleteCategory(c)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="cat" class="modal fade show d-block" tabindex="-1" (click)="cat = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ cat.id ? 'Edit category' : 'Add category' }}</h5><button type="button" class="close" (click)="cat = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="c-name">Name *</label><input id="c-name" class="form-control" maxlength="100" [(ngModel)]="cat.name" placeholder="e.g. By-laws"></div>
            <div class="form-group"><label for="c-desc">Description</label><input id="c-desc" class="form-control" maxlength="500" [(ngModel)]="cat.description"></div>
            <div class="form-group mb-0"><label for="c-order">Order</label><input id="c-order" type="number" class="form-control" [(ngModel)]="cat.displayOrder"></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="cat = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveCategory()" [disabled]="busy || !cat.name.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="versions" class="modal fade show d-block" tabindex="-1" (click)="versions = null">
      <div class="modal-dialog modal-xl modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Earlier versions of {{ current?.title }}</h5><button type="button" class="close" (click)="versions = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="row">
              <div class="col-md-4">
                <div class="list-group">
                  <a *ngFor="let v of versions" class="list-group-item list-group-item-action py-2" [class.active]="v.id === version?.id" tabindex="0"
                     (click)="version = v" (keydown.enter)="version = v">
                    <div class="small">Replaced {{ v.created_at | date: 'd MMM yyyy, h:mm a' }}</div>
                    <small [class.text-muted]="v.id !== version?.id">{{ v.replaced_by_name ? 'by ' + v.replaced_by_name : '' }}</small>
                  </a>
                </div>
              </div>
              <div class="col-md-8" *ngIf="version">
                <h5 class="mt-3 mt-md-0">{{ version.title }}</h5>
                <p class="small text-muted" *ngIf="version.effective_date">Effective {{ version.effective_date | date: 'd MMMM yyyy' }}</p>
                <div class="border rounded p-3 doc-text website-prose" [innerHTML]="version.content | websiteMarkdown"></div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="versions = null">Close</button>
            <button *ngIf="canEdit && version" type="button" class="btn btn-primary" (click)="restore()" [disabled]="busy">Make this the current version</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminDocumentsComponent implements OnInit {
  tab: 'documents' | 'categories' = 'documents';
  docs: DocRow[] = [];
  categories: Category[] = [];
  current: Doc | null = null;
  q = '';
  /** Server search results (title and text) while a search of 2+ letters is typed; null: the full list. */
  hits: DocRow[] | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  categoryId: number | null = null;
  showArchived = false;
  edit: { id: number | null; title: string; categoryId: number | null; effectiveDate: string; content: string; visibility: string; roles: number[] } | null = null;
  roleOptions: { id: number; name: string }[] = [];
  preview = false;
  cat: { id: number | null; name: string; description: string; displayOrder: number } | null = null;
  versions: Revision[] | null = null;
  version: Revision | null = null;
  copied = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('DOCUMENTS', 'EDIT'); }
  get canCategories(): boolean { return this.auth.can('DOCUMENT_CATEGORIES'); }
  get canEditCategories(): boolean { return this.auth.can('DOCUMENT_CATEGORIES', 'EDIT'); }
  get superAdmin(): boolean { return this.auth.currentUser()?.roleId === ROLE.SUPER_ADMIN; }
  get currentRow(): DocRow | undefined { return this.docs.find((d) => d.id === this.current?.id); }

  /** Documents matching the filters, by category in the categories' order. */
  get groups(): { name: string; docs: DocRow[] }[] {
    return this.categories
      .filter((c) => !this.categoryId || c.id === this.categoryId)
      .map((c) => ({
        name: c.name,
        docs: (this.hits ?? this.docs).filter((d) => d.category_id === c.id && (this.showArchived || !d.archived))
      }))
      .filter((g) => g.docs.length);
  }

  ngOnInit(): void { this.load(); }

  /** Searches titles and text on the server, a moment after typing stops. */
  search(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    const q = this.q.trim();
    if (q.length < 2) { this.hits = null; return; }
    this.searchTimer = setTimeout(() => {
      this.api.get<{ documents: DocRow[] }>('/admin/documents', { q }).subscribe({
        next: (r) => { if (this.q.trim() === q) this.hits = r.documents; },
        error: (e) => (this.error = errorText(e, 'Could not search the documents.'))
      });
    }, 300);
  }

  load(openId?: number): void {
    this.api.get<{ documents: DocRow[]; categories: Category[] }>('/admin/documents').subscribe({
      next: (r) => {
        this.docs = r.documents;
        this.categories = r.categories;
        if (openId) this.open(openId);
        if (this.hits) this.search(); // keep the search results up to date
      },
      error: (e) => (this.error = errorText(e, 'Could not load the documents.'))
    });
  }

  open(id: number): void {
    if (this.edit && !confirm('Leave without saving?')) return;
    this.edit = null;
    this.copied = false;
    this.api.get<Doc>(`/admin/documents/${id}`).subscribe({
      next: (d) => (this.current = d),
      error: (e) => (this.error = errorText(e, 'Could not open the document.'))
    });
  }

  newDoc(): void {
    this.current = null;
    this.preview = false;
    this.loadRoleOptions();
    this.edit = { id: null, title: '', categoryId: this.categoryId, effectiveDate: '', content: '', visibility: 'PUBLIC', roles: [] };
  }

  editDoc(): void {
    const d = this.current!;
    this.preview = false;
    this.loadRoleOptions();
    this.edit = { id: d.id, title: d.title, categoryId: d.category_id, effectiveDate: d.effective_date ?? '', content: d.content,
      visibility: d.visibility ?? 'PUBLIC', roles: [...(d.visible_roles ?? [])] };
  }

  cancelEdit(): void {
    const id = this.edit?.id;
    this.edit = null;
    if (id) this.open(id);
  }

  save(): void {
    const e = this.edit;
    if (!e) return;
    this.busy = true;
    const body = { title: e.title, categoryId: e.categoryId, effectiveDate: e.effectiveDate || null, content: e.content,
      visibility: e.visibility, visibleRoleIds: e.visibility === 'ROLES' ? e.roles : [] };
    const req = e.id ? this.api.put<{ message: string; id: number }>(`/admin/documents/${e.id}`, body)
      : this.api.post<{ message: string; id: number }>('/admin/documents', body);
    req.subscribe({
      next: (r) => { this.busy = false; this.edit = null; this.message = r.message; this.load(r.id); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save the document.'); }
    });
  }

  private loadRoleOptions(): void {
    if (this.roleOptions.length) return;
    this.api.get<{ id: number; name: string }[]>('/admin/documents/role-options').subscribe({ next: (r) => (this.roleOptions = r) });
  }

  toggleRole(id: number): void {
    if (!this.edit) return;
    this.edit.roles = this.edit.roles.includes(id) ? this.edit.roles.filter((x) => x !== id) : [...this.edit.roles, id];
  }

  archive(archived: boolean): void {
    const d = this.current!;
    this.api.post<{ message: string }>(`/admin/documents/${d.id}/archive`, { archived }).subscribe({
      next: (r) => { this.message = r.message; this.load(d.id); },
      error: (e) => (this.error = errorText(e, 'Could not change the document.'))
    });
  }

  remove(): void {
    const d = this.current!;
    if (!confirm(`Delete "${d.title}" and all its earlier versions? This cannot be undone.`)) return;
    this.api.delete<{ message: string }>(`/admin/documents/${d.id}`).subscribe({
      next: (r) => { this.message = r.message; this.current = null; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the document.'))
    });
  }

  copy(): void {
    const text = this.current?.content ?? '';
    navigator.clipboard?.writeText(text).then(() => (this.copied = true), () => (this.error = 'Could not copy; select the text and copy it instead.'));
  }

  openVersions(): void {
    const d = this.current!;
    this.api.get<Revision[]>(`/admin/documents/${d.id}/revisions`).subscribe({
      next: (r) => { this.versions = r; this.version = r[0] ?? null; },
      error: (e) => (this.error = errorText(e, 'Could not load the earlier versions.'))
    });
  }

  restore(): void {
    const d = this.current!, v = this.version!;
    if (!confirm('Make this version the current one? The current text is kept as an earlier version.')) return;
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/documents/${d.id}/revisions/${v.id}/restore`, {}).subscribe({
      next: (r) => { this.busy = false; this.versions = null; this.message = r.message; this.load(d.id); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not restore the version.'); }
    });
  }

  saveCategory(): void {
    const c = this.cat;
    if (!c) return;
    this.busy = true;
    const body = { name: c.name, description: c.description, displayOrder: c.displayOrder };
    const req = c.id ? this.api.put<{ message: string }>(`/admin/documents/categories/${c.id}`, body)
      : this.api.post<{ message: string }>('/admin/documents/categories', body);
    req.subscribe({
      next: (r) => { this.busy = false; this.cat = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the category.'); }
    });
  }

  deleteCategory(c: Category): void {
    if (!confirm(`Delete the category ${c.name}?`)) return;
    this.api.delete<{ message: string }>(`/admin/documents/categories/${c.id}`).subscribe({
      next: (r) => { this.message = r.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the category.'))
    });
  }
}
