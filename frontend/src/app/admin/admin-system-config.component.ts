import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

export interface Schedule {
  schedule_id: number;
  year: number;
  amount: number | null;
  frequency_months: number | null;
  due_notice_days: number | null;
  updated_at: string | null;
  updated_by_name: string | null;
}

/**
 * Payment Config list: for each session year, the amount of each installment and how often one is due
 * (every 3 or 4 months). Students of that year get their installments from it.
 */
@Component({
  selector: 'app-admin-system-config',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Payment Config</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a *ngIf="canEdit" routerLink="/admin/system-configuration/new" class="btn btn-primary btn-responsive"><i class="mdi mdi-plus mr-1"></i>Add Payment Config</a>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-1">Payment Config List</h4>
            <p class="text-muted small mb-3">
              Students get their installments from the config of their session year: the amount of each installment and how often
              one is due. E.g. an 8-semester course paid every 4 months has 4 × 3 = 12 installments.
            </p>
            <div class="row mb-3">
              <div class="col-12 col-md-4">
                <input type="text" class="form-control" placeholder="Search by year..." [(ngModel)]="search" (ngModelChange)="pg.reset()" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>Session Year</th><th>Amount per Installment</th><th>Frequency</th><th>Installments a Year</th><th>Shows as Due</th><th>Last Updated</th><th>Updated By</th><th *ngIf="canEdit">Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="8" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
                  <tr *ngIf="!loading && !visible.length"><td colspan="8" class="text-center text-muted">No payment config found.</td></tr>
                  <tr *ngFor="let s of visible | paginate: pg.page : pg.size">
                    <td><strong>{{ s.year }}</strong></td>
                    <td>₹{{ s.amount !== null ? (s.amount | number: '1.2-2') : '--' }}</td>
                    <td>Every {{ s.frequency_months || 3 }} months</td>
                    <td>{{ 12 / (s.frequency_months || 3) }}</td>
                    <td>{{ s.due_notice_days ?? 30 }} days before</td>
                    <td>{{ s.updated_at ? (s.updated_at | date: 'd MMM yyyy, h:mm a') : '--' }}</td>
                    <td>{{ s.updated_by_name || '--' }}</td>
                    <td *ngIf="canEdit"><a [routerLink]="['/admin/system-configuration', s.year, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="visible.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminSystemConfigComponent implements OnInit {
  readonly pg = new PageState();
  schedules: Schedule[] = [];
  search = '';
  loading = false;
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('PAYMENT_CONFIG', 'EDIT'); }

  ngOnInit(): void {
    this.loading = true;
    this.api.get<{ schedules: Schedule[] }>('/admin/system-configuration').subscribe({
      next: (r) => { this.loading = false; this.schedules = r.schedules; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the payment config.'); }
    });
  }

  get visible(): Schedule[] {
    const f = this.search.trim();
    return this.schedules.filter((s) => !f || String(s.year).includes(f));
  }
}
