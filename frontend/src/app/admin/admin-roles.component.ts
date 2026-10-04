import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { localDate } from '../shared/format';

export interface RoleRow {
  roleId?: number;
  roleName: string;
  description: string | null;
  builtIn?: boolean;
  userCount?: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Admin > Roles: list, add, edit and delete roles. Built-in roles (1-8) can be renamed but not deleted. */
@Component({
  selector: 'app-admin-roles',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Roles</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a routerLink="/admin/roles/new" class="btn btn-primary btn-responsive"><i class="mdi mdi-plus mr-1"></i>Add New Role</a>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Role List</h4>
            <div class="row mb-3">
              <div class="col-12 col-md-4">
                <input type="text" class="form-control" placeholder="Search role name or description..." [(ngModel)]="search" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>ID</th><th>Role Name</th><th>Description</th><th>Users</th><th>Last Updated</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngFor="let r of visible">
                    <td>{{ r.roleId }}</td>
                    <td>
                      {{ r.roleName }}
                      <span *ngIf="r.builtIn" class="badge badge-light ml-1" title="Used by the application's access rules">Built-in</span>
                    </td>
                    <td>{{ r.description || '--' }}</td>
                    <td>{{ r.userCount ?? 0 }}</td>
                    <td>{{ date(r.updatedAt) }}</td>
                    <td>
                      <a [routerLink]="['/admin/roles', r.roleId, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button type="button" class="btn btn-sm btn-danger waves-effect ml-1" (click)="remove(r)"
                              [disabled]="r.builtIn || (r.userCount ?? 0) > 0"
                              [title]="r.builtIn ? 'Built-in roles cannot be deleted' : ((r.userCount ?? 0) > 0 ? 'Users still have this role' : 'Delete role')">Delete</button>
                    </td>
                  </tr>
                  <tr *ngIf="!visible.length"><td colspan="6" class="text-center text-muted">No roles found.</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminRolesComponent implements OnInit {
  roles: RoleRow[] = [];
  search = '';
  message = '';
  error = '';
  readonly date = localDate;

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<RoleRow[]>('/admin/roles').subscribe({
      next: (r) => (this.roles = r),
      error: (e) => (this.error = errorText(e, 'Could not load roles.'))
    });
  }

  get visible(): RoleRow[] {
    const f = this.search.toLowerCase();
    return this.roles.filter((r) => [r.roleName, r.description].some((v) => (v ?? '').toLowerCase().includes(f)));
  }

  remove(r: RoleRow): void {
    if (!confirm(`Are you sure you want to delete the role "${r.roleName}"?`)) return;
    this.api.delete(`/admin/roles/${r.roleId}`).subscribe({
      next: () => { this.message = 'Role deleted successfully!'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the role.'))
    });
  }
}
