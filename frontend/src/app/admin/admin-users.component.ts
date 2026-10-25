import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AppDatePipe } from '../shared/app-date';
import { errorText } from '../shared/alerts/alerts.component';
import { ApiService } from '../core/services/api.service';
import { ChapterService } from '../core/services/chapter.service';
import { PagerComponent, PageState } from '../shared/pager/pager.component';
import { Chapter } from './admin-chapters.component';
import { RccCenter } from './admin-rcc-centers.component';
import { AuthService } from '../core/services/auth.service';
import { ROLE } from '../core/models/user.model';
import { EMPTY, Subject, Subscription, catchError, debounceTime, merge, switchMap } from 'rxjs';
import { CardTableDirective } from '../shared/card-table.directive';
import { DateInputComponent } from '../shared/date-input.component';

export interface UserRow {
  /** users.id: used in links and API calls. */
  id: number;
  /** The user's code (shown as "User Id"). */
  user_id: string;
  name: string;
  email: string | null;
  phone?: string | null;
  role_id: number;
  role_name: string;
  /** Roles besides the main one. */
  additional_role_ids?: number[];
  additional_role_names?: string[];
  status: string;
  chapter_id?: number | null;
  rcc_center_id?: number | null;
  /** A sponsor whose contact / personal details the viewer may not see (they come back blank). */
  masked?: boolean;
  chapter_name?: string | null;
  rcc_center_name?: string | null;
  /** Students: session year and study status. */
  year?: number | null;
  study_status?: string | null;
  /** Still on a temporary password (must choose their own at the next sign-in). */
  must_change_password?: boolean | null;
  last_sign_in?: string | null;
  created_at?: string | null;
}

interface BulkUsersResult {
  message: string; added: Record<string, unknown>[]; duplicates: Record<string, unknown>[];
  report: { problems?: Record<string, unknown>[] };
}

export interface RoleOption {
  roleId: number;
  roleName: string;
}

