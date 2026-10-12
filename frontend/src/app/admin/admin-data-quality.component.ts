import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { ADMIN_LINKS, NAV_GROUPS } from '../core/models/user.model';
import { StudyStatusOption, StudyStatusService } from '../core/services/study-status.service';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { asDate } from '../shared/format';
import { CardTableDirective } from '../shared/card-table.directive';

interface MergePreview {
  keep: { user_id: string; name: string }; remove: { user_id: string; name: string };
  moves: { applications: number; payments: number; progressReports: number; notifications: number; statusHistory: number };
  notes: string[];
}
interface Check {
  key: string; title: string; description: string; count: number; rows: Record<string, unknown>[];
  /** Menu group (NAV_GROUPS key) and the admin screen where it is fixed. */
  group: string; link: string;
  /** The check's students follow the study status filter. */
  study_filtered?: boolean;
}
interface Group { key: string; label: string; icon: string; checks: Check[]; problems: number }

const STUDY_KEY = 'dq-study-status';

/** Same order as the admin menu. */
const GROUP_ORDER = ['students', 'sponsors', 'community', 'website', 'administration'];

/** Columns that only help the server; not shown. */
const HIDDEN = new Set(['role_id', 'group']);

/**
 * Admin > Data Quality: records that need fixing, grouped like the admin menu (Students, Sponsors & Payments,
 * Community, Website, Administration). Each check shows a count; open it to see the records, and "Open ..." goes to
 * the screen where they are fixed. Groups with nothing to fix are folded.
 */
