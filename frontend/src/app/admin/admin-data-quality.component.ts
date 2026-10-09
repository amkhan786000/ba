import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate } from '../shared/format';
import { CardTableDirective } from '../shared/card-table.directive';

interface MergePreview {
  keep: { user_id: string; name: string }; remove: { user_id: string; name: string };
  moves: { applications: number; payments: number; progressReports: number; notifications: number; statusHistory: number };
  notes: string[];
}
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
  imports: [CommonModule, FormsModule, AlertsComponent, CardTableDirective],
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
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

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
              <thead class="thead-light"><tr><th *ngFor="let col of columns(c)">{{ header(col) }}</th><th *ngIf="mergeable(c)"></th></tr></thead>
              <tbody>
                <tr *ngFor="let row of c.rows">
                  <td *ngFor="let col of columns(c)">{{ cell(row[col]) }}</td>
                  <td *ngIf="mergeable(c)"><button type="button" class="btn btn-xs btn-outline-primary" (click)="startMerge(c, row)">Merge…</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <small *ngIf="c.count > c.rows.length" class="text-muted">Showing the first {{ c.rows.length }} of {{ c.count }}.</small>
        </div>
      </div>
    </ng-container>

    <!-- Merge a duplicate student into the one to keep (Super Admin only) -->
    <div *ngIf="merge" class="modal fade show d-block" tabindex="-1" (click)="merge = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Merge duplicate student</h5><button type="button" class="close" (click)="merge = null">&times;</button></div>
          <div class="modal-body">
            <p>
              Merge <strong>{{ merge.remove['name'] }} ({{ merge.remove['user_id'] }})</strong> into:
            </p>
            <select class="form-control mb-3" [(ngModel)]="merge.keepId" (ngModelChange)="previewMerge()">
              <option [ngValue]="null">Choose the record to keep…</option>
              <option *ngFor="let k of merge.candidates" [ngValue]="k['id']">{{ k['name'] }} ({{ k['user_id'] }}){{ k['study_status'] ? ' · ' + k['study_status'] : '' }}</option>
            </select>
            <div *ngIf="merge.preview as p">
              <p class="mb-1">These move from {{ p.remove.user_id }} to {{ p.keep.user_id }}:</p>
              <ul class="mb-2">
                <li>{{ p.moves.applications }} application(s), {{ p.moves.payments }} payment(s), {{ p.moves.progressReports }} progress report(s)</li>
                <li>{{ p.moves.notifications }} notification(s), {{ p.moves.statusHistory }} study-status change(s)</li>
                <li *ngFor="let n of p.notes">{{ n }}</li>
              </ul>
              <div class="alert alert-warning small mb-0">
                {{ p.remove.user_id }} will be deactivated and marked "merged into {{ p.keep.user_id }}". It is never deleted, but this can't be undone from the app.
              </div>
            </div>
            <p *ngIf="merge.error" class="text-danger mt-2 mb-0">{{ merge.error }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="merge = null">Cancel</button>
            <button type="button" class="btn btn-danger" (click)="confirmMerge()" [disabled]="!merge.preview || merge.busy">{{ merge.busy ? 'Merging…' : 'Merge' }}</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminDataQualityComponent implements OnInit {
  checks: Check[] = [];
  open = '';
  loading = false;
  error = '';
  message = '';

  merge: {
    remove: Record<string, unknown>; candidates: Record<string, unknown>[]; keepId: number | null;
    preview: MergePreview | null; busy: boolean; error: string;
  } | null = null;

  constructor(private api: ApiService, private auth: AuthService) {}

  /** Possible duplicates can be merged by the Super Admin. */
  mergeable(c: Check): boolean { return c.key === 'possible_duplicate_students' && this.auth.currentUser()?.roleId === 1; }

  /** The other students with the same name are the candidates to keep. */
  startMerge(c: Check, row: Record<string, unknown>): void {
    const key = (v: unknown) => String(v ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
    const candidates = c.rows.filter((r) => r['id'] !== row['id'] && key(r['name']) === key(row['name']));
    this.merge = { remove: row, candidates, keepId: candidates.length === 1 ? (candidates[0]['id'] as number) : null, preview: null, busy: false, error: '' };
    if (this.merge.keepId) this.previewMerge();
  }

  previewMerge(): void {
    const m = this.merge;
    if (!m) return;
    m.preview = null;
    m.error = '';
    if (!m.keepId) return;
    this.api.post<MergePreview>('/admin/students/merge/preview', { keepId: m.keepId, removeId: m.remove['id'] }).subscribe({
      next: (p) => (m.preview = p),
      error: (e) => (m.error = errorText(e, 'Could not prepare the merge.'))
    });
  }

  confirmMerge(): void {
    const m = this.merge;
    if (!m?.preview || !confirm(`Merge ${m.preview.remove.user_id} into ${m.preview.keep.user_id}?`)) return;
    m.busy = true;
    this.api.post<{ message: string }>('/admin/students/merge', { keepId: m.keepId, removeId: m.remove['id'] }).subscribe({
      next: (r) => { this.merge = null; this.message = r.message; this.load(); },
      error: (e) => { m.busy = false; m.error = errorText(e, 'Could not merge the students.'); }
    });
  }

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