/** Port of templates/admin/manage_users.html (filters, server-side pagination, Add User modal). */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [DateInputComponent, CommonModule, FormsModule, RouterLink, PagerComponent, CardTableDirective, AppDatePipe],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box"><h4 class="page-title">Manage Users</h4></div>
      </div>
    </div>

    <div *ngIf="message" class="alert alert-success alert-dismissible">
      {{ message }} <button type="button" class="close" (click)="message = ''"><span>&times;</span></button>
    </div>
    <div *ngIf="error" class="alert alert-danger alert-dismissible">
      {{ error }} <button type="button" class="close" (click)="error = ''"><span>&times;</span></button>
    </div>

    <div class="row mb-3">
      <div class="col-12">
        <button *ngIf="canEdit" type="button" class="btn btn-primary waves-effect waves-light" (click)="openAdd()">
          <i class="mdi mdi-plus mr-1"></i> Add New User
        </button>
        <button *ngIf="canEdit" type="button" class="btn btn-outline-primary waves-effect waves-light ml-2" (click)="openBulk()">
          <i class="mdi mdi-file-upload-outline mr-1"></i> Bulk add users (CSV)
        </button>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">User List</h4>

            <div class="row mb-3">
              <div class="col-12 col-md-3 mb-2">
                <input type="text" class="form-control" placeholder="Filter by Name" [(ngModel)]="fName" (ngModelChange)="filterChanged()" />
              </div>
              <div class="col-12 col-md-3 mb-2">
                <input type="text" class="form-control" placeholder="Filter by Email" [(ngModel)]="fEmail" (ngModelChange)="filterChanged()" />
              </div>
              <div class="col-12 col-md-3 mb-2">
                <select class="form-control" [(ngModel)]="fRole" (ngModelChange)="filterChanged(true)">
                  <option [ngValue]="null">Filter by Role</option>
                  <option *ngFor="let r of roles" [ngValue]="r.roleId">{{ r.roleName }}</option>
                </select>
              </div>
              <div class="col-12 col-md-3 mb-2">
                <select class="form-control" [(ngModel)]="fStatus" (ngModelChange)="filterChanged(true)">
                  <option value="">Filter by Status</option>
                  <option value="Active">Active</option>
                  <option value="Inactive">Inactive</option>
                </select>
              </div>
            </div>

            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead>
                  <tr><th>User Id</th><th>Name</th><th>Contact</th><th>Role</th><th class="tablet-hide">Chapter / centre</th>
                    <th class="tablet-hide">Last sign-in</th><th class="tablet-hide">Added</th><th>Status</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  <tr *ngIf="loading && !users.length"><td colspan="9" class="text-center text-muted">Loading…</td></tr>
                  <tr *ngIf="!loading && !users.length"><td colspan="9" class="text-center text-muted">No users match these filters.</td></tr>
                  <tr *ngFor="let u of users" [class.text-muted]="loading">
                    <td>{{ u.user_id }}</td>
                    <td>{{ u.name }}<div *ngIf="u.role_id === 6 && (u.year || u.study_status)" class="small text-muted">
                      {{ u.year ? 'Session ' + u.year : '' }}{{ u.year && u.study_status ? ' · ' : '' }}{{ studyLabel(u.study_status) }}</div></td>
                    <td class="small">{{ u.email ?? 'No email' }}<div class="text-muted">{{ u.phone || 'No phone' }}</div></td>
                    <td><span class="badge badge-light-secondary">{{ u.role_name }}</span><span *ngFor="let n of u.additional_role_names" class="badge badge-light ml-1" title="Additional role">+ {{ n }}</span></td>
                    <td class="small tablet-hide">{{ u.chapter_name || '' }}<div *ngIf="u.rcc_center_name" class="text-muted">{{ u.rcc_center_name }}</div>
                      <span *ngIf="!u.chapter_name && !u.rcc_center_name" class="text-muted">-</span></td>
                    <td class="small tablet-hide">{{ u.last_sign_in ? (u.last_sign_in | appDate: 'datetime') : 'Never' }}
                      <div *ngIf="u.must_change_password" class="text-warning">Temporary password</div></td>
                    <td class="small tablet-hide">{{ u.created_at ? (u.created_at | appDate) : '-' }}</td>
                    <td><span class="badge" [ngClass]="u.status === 'Active' ? 'badge-success' : 'badge-danger'">{{ u.status }}</span></td>
                    <td class="text-nowrap">
                      <a *ngIf="canEdit" [routerLink]="['/admin/users', u.id, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button *ngIf="canEdit && mayReset(u)" type="button" class="btn btn-sm btn-outline-warning waves-effect ml-1"
                              (click)="resetPassword(u)" [disabled]="resetting === u.id" title="Set a temporary password and email it to the user">
                        <span *ngIf="resetting === u.id" class="spinner-border spinner-border-sm"></span>
                        <i *ngIf="resetting !== u.id" class="mdi mdi-lock-reset"></i> Reset password
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="total" (pageChange)="load()"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <!-- Temporary password that could not be emailed: shown once -->
    <ng-container *ngIf="tempPassword">
      <div class="modal fade show d-block" tabindex="-1" role="dialog">
        <div class="modal-dialog modal-dialog-centered" role="document">
          <div class="modal-content">
            <div class="modal-header"><h5 class="modal-title">Temporary password</h5></div>
            <div class="modal-body">
              <p>{{ tempPassword.message }}</p>
              <div class="d-flex align-items-center">
                <code class="h5 mb-0 mr-2 p-2 bg-light border rounded">{{ tempPassword.password }}</code>
                <button type="button" class="btn btn-sm btn-outline-secondary" (click)="copy(tempPassword.password)">
                  <i class="mdi mdi-content-copy"></i> {{ copied ? 'Copied' : 'Copy' }}
                </button>
              </div>
              <small class="text-muted d-block mt-2">It won't be shown again. {{ tempPassword.name }} must choose a new password after signing in.</small>
            </div>
            <div class="modal-footer"><button type="button" class="btn btn-primary" (click)="tempPassword = null">Done</button></div>
          </div>
        </div>
      </div>
      <div class="modal-backdrop fade show"></div>
    </ng-container>

    <!-- Bulk add users -->
    <div *ngIf="bulk" class="modal fade show d-block" tabindex="-1" (click)="bulk = null">
      <div class="modal-dialog modal-xl modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Bulk add users</h5>
            <button type="button" class="close" (click)="bulk = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="bulk.error" class="alert alert-danger">{{ bulk.error }}</div>
            <ng-container *ngIf="!bulk.result">
              <p>Upload a CSV with the columns <strong>Name</strong>, <strong>Chapter Name</strong>, <strong>Phone Number</strong> and <strong>Role</strong>
                (e.g. Sponsor, Chapter Lead, Office Coordinator). <a href="" (click)="$event.preventDefault(); template()">Download the template</a>.</p>
              <ul class="small text-muted">
                <li><strong>Name, Chapter Name and Role are required</strong>; Chapter Name must match a chapter in Rahbar.</li>
                <li>Someone brought by another person: write <em>Abdul Rahman c/o Imran Khan</em>; "Imran Khan" is kept as their c/o.</li>
                <li>Any phone number works, from any country (e.g. +966 50 062 1750). No phone number? One starting with 5 is made up
                  (never a real mobile); add the real number later.</li>
                <li>A name that looks like someone already in Rahbar <strong>in the same chapter</strong>, or twice in the file for the same chapter, is
                  <strong>not added</strong> and listed for you to check (Md / Mohd / Mohammad are treated as the same).</li>
                <li>Everyone signs in with their user ID email (shown after the upload) or phone and the temporary password, and must choose their own.
                  Students are added in the Student Directory instead.</li>
              </ul>
              <input type="file" accept=".csv,text/csv" class="form-control-file" (change)="bulk.file = $any($event.target).files?.[0] ?? null">
            </ng-container>

            <ng-container *ngIf="bulk.result as r">
              <div class="alert alert-info">{{ r.message }}</div>
              <ng-container *ngIf="r.duplicates.length">
                <h6 class="text-danger"><i class="mdi mdi-account-alert"></i> Not entered: possible duplicates ({{ r.duplicates.length }}). Please check these.</h6>
                <div class="table-responsive mb-3"><table class="table table-sm table-bordered">
                  <thead class="thead-light"><tr><th>Row</th><th>Name</th><th>Role</th><th>Chapter</th><th>Phone</th><th>Looks like</th></tr></thead>
                  <tbody><tr *ngFor="let d of r.duplicates" class="table-warning">
                    <td>{{ d['row'] }}</td><td><strong>{{ d['name'] }}</strong></td><td>{{ d['role'] }}</td><td>{{ d['chapter'] || '-' }}</td>
                    <td>{{ d['phone'] || '-' }}</td><td class="small">{{ d['looks_like'] }}</td></tr></tbody>
                </table></div>
              </ng-container>
              <ng-container *ngIf="otherProblems(r).length">
                <h6>Other rows not added or with a note</h6>
                <div class="table-responsive mb-3"><table class="table table-sm">
                  <tbody><tr *ngFor="let p of otherProblems(r)">
                    <td>Row {{ p['row'] }}</td><td>{{ p['reference'] || '' }}</td>
                    <td><span class="badge" [ngClass]="p['type'] === 'failed' ? 'badge-danger' : p['type'] === 'warning' ? 'badge-warning' : 'badge-secondary'">{{ p['type'] }}</span></td>
                    <td class="small">{{ p['reason'] }}</td></tr></tbody>
                </table></div>
              </ng-container>
              <ng-container *ngIf="r.added.length">
                <div class="d-flex align-items-center mb-1"><h6 class="mb-0 mr-auto text-success">Added ({{ r.added.length }})</h6>
                  <button type="button" class="btn btn-sm btn-outline-secondary" (click)="downloadAdded(r.added)"><i class="mdi mdi-download"></i> Download list (CSV)</button></div>
                <div class="table-responsive"><table class="table table-sm">
                  <thead class="thead-light"><tr><th>User ID</th><th>Name</th><th>c/o</th><th>Role</th><th>Chapter</th><th>Phone</th><th>Signs in with</th></tr></thead>
                  <tbody><tr *ngFor="let a of r.added">
                    <td>{{ a['user_id'] }}</td><td>{{ a['name'] }}</td><td>{{ a['care_of'] || '' }}</td><td>{{ a['role'] }}</td><td>{{ a['chapter'] || '-' }}</td>
                    <td>{{ a['phone'] }} <span *ngIf="a['phone_generated']" class="badge badge-light border" title="Made up: add the real number">made up</span></td>
                    <td class="small">{{ a['sign_in'] }}</td></tr></tbody>
                </table></div>
                <p class="small text-muted mb-0">They sign in with this email (or a real phone number) and the default temporary password, then choose their own.</p>
              </ng-container>
            </ng-container>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="bulk = null">{{ bulk.result ? 'Close' : 'Cancel' }}</button>
            <button *ngIf="!bulk.result" type="button" class="btn btn-primary" (click)="uploadBulk()" [disabled]="!bulk.file || bulk.busy">
              {{ bulk.busy ? 'Uploading…' : 'Upload' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Add User modal -->
    <ng-container *ngIf="showAdd">
      <div class="modal fade show d-block" tabindex="-1" role="dialog" (click)="showAdd = false">
        <div class="modal-dialog modal-dialog-centered" role="document" (click)="$event.stopPropagation()">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Add New User</h5>
              <button type="button" class="close" (click)="showAdd = false"><span>&times;</span></button>
            </div>
            <div class="modal-body">
              <div *ngIf="addError" class="alert alert-danger py-2">{{ addError }}</div>
              <form #f="ngForm" (ngSubmit)="saveUser()">
                <div class="form-group">
                  <label>ID (Leave blank to auto-generate)</label>
                  <input type="text" class="form-control" name="userId" [(ngModel)]="newUser.userId" placeholder="Auto-generated if empty" />
                </div>
                <div class="form-group"><label>Name</label><input type="text" class="form-control" name="name" [(ngModel)]="newUser.name" required /></div>
                <div class="form-group"><label>Contact</label><input type="text" class="form-control" name="contact" [(ngModel)]="newUser.contact" required /></div>
                <div class="form-group"><label>Email</label><input type="email" class="form-control" name="email" [(ngModel)]="newUser.email" /></div>
                <div class="form-group">
                  <label>Chapter <small class="text-muted">(optional)</small></label>
                  <select class="form-control" name="chapterId" [(ngModel)]="newUser.chapterId">
                    <option [ngValue]="null">No chapter</option>
                    <option *ngFor="let c of chapterOptions(null)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>RCC Center <small class="text-muted">(optional; needed for RCC coordinators)</small></label>
                  <select class="form-control" name="rccCenterId" [(ngModel)]="newUser.rccCenterId">
                    <option [ngValue]="null">No RCC center</option>
                    <option *ngFor="let r of rccCenters" [ngValue]="r.rccCenterId">{{ r.centerName }}</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Role</label>
                  <select class="form-control" name="roleId" [(ngModel)]="newUser.roleId" required
                          (ngModelChange)="mainChanged(newUser, $event)">
                    <option *ngFor="let r of assignableRoles" [ngValue]="r.roleId">{{ r.roleName }}</option>
                  </select>
                </div>
                <div class="form-group" *ngIf="newUser.roleId">
                  <label class="d-block mb-1">Also has these roles <span class="text-muted small">(optional)</span></label>
                  <div class="custom-control custom-checkbox custom-control-inline" *ngFor="let r of extraRoleChoices(newUser.roleId)">
                    <input type="checkbox" class="custom-control-input" [id]="'new-extra-' + r.roleId" [checked]="newUser.additionalRoleIds.includes(r.roleId)"
                           (change)="toggleExtra(newUser, r.roleId)">
                    <label class="custom-control-label" [for]="'new-extra-' + r.roleId">{{ r.roleName }}</label>
                  </div>
                  <small class="form-text text-muted">One person, one account: e.g. a Super Admin who also sponsors a student gets the Sponsor role here, not a second account.</small>
                </div>
                <div class="form-row" *ngIf="newUser.roleId === 6">
                  <div class="form-group col-md-6">
                    <label>Session Year</label>
                    <input type="number" class="form-control" name="year" min="1990" max="2100" placeholder="e.g. 2025" [(ngModel)]="newUser.year" />
                    <small class="text-muted">Picks the Payment Config (amount and frequency).</small>
                  </div>
                  <div class="form-group col-md-6">
                    <label>Payment Start Date</label>
                    <app-date-input name="paymentStartDate" [(ngModel)]="newUser.paymentStartDate"></app-date-input>
                    <small class="text-muted">The first installment is due on this date.</small>
                  </div>
                </div>
                <div class="form-group">
                  <label>Status</label>
                  <select class="form-control" name="status" [(ngModel)]="newUser.status" required>
                    <option value="Active">Active</option>
                    <option value="Inactive">Inactive</option>
                  </select>
                </div>
                <p class="small text-muted mb-3">
                  <i class="mdi mdi-information-outline mr-1"></i>A temporary password is created and emailed to the user (if they have an email).
                  They must choose their own password at first sign-in.
                </p>
                <div class="text-right">
                  <button type="submit" class="btn btn-primary waves-effect waves-light" [disabled]="f.invalid || saving">
                    {{ saving ? 'Saving…' : 'Save User' }}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </ng-container>
  `
})
export class AdminUsersComponent implements OnInit, OnDestroy {
  /** Study status code (ON_HOLD) as shown elsewhere ("On hold"). */
  studyLabel(code: string | null | undefined): string {
    return ({ STUDYING: 'Studying', ON_HOLD: 'On hold', GRADUATED: 'Graduated', DROPPED_OUT: 'Dropped out' } as Record<string, string>)[code ?? ''] ?? '';
  }

  /** Current page of users (the server pages and filters them). */
  users: UserRow[] = [];
  total = 0;
  readonly pg = new PageState(10);
  roles: RoleOption[] = [];
  chapters: Chapter[] = [];
  loading = false;

  fName = '';
  fEmail = '';
  fRole: number | null = null;
  fStatus = '';
  private typing = new Subject<void>();
  private reload = new Subject<void>();
  private sub?: Subscription;

  message = '';
  error = '';

  showAdd = false;
  saving = false;
  addError = '';
  newUser = this.blankUser();

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService) {}

  /** Bulk add users: the file chosen and the server's answer. */
  bulk: { file: File | null; busy: boolean; error: string; result: BulkUsersResult | null } | null = null;

  openBulk(): void { this.bulk = { file: null, busy: false, error: '', result: null }; }

  template(): void {
    this.api.download('/admin/users/bulk-template', {}, 'users_template.csv').subscribe({ error: (e) => this.bulk && (this.bulk.error = errorText(e, 'Could not download.')) });
  }

  uploadBulk(): void {
    const b = this.bulk;
    if (!b?.file) return;
    b.busy = true;
    b.error = '';
    const fd = new FormData();
    fd.append('file', b.file);
    this.api.post<BulkUsersResult>('/admin/users/bulk-upload', fd).subscribe({
      next: (r) => { b.busy = false; b.result = r; this.load(); },
      error: (e) => { b.busy = false; b.error = errorText(e, 'Could not upload the file.'); }
    });
  }

  /** Rows skipped / failed / with a warning, other than the duplicates listed above them. */
  otherProblems(r: BulkUsersResult): Record<string, unknown>[] {
    const dupRows = new Set(r.duplicates.map((d) => d['row']));
    return (r.report.problems ?? []).filter((p) => !dupRows.has(p['row'] as number));
  }

  /** The accounts added, as a CSV to keep or hand out. */
  downloadAdded(rows: Record<string, unknown>[]): void {
    const cols = ['user_id', 'name', 'care_of', 'role', 'chapter', 'phone', 'sign_in'];
    const esc = (v: unknown) => '"' + String(v ?? '').replace(/"/g, '""') + '"';
    const csv = ['User ID,Name,c/o,Role,Chapter,Phone,Signs in with', ...rows.map((r) => cols.map((c) => esc(r[c])).join(','))].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'users_added.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  /** Active chapters, plus the given one when it is inactive. */
  rccCenters: RccCenter[] = [];

  get canEdit(): boolean { return this.auth.can('USERS', 'EDIT'); }

  resetting: number | null = null;
  tempPassword: { name: string; message: string; password: string } | null = null;
  copied = false;

  /** Only a Super Admin may reset a Super Admin's password. */
  mayReset(u: UserRow): boolean {
    return u.role_id !== ROLE.SUPER_ADMIN || this.auth.isSuperAdmin();
  }

  resetPassword(u: UserRow): void {
    if (!confirm(`Reset the password of ${u.name}? They get a temporary password and must choose a new one when they sign in.`)) return;
    this.resetting = u.id;
    this.error = '';
    this.api.post<{ message: string; emailed: boolean; temporaryPassword?: string }>(`/admin/users/${u.id}/reset-password`, {}).subscribe({
      next: (r) => {
        this.resetting = null;
        if (r.temporaryPassword) {
          this.copied = false;
          this.tempPassword = { name: u.name, message: r.message, password: r.temporaryPassword };
        } else {
          this.message = r.message;
        }
      },
      error: (err) => { this.resetting = null; this.error = err?.error?.error ?? 'Could not reset the password.'; }
    });
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => (this.copied = true), () => {});
  }

  /** Only a Super Admin may give someone the Super Admin role. */
  get assignableRoles(): RoleOption[] {
    return this.auth.isSuperAdmin() ? this.roles : this.roles.filter((r) => r.roleId !== ROLE.SUPER_ADMIN);
  }

  chapterOptions(currentId: number | null | undefined): Chapter[] {
    return ChapterService.options(this.chapters, currentId);
  }

  ngOnInit(): void {
    this.api.get<RoleOption[]>('/admin/roles').subscribe({ next: (r) => (this.roles = r) });
    this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
    this.api.get<RccCenter[]>('/admin/rcc-centers').subscribe({ next: (r) => (this.rccCenters = r) });
    // One request stream: a newer request cancels an older one, so a slow reply can't overwrite a newer page.
    this.sub = merge(this.typing.pipe(debounceTime(300)), this.reload).pipe(
      switchMap(() => {
        this.loading = true;
        return this.api.get<{ data: UserRow[]; total: number }>('/admin/users', {
          page: this.pg.page, size: this.pg.size, name: this.fName, email: this.fEmail, roleId: this.fRole, status: this.fStatus
        }).pipe(catchError((err) => {
          // Keep the stream alive so the next filter / page change still loads.
          this.loading = false;
          this.error = err?.error?.error ?? 'Could not load users.';
          return EMPTY;
        }));
      })
    ).subscribe((r) => { this.users = r.data; this.total = r.total; this.loading = false; });
    this.load();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  /** Any filter change goes back to page 1; text filters wait until typing pauses. */
  filterChanged(immediate = false): void {
    this.pg.reset();
    if (immediate) this.load(); else this.typing.next();
  }

  load(): void { this.reload.next(); }

  openAdd(): void {
    this.newUser = this.blankUser();
    this.addError = '';
    this.showAdd = true;
  }

  saveUser(): void {
    // A blank user id is filled in by the server (next number after the highest numeric id).
    const body = { ...this.newUser, userId: this.newUser.userId.trim() };
    this.saving = true;
    this.addError = '';
    this.api.post<{ message: string; emailed: boolean; temporaryPassword?: string }>('/admin/users', body).subscribe({
      next: (res) => {
        this.saving = false;
        this.showAdd = false;
        if (res.temporaryPassword) {
          // Not emailed: show the temporary password once so the admin can pass it on.
          this.copied = false;
          this.tempPassword = { name: body.name, message: res.message, password: res.temporaryPassword };
        } else {
          this.message = res.message ?? 'User saved successfully!';
        }
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.addError = err?.error?.error ?? 'Could not save user.';
      }
    });
  }

  /** Roles that can be added besides the main one. */
  extraRoleChoices(main: number | null): RoleOption[] { return this.assignableRoles.filter((r) => r.roleId !== main); }

  /** The new main role is not also an additional one. */
  mainChanged(f: { additionalRoleIds: number[] }, main: number): void { f.additionalRoleIds = f.additionalRoleIds.filter((r) => r !== main); }

  toggleExtra(f: { additionalRoleIds: number[] }, roleId: number): void {
    f.additionalRoleIds = f.additionalRoleIds.includes(roleId) ? f.additionalRoleIds.filter((r) => r !== roleId) : [...f.additionalRoleIds, roleId];
  }

  private blankUser() {
    return { userId: '', name: '', contact: '', email: '', chapterId: null as number | null, rccCenterId: null as number | null, roleId: null as number | null, additionalRoleIds: [] as number[], status: 'Active',
      year: new Date().getFullYear() as number | null, paymentStartDate: '' };
  }
}
