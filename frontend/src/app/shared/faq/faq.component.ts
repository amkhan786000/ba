import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ROLE } from '../../core/models/user.model';
import { AlertsComponent, errorText } from '../alerts/alerts.component';

interface Faq {
  id: number; category: string; question: string; answer: string;
  /** Editors only. */
  audience?: string; on_website?: boolean; active?: boolean; display_order?: number;
}
interface FaqBody { faqs: Faq[]; editor: boolean; audiences: Record<string, string> }
type Draft = { id: number | null; category: string; audience: string; question: string; answer: string; onWebsite: boolean; active: boolean; displayOrder: number | null };

/** Portal URL prefix -> the role whose questions it shows. */
const PORTAL_AREAS: Record<string, number> = { coordinator: ROLE.COORDINATOR, convenor: ROLE.CONVENOR, sponsor: ROLE.SPONSOR, student: ROLE.STUDENT };

/**
 * Help & FAQ, in the admin area and every portal. Everyone reads the questions meant for them; roles with FAQ edit
 * access (Roles & Responsibilities) add, change and delete them and choose which ones biharanjuman.com shows.
 */
@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  styles: [`
    .faq-q { cursor: pointer; padding: 12px 0; border-bottom: 1px solid var(--line); }
    .faq-q:last-child { border-bottom: 0; }
    .faq-a { white-space: pre-line; color: var(--ink-2); margin-top: 6px; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex flex-wrap justify-content-between align-items-center">
      <h4 class="page-title mb-2">Help &amp; FAQ</h4>
      <button *ngIf="editor" class="btn btn-primary mb-2" (click)="openNew()"><i class="mdi mdi-plus mr-1"></i>Add a question</button>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <input class="form-control mb-3" placeholder="Search the questions and answers" [(ngModel)]="q" aria-label="Search">
      <p *ngIf="loaded && !groups.length" class="text-muted mb-0">{{ q ? 'Nothing matches your search.' : 'No questions yet.' }}</p>
      <div *ngFor="let g of groups" class="mb-3">
        <h5 class="mb-1">{{ g.category }}</h5>
        <div *ngFor="let f of g.items" class="faq-q" (click)="toggle(f.id)">
          <div class="d-flex justify-content-between align-items-start">
            <strong>{{ f.question }}</strong>
            <span class="text-nowrap ml-2">
              <span *ngIf="editor && f.on_website" class="badge badge-success mr-1" title="Shown on biharanjuman.com">Website</span>
              <span *ngIf="editor && f.active === false" class="badge badge-secondary mr-1">Hidden</span>
              <span *ngIf="editor" class="badge badge-light border mr-1">{{ audiences[f.audience!] || f.audience }}</span>
              <i class="mdi" [ngClass]="open.has(f.id) ? 'mdi-chevron-up' : 'mdi-chevron-down'"></i>
            </span>
          </div>
          <div *ngIf="open.has(f.id)" class="faq-a">{{ f.answer }}
            <div *ngIf="editor" class="mt-2" (click)="$event.stopPropagation()">
              <button class="btn btn-xs btn-outline-primary mr-1" (click)="openEdit(f)">Edit</button>
              <button class="btn btn-xs btn-outline-danger" (click)="remove(f)">Delete</button>
            </div>
          </div>
        </div>
      </div>
    </div></div>

    <div *ngIf="draft" class="modal fade show d-block" tabindex="-1" (click)="draft = null">
      <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ draft.id ? 'Edit question' : 'Add a question' }}</h5>
            <button type="button" class="close" (click)="draft = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-row">
              <div class="form-group col-md-6"><label for="f-cat">Category *</label>
                <input id="f-cat" class="form-control" maxlength="80" [(ngModel)]="draft.category" list="faq-cats">
                <datalist id="faq-cats"><option *ngFor="let c of categories" [value]="c"></option></datalist></div>
              <div class="form-group col-md-6"><label for="f-aud">Who sees it in Rahbar</label>
                <select id="f-aud" class="form-control" [(ngModel)]="draft.audience">
                  <option *ngFor="let k of audienceKeys" [value]="k">{{ audiences[k] }}</option></select></div>
            </div>
            <div class="form-group"><label for="f-q">Question *</label><input id="f-q" class="form-control" maxlength="300" [(ngModel)]="draft.question"></div>
            <div class="form-group"><label for="f-a">Answer *</label><textarea id="f-a" class="form-control" rows="6" [(ngModel)]="draft.answer"></textarea></div>
            <div class="form-row align-items-center">
              <div class="form-group col-md-4"><label for="f-order">Order</label>
                <input id="f-order" type="number" class="form-control" [(ngModel)]="draft.displayOrder" placeholder="last"></div>
              <div class="form-group col-md-8">
                <div class="custom-control custom-switch"><input type="checkbox" class="custom-control-input" id="f-web" [(ngModel)]="draft.onWebsite">
                  <label class="custom-control-label" for="f-web">Show on biharanjuman.com (FAQ page)</label></div>
                <div class="custom-control custom-switch mt-1"><input type="checkbox" class="custom-control-input" id="f-act" [(ngModel)]="draft.active">
                  <label class="custom-control-label" for="f-act">Shown in Rahbar (switch off to hide it without deleting)</label></div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="draft = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="busy || !draft.category.trim() || !draft.question.trim() || !draft.answer.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class FaqComponent implements OnInit {
  readonly asRole: number | null;
  faqs: Faq[] = [];
  editor = false;
  audiences: Record<string, string> = {};
  loaded = false;
  q = '';
  open = new Set<number>();
  draft: Draft | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, router: Router) {
    this.asRole = PORTAL_AREAS[router.url.split('/')[1]] ?? null;
  }

  get audienceKeys(): string[] { return Object.keys(this.audiences); }
  get categories(): string[] { return [...new Set(this.faqs.map((f) => f.category))]; }

  /** Questions by category (in order), filtered by every word of the search. */
  get groups(): { category: string; items: Faq[] }[] {
    const words = this.q.trim().toLowerCase().split(/\s+/).filter(Boolean);
    const out: { category: string; items: Faq[] }[] = [];
    for (const f of this.faqs) {
      const text = (f.category + ' ' + f.question + ' ' + f.answer).toLowerCase();
      if (words.some((w) => !text.includes(w))) continue;
      let g = out.find((x) => x.category === f.category);
      if (!g) out.push((g = { category: f.category, items: [] }));
      g.items.push(f);
    }
    return out;
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<FaqBody>('/faqs', { asRole: this.asRole }).subscribe({
      next: (r) => { this.faqs = r.faqs; this.editor = r.editor; this.audiences = r.audiences; this.loaded = true; },
      error: (e) => (this.error = errorText(e, 'Could not load the FAQ.'))
    });
  }

  toggle(id: number): void { if (this.open.has(id)) this.open.delete(id); else this.open.add(id); }

  openNew(): void {
    this.draft = { id: null, category: '', audience: 'ALL', question: '', answer: '', onWebsite: false, active: true, displayOrder: null };
  }

  openEdit(f: Faq): void {
    this.draft = { id: f.id, category: f.category, audience: f.audience ?? 'ALL', question: f.question, answer: f.answer,
      onWebsite: !!f.on_website, active: f.active !== false, displayOrder: f.display_order ?? null };
  }

  save(): void {
    if (!this.draft) return;
    const d = this.draft;
    this.busy = true;
    const req = d.id ? this.api.put<{ message: string }>(`/admin/faqs/${d.id}`, d) : this.api.post<{ message: string }>('/admin/faqs', d);
    req.subscribe({
      next: (r) => { this.busy = false; this.draft = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the question.'); }
    });
  }

  remove(f: Faq): void {
    if (!confirm(`Delete "${f.question}"?`)) return;
    this.api.delete<{ message: string }>(`/admin/faqs/${f.id}`).subscribe({
      next: (r) => { this.message = r.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete it.'))
    });
  }
}
