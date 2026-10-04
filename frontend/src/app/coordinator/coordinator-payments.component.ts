import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { uploadUrl } from '../shared/format';

interface PaymentRow {
  payment_id: number;
  grantee_id: string; grantee_name: string | null; grantee_phone: string | null;
  grantor_id: string | null; grantor_name: string | null; grantor_phone: string | null; grantor_reference: string | null;
  amount: number | null; status: string | null; receipt_url: string | null;
}

type SortCol = 'grantee_name' | 'grantor_name' | 'amount' | 'status';

/** Port of templates/coordinator/monitor_payments.html (server-side paged table, search, sort, CSV). */
@Component({
  selector: 'app-coordinator-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent],
  styles: [`th.sortable { cursor: pointer; user-select: none; white-space: nowrap; } th.sortable .mdi { color: #adb5bd; }`],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex justify-content-between align-items-center">
          <h4 class="page-title mb-0">Monitor Payments</h4>
          <button class="btn btn-primary btn-sm" (click)="downloadCsv()" [disabled]="exporting"><i class="mdi mdi-download"></i> Download CSV</button>
        </div>
      </div>
    </div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Payment Details</h4>
            <div class="row dt-toolbar mb-2">
              <div class="col-sm-6">
                <label>Show
                  <select class="form-control form-control-sm" [(ngModel)]="pageSize" (ngModelChange)="page = 1; load()">
                    <option [ngValue]="10">10</option><option [ngValue]="25">25</option><option [ngValue]="50">50</option><option [ngValue]="100">100</option>
                  </select> entries</label>
              </div>
              <div class="col-sm-6 text-sm-right">
                <label>Search:<input type="search" class="form-control form-control-sm" [(ngModel)]="search" (ngModelChange)="search$.next($event)"></label>
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-bordered nowrap" style="width: 100%">
                <thead>
                  <tr>
                    <th class="sortable" (click)="sort('grantee_name')">Student Detail <i class="mdi" [ngClass]="icon('grantee_name')"></i></th>
                    <th class="sortable" (click)="sort('grantor_name')">Sponsor Detail <i class="mdi" [ngClass]="icon('grantor_name')"></i></th>
                    <th class="sortable" (click)="sort('amount')">Amount <i class="mdi" [ngClass]="icon('amount')"></i></th>
                    <th class="sortable" (click)="sort('status')">Status <i class="mdi" [ngClass]="icon('status')"></i></th>
                    <th>Receipt</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="5" class="text-center text-muted">Processing...</td></tr>
                  <tr *ngIf="!loading && !rows.length"><td colspan="5" class="text-center text-muted">No matching records found</td></tr>
                  <tr *ngFor="let r of rows">
                    <td><strong>ID:</strong> {{ r.grantee_id || 'N/A' }}<br><strong>Name:</strong> {{ r.grantee_name || 'N/A' }}<br><strong>Phone:</strong> {{ r.grantee_phone || 'N/A' }}</td>
                    <td>
                      <strong>ID:</strong> {{ r.grantor_id || 'N/A' }}<br><strong>Name:</strong> {{ r.grantor_name || 'N/A' }}<br><strong>Phone:</strong> {{ r.grantor_phone || 'N/A' }}
                      <ng-container *ngIf="r.grantor_reference && r.grantor_reference !== r.grantor_id"><br><strong>Ref:</strong> {{ r.grantor_reference }}</ng-container>
                    </td>
                    <td>{{ r.amount ?? 'N/A' }}</td>
                    <td><span class="badge" [ngClass]="'badge-' + badge(r.status)">{{ r.status || 'N/A' }}</span></td>
                    <td>
                      <a *ngIf="r.receipt_url; else noReceipt" [href]="receipt(r.receipt_url)" target="_blank" class="btn btn-sm btn-info">Download Receipt</a>
                      <ng-template #noReceipt>No Receipt</ng-template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [total]="filtered" [page]="page" [pageSize]="pageSize" (pageChange)="page = $event; load()"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorPaymentsComponent implements OnInit, OnDestroy {
  rows: PaymentRow[] = [];
  filtered = 0;
  page = 1;
  pageSize = 10;
  search = '';
  search$ = new Subject<string>();
  orderBy: SortCol = 'grantee_name';
  orderDir: 'asc' | 'desc' = 'asc';
  loading = false;
  exporting = false;
  error = '';
  private sub?: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.sub = this.search$.pipe(debounceTime(300)).subscribe(() => { this.page = 1; this.load(); });
    this.load();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  private query(start: number, length: number) {
    return this.api.get<{ recordsFiltered: number; data: PaymentRow[] }>('/coordinator/monitor-payments', {
      start, length, search: this.search.trim(), orderBy: this.orderBy, orderDir: this.orderDir
    });
  }

  load(): void {
    this.loading = true;
    this.query((this.page - 1) * this.pageSize, this.pageSize).subscribe({
      next: (r) => { this.loading = false; this.rows = r.data; this.filtered = r.recordsFiltered; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load payments.'); }
    });
  }

  sort(col: SortCol): void {
    this.orderDir = this.orderBy === col && this.orderDir === 'asc' ? 'desc' : 'asc';
    this.orderBy = col;
    this.page = 1;
    this.load();
  }

  icon(col: SortCol): string {
    if (this.orderBy !== col) return 'mdi-unfold-more-horizontal';
    return this.orderDir === 'asc' ? 'mdi-chevron-up' : 'mdi-chevron-down';
  }

  badge(status: string | null): string {
    switch (status) {
      case 'Paid': return 'success';
      case 'Pending': return 'warning';
      case 'Approved': return 'primary';
      case 'Rejected': return 'danger';
      default: return 'secondary';
    }
  }

  receipt(path: string): string { return uploadUrl(path) ?? '#'; }

  /** Exports every row matching the current search (Student, Sponsor, Amount, Status), like the page's CSV button. */
  downloadCsv(): void {
    this.exporting = true;
    this.query(0, Math.max(this.filtered, 1)).subscribe({
      next: (r) => {
        this.exporting = false;
        const q = (v: unknown) => '"' + String(v ?? '').replace(/"/g, '""') + '"';
        const lines = ['Student Detail,Sponsor Detail,Amount,Status'];
        for (const p of r.data) {
          lines.push([
            `ID: ${p.grantee_id ?? 'N/A'} Name: ${p.grantee_name ?? 'N/A'} Phone: ${p.grantee_phone ?? 'N/A'}`,
            `ID: ${p.grantor_id ?? 'N/A'} Name: ${p.grantor_name ?? 'N/A'} Phone: ${p.grantor_phone ?? 'N/A'}`,
            p.amount, p.status
          ].map(q).join(','));
        }
        const url = URL.createObjectURL(new Blob([lines.join('\n') + '\n'], { type: 'text/csv;charset=utf-8;' }));
        const a = document.createElement('a');
        a.href = url;
        a.download = 'payments.csv';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => { this.exporting = false; this.error = errorText(e, 'Could not export payments.'); }
    });
  }
}
