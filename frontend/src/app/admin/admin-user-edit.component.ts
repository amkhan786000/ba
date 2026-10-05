import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { RoleOption, UserRow } from './admin-users.component';

/** Port of templates/admin/edit_user.html */
@Component({
  selector: 'app-admin-user-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box"><h4 class="page-title">Edit User</h4></div>
      </div>
    </div>

    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
        <div class="card" *ngIf="form">
          <div class="card-body">
            <h4 class="header-title mb-3">Edit User Details</h4>
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="name">Name</label>
                <input type="text" class="form-control" id="name" name="name" [(ngModel)]="form.name" required>
              </div>
              <div class="form-group">
                <label for="email">Email</label>
                <input type="email" class="form-control" id="email" name="email" [(ngModel)]="form.email" required>
              </div>
              <div class="row">
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="role_id">Role</label>
                    <select class="form-control" id="role_id" name="roleId" [(ngModel)]="form.roleId" required>
                      <option *ngFor="let r of roles" [ngValue]="r.roleId">{{ r.roleName }}</option>
                    </select>
                  </div>
                </div>
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="status">Status</label>
                    <select class="form-control" id="status" name="status" [(ngModel)]="form.status" required>
                      <option value="Active">Active</option>
                      <option value="Inactive">Inactive</option>
                      <option value="registered">Registered</option>
                      <option value="recognised">Recognised</option>
                    </select>
                  </div>
                </div>
              </div>
              <div class="d-flex justify-content-between mt-3">
                <a routerLink="/admin/users" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving">
                  {{ saving ? 'Saving…' : 'Save Changes' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminUserEditComponent implements OnInit {
  /** Bound from the route parameter :userId (withComponentInputBinding). */
  @Input() userId = '';

  roles: RoleOption[] = [];
  form: { name: string; email: string; roleId: number; status: string } | null = null;
  error = '';
  saving = false;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.api.get<RoleOption[]>('/admin/roles').subscribe({ next: (r) => (this.roles = r) });
    this.api.get<UserRow>(`/admin/users/${encodeURIComponent(this.userId)}`).subscribe({
      next: (u) => {
        this.form = { name: u.name, email: u.email ?? '', roleId: u.role_id, status: u.status };
      },
      error: (err) => (this.error = err?.error?.error ?? 'Could not load user.')
    });
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    this.api.put<{ message: string }>(`/admin/users/${encodeURIComponent(this.userId)}`, this.form).subscribe({
      next: () => this.router.navigate(['/admin/users']),
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.error ?? 'Could not save changes.';
      }
    });
  }
}