@Component({
  selector: 'app-admin-data-quality',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, CardTableDirective],
  styles: [`
    .check-head { cursor: pointer; }
    .check-head:hover { background: rgba(0, 0, 0, .02); }
    .check-table td, .check-table th { white-space: nowrap; }
    .group-head { cursor: pointer; user-select: none; }
    .group-chip { cursor: pointer; }
    .study-filter label { cursor: pointer; }
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

    <div class="card mb-3">
      <div class="card-body py-2 d-flex flex-wrap align-items-center study-filter">
        <span class="mr-3 font-weight-semibold">Check students who are:</span>
        <div class="custom-control custom-checkbox custom-control-inline" *ngFor="let s of studyStatuses">
          <input type="checkbox" class="custom-control-input" [id]="'dq-study-' + s.code" [checked]="study.has(s.code)"
                 [disabled]="loading || (study.has(s.code) && study.size === 1)" (change)="toggleStudy(s.code)">
          <label class="custom-control-label" [for]="'dq-study-' + s.code">{{ s.label }}</label>
        </div>
        <small class="text-muted ml-md-auto">Student checks marked <span class="badge badge-light border">{{ studyText }}</span> follow this choice.</small>
      </div>
    </div>

    <div *ngIf="loading" class="text-center my-5"><span class="spinner-border"></span></div>

    <ng-container *ngIf="!loading && checks.length">
      <p class="text-muted mb-2">
        <ng-container *ngIf="problems; else allGood">{{ problems }} of {{ checks.length }} checks found records to look at.</ng-container>
        <ng-template #allGood><i class="mdi mdi-check-circle text-success mr-1"></i>All {{ checks.length }} checks passed.</ng-template>
      </p>
      <div class="mb-3">
        <span *ngFor="let g of groups" class="badge badge-pill mr-2 mb-1 p-2 group-chip" [ngClass]="g.problems ? 'badge-warning' : 'badge-light border'"
              (click)="openGroup(g)" [title]="g.problems ? g.problems + ' check(s) to look at' : 'Nothing to fix'">
          <i class="mdi mr-1" [ngClass]="g.icon"></i>{{ g.label }}: {{ g.problems }}
        </span>
      </div>

      <div *ngFor="let g of groups" class="mb-3" [id]="'dq-' + g.key">
        <h5 class="group-head d-flex align-items-center py-2 mb-2 border-bottom" (click)="toggleGroup(g)" [attr.aria-expanded]="isOpen(g)">
          <i class="mdi mr-2" [ngClass]="g.icon"></i>
          <span class="flex-fill">{{ g.label }}</span>
          <span class="badge badge-pill mr-2" [ngClass]="g.problems ? 'badge-warning' : 'badge-light'">{{ g.problems ? g.problems + ' to look at' : 'all good' }}</span>
          <i class="mdi" [ngClass]="isOpen(g) ? 'mdi-chevron-up' : 'mdi-chevron-down'"></i>
        </h5>
        <ng-container *ngIf="isOpen(g)">
      <div class="card mb-2" *ngFor="let c of g.checks">
        <div class="card-body py-3 check-head d-flex align-items-center" (click)="toggle(c)">
          <i class="mdi mr-3 font-20" [ngClass]="c.count ? 'mdi-alert-circle text-warning' : 'mdi-check-circle text-success'"></i>
          <div class="flex-fill">
            <h5 class="mb-0">{{ c.title }} <span *ngIf="c.study_filtered" class="badge badge-light border font-weight-normal ml-1">{{ studyText }}</span></h5>
            <small class="text-muted">{{ c.description }}</small>
          </div>
          <span class="badge badge-pill mr-3" [ngClass]="c.count ? 'badge-warning' : 'badge-light'">{{ c.count }}</span>
          <i *ngIf="c.count" class="mdi" [ngClass]="open === c.key ? 'mdi-chevron-up' : 'mdi-chevron-down'"></i>
        </div>
        <div class="px-3 pb-2" *ngIf="open === c.key && c.count && screen(c) as s">
          <a class="btn btn-sm btn-outline-primary" [routerLink]="s.path"><i class="mdi mdi-arrow-right mr-1"></i>Open {{ s.label }}</a>
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
  groups: Group[] = [];
  open = '';
  /** Groups opened or closed by hand (otherwise: open when something needs fixing). */
  private groupState: Record<string, boolean> = {};
  loading = false;
  error = '';
  message = '';
  /** Study statuses of the students the checks look at; studying only unless chosen otherwise (remembered). */
  study = new Set<string>(this.savedStudy());

  merge: {
    remove: Record<string, unknown>; candidates: Record<string, unknown>[]; keepId: number | null;
    preview: MergePreview | null; busy: boolean; error: string;
  } | null = null;

  constructor(private api: ApiService, private auth: AuthService, private statuses: StudyStatusService) {}

  /** The study statuses (from the server), in the order of the Student Directory's Study tab. */
  get studyStatuses(): StudyStatusOption[] { return this.statuses.list; }

  /** Possible duplicates can be merged by the Super Admin. */
  mergeable(c: Check): boolean { return c.key === 'possible_duplicate_students' && this.auth.isSuperAdmin(); }

  /** The other students of the same duplicate group are the candidates to keep. */
  startMerge(c: Check, row: Record<string, unknown>): void {
    const candidates = c.rows.filter((r) => r['id'] !== row['id'] && r['group'] === row['group']);
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

  /** "Studying", "Studying + On hold", or "All study statuses". */
  get studyText(): string {
    if (this.studyStatuses.length && this.study.size === this.studyStatuses.length) return 'All study statuses';
    return [...this.study].map((c) => this.statuses.label(c)).join(' + ');
  }

  toggleStudy(code: string): void {
    const next = new Set(this.study);
    if (next.has(code)) next.delete(code); else next.add(code);
    if (!next.size) return; // at least one status
    this.study = next;
    try { localStorage.setItem(STUDY_KEY, [...next].join(',')); } catch { /* private window: just not remembered */ }
    this.load();
  }

  private savedStudy(): string[] {
    let saved: string[] = [];
    // Unknown codes are ignored by the server.
    try { saved = (localStorage.getItem(STUDY_KEY) ?? '').split(',').filter((c) => /^[A-Z_]+$/.test(c)); } catch { /* none */ }
    return saved.length ? saved : ['STUDYING'];
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const studyStatus = [...this.study].join(',');
    this.api.get<Check[]>('/admin/data-quality', { studyStatus }).subscribe({
      next: (c) => { this.loading = false; this.checks = c; this.groups = this.group(c); },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not run the data checks.'); }
    });
  }

  get problems(): number { return this.checks.filter((c) => c.count).length; }

  /** Checks by menu group, in menu order; within a group, those with records to look at come first. */
  private group(checks: Check[]): Group[] {
    const keys = [...GROUP_ORDER, ...checks.map((c) => c.group).filter((g) => !GROUP_ORDER.includes(g))];
    return [...new Set(keys)].map((key) => {
      const list = checks.filter((c) => (c.group || 'administration') === key);
      const def = NAV_GROUPS[key] ?? { label: key, icon: 'mdi-folder-outline' };
      return { key, label: def.label, icon: def.icon, checks: [...list].sort((a, b) => Number(!!b.count) - Number(!!a.count)),
        problems: list.filter((c) => c.count).length };
    }).filter((g) => g.checks.length);
  }

  isOpen(g: Group): boolean { return this.groupState[g.key] ?? g.problems > 0; }

  toggleGroup(g: Group): void { this.groupState[g.key] = !this.isOpen(g); }

  openGroup(g: Group): void {
    this.groupState[g.key] = true;
    setTimeout(() => document.getElementById('dq-' + g.key)?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  /** The admin screen where a check's records are fixed, if this user may open it. */
  screen(c: Check): { path: string; label: string } | null {
    const link = ADMIN_LINKS.find((l) => l.path === c.link);
    return link && (!link.section || this.auth.can(link.section)) ? { path: link.path, label: link.label } : null;
  }

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
