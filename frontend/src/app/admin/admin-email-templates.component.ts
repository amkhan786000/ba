import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

export interface EmailTemplateRow {
  key: string;
  group: string;
  label: string;
  description: string;
  placeholders: string[];
  required: string[];
  /** True when the built-in wording has been replaced. */
  customised: boolean;
  subject: string;
  body: string;
  default_subject: string;
  default_body: string;
  updated_at: string | null;
  updated_by_name: string | null;
}

/**
 * Admin > Email Templates: every kind of email the application sends. Each one has built-in wording; editing it
 * saves a custom template, and deleting that (Super Admin only) goes back to the built-in wording.
 */
@Component({
  selector: 'app-admin-email-templates',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Email Templates</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card">
      <div class="card-body">
        <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
          <p class="text-muted mb-2 mb-md-0 mr-md-3">
            The wording of every email the application sends. Each email starts with built-in wording; edit it to use your own.
            Placeholders such as <code>{{ '{{name}}' }}</code> are filled in when the email is sent.
          </p>
          <input class="form-control" style="max-width: 280px" placeholder="Search emails" [(ngModel)]="q">
        </div>

        <ng-container *ngFor="let g of groups">
          <h5 class="mt-3 mb-2">{{ g.group }}</h5>
          <div class="table-responsive">
            <table class="table table-sm table-striped table-centered mb-0">
              <thead><tr><th style="width: 30%">Email</th><th>Subject</th><th style="width: 130px">Wording</th><th style="width: 170px">Last changed</th><th style="width: 160px">Actions</th></tr></thead>
              <tbody>
                <tr *ngFor="let t of g.items">
                  <td><strong>{{ t.label }}</strong><div class="small text-muted">{{ t.description }}</div></td>
                  <td class="small">{{ t.subject }}</td>
                  <td><span class="badge" [ngClass]="t.customised ? 'badge-primary' : 'badge-light'">{{ t.customised ? 'Custom' : 'Built-in' }}</span></td>
                  <td class="small">
                    <ng-container *ngIf="t.customised; else dash">{{ t.updated_at | date: 'd MMM yyyy' }}<div class="text-muted">{{ t.updated_by_name }}</div></ng-container>
                  </td>
                  <td class="text-nowrap">
                    <a [routerLink]="['/admin/email-templates', t.key]" class="btn btn-sm btn-primary">{{ canEdit ? (t.customised ? 'Edit' : 'Customise') : 'View' }}</a>
                    <button *ngIf="isSuperAdmin && t.customised" type="button" class="btn btn-sm btn-danger ml-1" (click)="remove(t)">Delete</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </ng-container>
        <p *ngIf="!loading && !groups.length" class="text-muted">No email matches "{{ q }}".</p>
        <div *ngIf="loading" class="text-center my-4"><span class="spinner-border"></span></div>
        <ng-template #dash>--</ng-template>
      </div>
    </div>
  `
})
export class AdminEmailTemplatesComponent implements OnInit {
  templates: EmailTemplateRow[] = [];
  q = '';
  loading = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('EMAIL_TEMPLATES', 'EDIT'); }
  /** Only the Super Admin may delete a custom template. */
  get isSuperAdmin(): boolean { return this.auth.currentUser()?.roleId === 1; }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<EmailTemplateRow[]>('/admin/email-templates').subscribe({
      next: (t) => { this.loading = false; this.templates = t; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the email templates.'); }
    });
  }

  get groups(): { group: string; items: EmailTemplateRow[] }[] {
    const q = this.q.trim().toLowerCase();
    const out: { group: string; items: EmailTemplateRow[] }[] = [];
    for (const t of this.templates) {
      if (q && !(t.label + ' ' + t.description + ' ' + t.subject + ' ' + t.group).toLowerCase().includes(q)) continue;
      let g = out.find((x) => x.group === t.group);
      if (!g) out.push((g = { group: t.group, items: [] }));
      g.items.push(t);
    }
    return out;
  }

  remove(t: EmailTemplateRow): void {
    if (!confirm(`Delete the custom wording of "${t.label}"? The email goes back to the built-in wording.`)) return;
    this.api.delete<{ message: string }>(`/admin/email-templates/${t.key}`).subscribe({
      next: (r) => { this.message = r.message; this.error = ''; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the template.'))
    });
  }
}
