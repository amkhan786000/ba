import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';

export interface UserRow {
  user_id: string;
  name: string;
  email: string | null;
  phone?: string | null;
  role_id: number;
  role_name: string;
  status: string;
  region?: string | null;
}

export interface RoleOption {
  roleId: number;
  roleName: string;
}

type PageItem = { label: string; page: number; active?: boolean; disabled?: boolean; dots?: boolean };

/** Port of templates/admin/manage_users.html (filters, 10-per-page pagination, Add User modal). */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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
        <button type="button" class="btn btn-primary waves-effect waves-light" (click)="openAdd()">
          <i class="mdi mdi-plus mr-1"></i> Add New User
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
                <input type="text" class="form-control" placeholder="Filter by Name" [(ngModel)]="fName" (ngModelChange)="applyFilters()" />
              </div>
              <div class="col-12 col-md-3 mb-2">
                <input type="text" class="form-control" placeholder="Filter by Email" [(ngModel)]="fEmail" (ngModelChange)="applyFilters()" />
              </div>
              <div class="col-12 col-md-3 mb-2">
                <select class="form-control" [(ngModel)]="fRole" (ngModelChange)="applyFilters()">
                  <option value="">Filter by Role</option>
                  <option *ngFor="let r of roles" [value]="r.roleName">{{ r.roleName }}</option>
                </select>
              </div>
              <div class="col-12 col-md-3 mb-2">
                <select class="form-control" [(ngModel)]="fStatus" (ngModelChange)="applyFilters()">
                  <option value="">Filter by Status</option>
                  <option value="Active">Active</option>
                  <option value="Inactive">Inactive</option>
                </select>
              </div>
            </div>

            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead>
                  <tr><th>User Id</th><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="6" class="text-center text-muted">Loading…</td></tr>
                  <tr *ngFor="let u of pageRows">
                    <td>{{ u.user_id }}</td>
                    <td>{{ u.name }}</td>
                    <td>{{ u.email ?? 'None' }}</td>
                    <td><span class="badge badge-light-secondary">{{ u.role_name }}</span></td>
                    <td><span class="badge" [ngClass]="u.status === 'Active' ? 'badge-success' : 'badge-danger'">{{ u.status }}</span></td>
                    <td><a [routerLink]="['/admin/users', u.user_id, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a></td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="row mt-3">
              <div class="col-sm-12 col-md-5">
                <div class="text-muted">Showing {{ showingStart }} to {{ showingEnd }} of {{ filtered.length }} entries</div>
              </div>
              <div class="col-sm-12 col-md-7">
                <nav>
                  <ul class="pagination pagination-rounded justify-content-end mb-0">
                    <li *ngFor="let p of pageItems" class="page-item" [class.active]="p.active" [class.disabled]="p.disabled || p.dots">
                      <span *ngIf="p.dots" class="page-link">...</span>
                      <a *ngIf="!p.dots" class="page-link" href="#" (click)="goTo($event, p)">{{ p.label }}</a>
                    </li>
                  </ul>
                </nav>
              </div>
            </div>
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
                <div class="form-group"><label>Chapter</label><input type="text" class="form-control" name="region" [(ngModel)]="newUser.region" required /></div>
                <div class="form-group">
                  <label>Role</label>
                  <select class="form-control" name="roleId" [(ngModel)]="newUser.roleId" required>
                    <option *ngFor="let r of roles" [ngValue]="r.roleId">{{ r.roleName }}</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Status</label>
                  <select class="form-control" name="status" [(ngModel)]="newUser.status" required>
                    <option value="Active">Active</option>
                    <option value="Inactive">Inactive</option>
                  </select>
                </div>
                <div class="form-group"><label>Password</label><input type="password" class="form-control" name="password" [(ngModel)]="newUser.password" required /></div>
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
export class AdminUsersComponent implements OnInit {
  readonly rowsPerPage = 10;

  users: UserRow[] = [];
  roles: RoleOption[] = [];
  filtered: UserRow[] = [];
  pageRows: UserRow[] = [];
  pageItems: PageItem[] = [];
  currentPage = 1;
  loading = false;

  fName = '';
  fEmail = '';
  fRole = '';
  fStatus = '';

  message = '';
  error = '';

  showAdd = false;
  saving = false;
  addError = '';
  newUser = this.blankUser();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<RoleOption[]>('/admin/roles').subscribe({ next: (r) => (this.roles = r) });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.get<UserRow[]>('/admin/users').subscribe({
      next: (u) => {
        this.users = u;
        this.loading = false;
        this.applyFilters(false);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error ?? 'Could not load users.';
      }
    });
  }

  /** Same matching rules as the original page script (case-insensitive "contains"). */
  applyFilters(resetPage = true): void {
    const has = (v: unknown, f: string) => String(v ?? '').toLowerCase().includes(f.toLowerCase());
    this.filtered = this.users.filter((u) =>
      has(u.name, this.fName) &&
      has(u.email ?? 'None', this.fEmail) &&
      has(u.role_name, this.fRole) &&
      has(u.status, this.fStatus)
    );
    if (resetPage) this.currentPage = 1;
    this.render();
  }

  get totalPages(): number { return Math.ceil(this.filtered.length / this.rowsPerPage); }
  get showingStart(): number { return this.filtered.length === 0 ? 0 : (this.currentPage - 1) * this.rowsPerPage + 1; }
  get showingEnd(): number { return Math.min(this.currentPage * this.rowsPerPage, this.filtered.length); }

  goTo(event: Event, p: PageItem): void {
    event.preventDefault();
    if (p.disabled || p.dots) return;
    this.currentPage = p.page;
    this.render();
  }

  private render(): void {
    if (this.currentPage > Math.max(this.totalPages, 1)) this.currentPage = 1;
    const start = (this.currentPage - 1) * this.rowsPerPage;
    this.pageRows = this.filtered.slice(start, start + this.rowsPerPage);

    const total = this.totalPages;
    const items: PageItem[] = [];
    if (total > 1) {
      const cur = this.currentPage;
      const range = 1;
      items.push({ label: 'Previous', page: cur - 1, disabled: cur === 1 });
      for (let i = 1; i <= total; i++) {
        if (i === 1 || i === total || (i >= cur - range && i <= cur + range)) {
          items.push({ label: String(i), page: i, active: i === cur });
        } else if (i === cur - range - 1 || i === cur + range + 1) {
          items.push({ label: '...', page: i, dots: true });
        }
      }
      items.push({ label: 'Next', page: cur + 1, disabled: cur === total });
    }
    this.pageItems = items;
  }

  openAdd(): void {
    this.newUser = this.blankUser();
    this.addError = '';
    this.showAdd = true;
  }

  saveUser(): void {
    const body = { ...this.newUser, userId: this.newUser.userId.trim() || this.nextUserId() };
    this.saving = true;
    this.addError = '';
    this.api.post<{ message: string }>('/admin/users', body).subscribe({
      next: (res) => {
        this.saving = false;
        this.showAdd = false;
        this.message = res.message ?? 'User saved successfully!';
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.addError = err?.error?.error ?? 'Could not save user.';
      }
    });
  }

  /** Same rule as the Flask page: highest numeric id + 1, or 1001 if there are none. */
  private nextUserId(): string {
    const ids = this.users.map((u) => Number(u.user_id)).filter((n) => Number.isInteger(n));
    return String(ids.length ? Math.max(...ids) + 1 : 1001);
  }

  private blankUser() {
    return { userId: '', name: '', contact: '', email: '', region: '', roleId: null as number | null, status: 'Active', password: '' };
  }
}
