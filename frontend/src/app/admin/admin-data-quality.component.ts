import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate } from '../shared/format';
import { CardTableDirective } from '../shared/card-table.directive';

interface Check { key: string; title: string; description: string; count: number; rows: Record<string, unknown>[] }

/** Columns that only help the server; not shown. */
const HIDDEN = new Set(['role_id']);

/**
 * Admin > Data Quality: records that need fixing (students without a sponsor, bank details or course,
 * possible duplicates, sponsors without email, ...). Each check shows a count; open it to see the records.
 */
@Component({
  selector: 'app-admin-data-quality',
  standalone: true,
  imports: [CommonModule, AlertsComponent, CardTableDirective],
  styles: [`
    .check-head { cursor: pointer; }
    .check-head:hover { background: rgba(0, 0, 0, .02); }
    .check-table td, .check-table th { white-space: nowrap; }
  `],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex justify-content-between align-items-center">
          <h4 class="page-title">Data Quality</h4>
          <button class="btn btn-outline-primary" (click)="load()" [disabled]="loading"><i class="mdi mdi-refresh mr-1"></i>Check again</button>
        </div>
      </div>
    </div>
    <app-alerts [(error)]="error"></app-alerts>

    <div *ngIf="loading" class="text-center my-5"><span class="spinner-border"></span></div>

    <ng-container *ngIf="!loading && checks.length">
      <p class="text-muted">
        <ng-container *ngIf="problems; else allGood">{{ problems }} of {{ checks.length }} checks found records to look at.</ng-container>
        <ng-template #allGood><i class="mdi mdi-check-circle text-success mr-1"></i>All {{ checks.length }} checks passed.</ng-template>
      </p>
      <div class="card mb-2" *ngFor="let c of checks">
        <div class="card-body py-3 check-head d-flex align-items-center" (click)="toggle(c)">
          <i class="mdi mr-3 font-20" [ngClass]="c.count ? 'mdi-alert-circle text-warning' : 'mdi-check-circle text-success'"></i>
          <div class="flex-fill">
            <h5 class="mb-0">{{ c.title }}</h5>
            <small class="text-muted">{{ c.description }}</small>
          </div>
          <span class="badge badge-pill mr-3" [ngClass]="c.count ? 'badge-warning' : 'badge-light'">{{ c.count }}</span>
          <i *ngIf="c.count" class="mdi" [ngClass]="open === c.key ? 'mdi-chevron-up' : 'mdi-chevron-down'"></i>
        </div>
        <div class="card-body pt-0" *ngIf="open === c.key && c.count">
          <div class="table-responsive">
            <table class="table table-sm table-striped check-table mb-0">
              <thead class="thead-light"><tr><th *ngFor="let col of columns(c)">{{ header(col) }}</th></tr></thead>
              <tbody>
                <tr *ngFor="let row of c.rows"><td *ngFor="let col of columns(c)">{{ cell(row[col]) }}</td></tr>
              </tbody>
            </table>
          </div>
          <small *ngIf="c.count > c.rows.length" class="text-muted">Showing the first {{ c.rows.length }} of {{ c.count }}.</small>
        </div>
      </div>
    </ng-container>
  `
})
export class AdminDataQualityComponent implements OnInit {
  checks: Check[] = [];
  open = '';
  loading = false;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<Check[]>('/admin/data-quality').subscribe({
      next: (c) => { this.loading = false; this.checks = c; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not run the data checks.'); }
    });
  }

  get problems(): number { return this.checks.filter((c) => c.count).length; }

  toggle(c: Check): void { if (c.count) this.open = this.open === c.key ? '' : c.key; }

  columns(c: Check): string[] { return Object.keys(c.rows[0] ?? {}).filter((k) => !HIDDEN.has(k)); }

  /** "student_mobile" -> "Student mobile"; "id" -> "#". */
  header(column: string): string {
    if (column === 'id') return '#';
    const text = column.replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  cell(v: unknown): string {
    if (v === null || v === undefined || v === '') return '--';
    if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}(T|$)/.test(v)) return asDate(v)?.toLocaleDateString() ?? v;
    return String(v);
  }
}
