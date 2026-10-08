import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { ROLE_LABELS } from '../core/models/user.model';
import { PagerComponent, PageState } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface LogRow {
  log_id: number; user_id: number | null; user_code: string | null; user_name: string | null; role_id: number | null; action: string;
  method: string | null; path: string | null; status_code: number | null; ip_address: string | null; created_at: string;
}

/** Admin > Activity Log: who changed what (and every sign-in), newest first. */
@Component({
  selector: 'app-admin-activity-log',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Activity Log</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="card">
      <div class="card-body">
        <form class="row align-items-end" (ngSubmit)="search()">
          <div class="col-12 col-md-4 form-group">
            <label for="q">Search</label>
            <input id="q" class="form-control" name="q" [(ngModel)]="q" placeholder="Action, user name, user ID or path">
          </div>
          <div class="col-6 col-md-2 form-group">
            <label for="userId">User ID</label>
            <input id="userId" class="form-control" name="userId" [(ngModel)]="userId" placeholder="Any">
          </div>
          <div class="col-6 col-md-2 form-group">
            <label for="from">From</label>
            <input id="from" type="date" class="form-control" name="from" [(ngModel)]="from">
          </div>
          <div class="col-6 col-md-2 form-group">
            <label for="to">To</label>
            <input id="to" type="date" class="form-control" name="to" [(ngModel)]="to">
          </div>
          <div class="col-6 col-md-2 form-group d-flex">
            <button class="btn btn-primary flex-fill" type="submit">Filter</button>
            <button class="btn btn-light ml-2" type="button" (click)="reset()" title="Clear filters"><i class="mdi mdi-close"></i></button>
          </div>
        </form>

        <div class="table-responsive">
          <table class="table mb-0">
            <thead><tr><th>When</th><th>User</th><th>Action</th><th>Result</th><th>IP address</th></tr></thead>
            <tbody>
              <tr *ngIf="loading"><td colspan="5" class="text-center text-muted"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !rows.length"><td colspan="5" class="text-center text-muted">No activity found.</td></tr>
              <tr *ngFor="let r of rows">
                <td class="text-nowrap">{{ r.created_at | date: 'd MMM yyyy, HH:mm' }}</td>
                <td>
                  <ng-container *ngIf="r.user_id; else anon">
                    <strong>{{ r.user_name || r.user_code }}</strong>
                    <div class="small text-muted">{{ r.user_code }} · {{ role(r.role_id) }}</div>
                  </ng-container>
                  <ng-template #anon><span class="text-muted">Public / not signed in</span></ng-template>
                </td>
                <td>
                  {{ r.action }}
                  <div class="small text-muted" *ngIf="r.path">{{ r.method }} {{ r.path }}</div>
                </td>
                <td><span class="badge" [ngClass]="ok(r) ? 'badge-success' : 'badge-danger'">{{ ok(r) ? 'OK' : (r.status_code || 'Error') }}</span></td>
                <td class="text-muted">{{ r.ip_address || '--' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <app-pager [state]="pg" [total]="total" (pageChange)="load()"></app-pager>
      </div>
    </div>
  `
})
export class AdminActivityLogComponent implements OnInit {
  rows: LogRow[] = [];
  total = 0;
  readonly pg = new PageState(25);
  q = '';
  userId = '';
  from = '';
  to = '';
  loading = false;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  search(): void { this.pg.reset(); this.load(); }

  load(): void {
    this.loading = true;
    // The activity endpoint counts pages from 0.
    this.api.get<{ total: number; data: LogRow[] }>('/admin/activity', {
      search: this.q, userId: this.userId, from: this.from, to: this.to, page: this.pg.page - 1, size: this.pg.size
    }).subscribe({
      next: (r) => { this.loading = false; this.rows = r.data; this.total = r.total; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the activity log.'); }
    });
  }

  reset(): void { this.q = this.userId = this.from = this.to = ''; this.search(); }

  ok(r: LogRow): boolean { return !r.status_code || r.status_code < 400; }

  role(id: number | null): string { return id == null ? '' : ROLE_LABELS[id] ?? 'Role ' + id; }
}
