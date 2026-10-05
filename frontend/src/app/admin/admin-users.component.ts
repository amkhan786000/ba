import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { PagerComponent, PageState } from '../shared/pager/pager.component';
import { EMPTY, Subject, Subscription, catchError, debounceTime, merge, switchMap } from 'rxjs';

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

/** Port of templates/admin/manage_users.html (filters, server-side pagination, Add User modal). */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PagerComponent],
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
                  <tr><th>User Id</th><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  <tr *ngIf="loading && !users.length"><td colspan="6" class="text-center text-muted">Loading…</td></tr>
                  <tr *ngIf="!loading && !users.length"><td colspan="6" class="text-center text-muted">No users match these filters.</td></tr>
                  <tr *ngFor="let u of users" [class.text-muted]="loading">
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
            <app-pager [state]="pg" [total]="total" (pageChange)="load()"></app-pager>
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
export class AdminUsersComponent implements OnInit, OnDestroy {
  /** Current page of users (the server pages and filters them). */
  users: UserRow[] = [];
  total = 0;
  readonly pg = new PageState(10);
  roles: RoleOption[] = [];
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

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<RoleOption[]>('/admin/roles').subscribe({ next: (r) => (this.roles = r) });
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

  private blankUser() {
    return { userId: '', name: '', contact: '', email: '', region: '', roleId: null as number | null, status: 'Active', password: '' };
  }
}
