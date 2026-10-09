import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface Criterion { criterion_id: number; name: string; description: string | null; sort_order: number; active: boolean; used?: number }
interface RankRow {
  rank: number; application_id: number; name: string; course_applied: string | null; rcc_name: string | null; status: string | null;
  interviewers: number; overall: number | null; averages: Record<string, number | null>;
  recommendations: { APPROVE: number; WAITLIST: number; REJECT: number };
}

/**
 * Applications > Interview ranking: interviewed applications ordered by their average score (each interviewer's
 * average, averaged), with the per-criterion averages and recommendations. Admins also manage the criteria here.
 */
@Component({
  selector: 'app-admin-interview-ranking',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, CardTableDirective],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box">
          <a routerLink="/admin/applications" class="small font-weight-bold"><i class="mdi mdi-arrow-left"></i> Applications</a>
          <h4 class="page-title mt-1">Interview ranking</h4>
        </div>
      </div>
    </div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card">
      <div class="card-body">
        <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
          <p class="text-muted mb-2 mb-md-0 mr-md-3">
            Interviewed applications, best average first. Each interviewer scores every criterion 1–10 on the application page;
            the score is the average of the interviewers' averages.
          </p>
          <select class="form-control" style="max-width: 220px" [(ngModel)]="statusFilter">
            <option value="">All statuses</option>
            <option *ngFor="let s of statuses" [value]="s">{{ s }}</option>
          </select>
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-0">
            <thead>
              <tr>
                <th>#</th><th>Applicant</th><th>Status</th><th class="text-center">Score</th>
                <th *ngFor="let c of criteria" class="text-center">{{ c.name }}</th>
                <th>Recommendations</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngIf="loading"><td [attr.colspan]="6 + criteria.length" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !visible.length"><td [attr.colspan]="6 + criteria.length" class="text-center text-muted">No interviews have been scored yet.</td></tr>
              <tr *ngFor="let r of visible">
                <td><strong>{{ r.rank }}</strong></td>
                <td><strong>{{ r.name }}</strong><div class="small text-muted">{{ r.course_applied }}{{ r.rcc_name ? ' · ' + r.rcc_name : '' }}</div></td>
                <td><span class="badge badge-light text-capitalize">{{ r.status || 'no status' }}</span></td>
                <td class="text-center"><strong class="font-16">{{ r.overall ?? '--' }}</strong><div class="small text-muted">{{ r.interviewers }} interviewer(s)</div></td>
                <td *ngFor="let c of criteria" class="text-center">{{ r.averages[c.criterion_id] ?? '--' }}</td>
                <td class="text-nowrap">
                  <span *ngIf="r.recommendations.APPROVE" class="badge badge-success mr-1">{{ r.recommendations.APPROVE }} approve</span>
                  <span *ngIf="r.recommendations.WAITLIST" class="badge badge-warning mr-1">{{ r.recommendations.WAITLIST }} waitlist</span>
                  <span *ngIf="r.recommendations.REJECT" class="badge badge-danger">{{ r.recommendations.REJECT }} reject</span>
                </td>
                <td><a [routerLink]="['/admin/applications', r.application_id]" class="btn btn-xs btn-primary">Open</a></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <h4 class="header-title">Interview criteria</h4>
        <p class="text-muted">What interviewers score. A criterion that has been used can't be deleted; make it inactive instead (old scores are kept).</p>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-3">
            <thead><tr><th>Order</th><th>Criterion</th><th>Description</th><th>Status</th><th>Used</th><th *ngIf="canEdit"></th></tr></thead>
            <tbody>
              <tr *ngFor="let c of allCriteria">
                <td>{{ c.sort_order }}</td>
                <td><strong>{{ c.name }}</strong></td>
                <td class="small">{{ c.description }}</td>
                <td><span class="badge" [ngClass]="c.active ? 'badge-success' : 'badge-light'">{{ c.active ? 'Active' : 'Inactive' }}</span></td>
                <td>{{ c.used || 0 }}</td>
                <td *ngIf="canEdit" class="text-nowrap">
                  <button type="button" class="btn btn-xs btn-primary" (click)="edit(c)">Edit</button>
                  <button *ngIf="!c.used" type="button" class="btn btn-xs btn-danger ml-1" (click)="remove(c)">Delete</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <form *ngIf="canEdit && form" (ngSubmit)="save()" class="border rounded p-3">
          <h5 class="mt-0">{{ form.criterionId ? 'Edit criterion' : 'New criterion' }}</h5>
          <div class="row">
            <div class="col-md-4 form-group"><label>Name</label><input class="form-control" name="name" maxlength="100" [(ngModel)]="form.name" required></div>
            <div class="col-md-5 form-group"><label>Description <span class="text-muted">(optional)</span></label><input class="form-control" name="desc" maxlength="500" [(ngModel)]="form.description"></div>
            <div class="col-md-1 form-group"><label>Order</label><input type="number" class="form-control" name="order" [(ngModel)]="form.sortOrder"></div>
            <div class="col-md-2 form-group d-flex align-items-end">
              <div class="custom-control custom-checkbox mb-2">
                <input type="checkbox" class="custom-control-input" id="active" name="active" [(ngModel)]="form.active">
                <label class="custom-control-label" for="active">Active</label>
              </div>
            </div>
          </div>
          <button type="submit" class="btn btn-primary mr-2" [disabled]="saving || !form.name.trim()">{{ saving ? 'Saving…' : 'Save' }}</button>
          <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
        </form>
        <button *ngIf="canEdit && !form" type="button" class="btn btn-outline-primary" (click)="edit()"><i class="mdi mdi-plus mr-1"></i>Add criterion</button>
      </div>
    </div>
  `
})
export class AdminInterviewRankingComponent implements OnInit {
  rows: RankRow[] = [];
  criteria: Criterion[] = [];
  allCriteria: Criterion[] = [];
  statusFilter = '';
  form: { criterionId: number | null; name: string; description: string; sortOrder: number; active: boolean } | null = null;
  loading = false;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('APPLICATIONS', 'EDIT'); }
  get statuses(): string[] { return [...new Set(this.rows.map((r) => r.status ?? 'no status'))]; }
  get visible(): RankRow[] { return this.rows.filter((r) => !this.statusFilter || (r.status ?? 'no status') === this.statusFilter); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<{ criteria: Criterion[]; rows: RankRow[] }>('/admin/interviews/ranking').subscribe({
      next: (r) => {
        this.loading = false;
        this.rows = r.rows;
        // Columns: the criteria anybody was scored on, in order.
        const used = new Set(r.rows.flatMap((x) => Object.keys(x.averages)));
        this.criteria = r.criteria.filter((c) => used.has(String(c.criterion_id)));
      },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the ranking.'); }
    });
    this.api.get<Criterion[]>('/admin/interviews/criteria').subscribe({ next: (c) => (this.allCriteria = c) });
  }

  edit(c?: Criterion): void {
    this.form = c
      ? { criterionId: c.criterion_id, name: c.name, description: c.description ?? '', sortOrder: c.sort_order, active: c.active }
      : { criterionId: null, name: '', description: '', sortOrder: (this.allCriteria.length + 1), active: true };
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    this.api.post('/admin/interviews/criteria', this.form).subscribe({
      next: () => { this.saving = false; this.form = null; this.message = 'Criterion saved.'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the criterion.'); }
    });
  }

  remove(c: Criterion): void {
    if (!confirm(`Delete the criterion "${c.name}"?`)) return;
    this.api.delete(`/admin/interviews/criteria/${c.criterion_id}`).subscribe({
      next: () => { this.message = 'Criterion deleted.'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the criterion.'))
    });
  }
}
