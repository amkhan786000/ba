import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { localDate } from '../shared/format';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';

export interface RoleRow {
  roleId?: number;
  roleName: string;
  description: string | null;
  builtIn?: boolean;
  /** Admin-screen permission keys, e.g. "USERS:EDIT" (see Section). */
  permissions?: string[];
  /** ALL, CHAPTER (own chapter only) or RCC (own RCC center only). */
  scope?: string;
  /** The Super Admin always has every permission. */
  superAdmin?: boolean;
  userCount?: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Admin > Roles & Permissions: list, add, edit and delete roles. Built-in roles (1-8) can't be deleted. */
@Component({
  selector: 'app-admin-roles',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, PaginatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Roles &amp; Permissions</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a *ngIf="canEdit" routerLink="/admin/roles/new" class="btn btn-primary btn-responsive"><i class="mdi mdi-plus mr-1"></i>Add New Role</a>
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
                <thead><tr><th>ID</th><th>Role Name</th><th>Description</th><th>Access</th><th>Users</th><th>Last Updated</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngFor="let r of visible | paginate: pg.page : pg.size">
                    <td>{{ r.roleId }}</td>
                    <td>
                      {{ r.roleName }}
                      <span *ngIf="r.builtIn" class="badge badge-light ml-1" title="Used by the application's access rules">Built-in</span>
                    </td>
                    <td>{{ r.description || '--' }}</td>
                    <td><small>{{ access(r) }}</small></td>
                    <td>{{ r.userCount ?? 0 }}</td>
                    <td>{{ date(r.updatedAt) }}</td>
                    <td>
                      <a [routerLink]="['/admin/roles', r.roleId, 'edit']" class="btn btn-sm btn-primary waves-effect">{{ canEdit ? 'Edit' : 'View' }}</a>
                      <button *ngIf="canEdit" type="button" class="btn btn-sm btn-danger waves-effect ml-1" (click)="remove(r)"
                              [disabled]="r.builtIn || (r.userCount ?? 0) > 0"
                              [title]="r.builtIn ? 'Built-in roles cannot be deleted' : ((r.userCount ?? 0) > 0 ? 'Users still have this role' : 'Delete role')">Delete</button>
                    </td>
                  </tr>
                  <tr *ngIf="!visible.length"><td colspan="6" class="text-center text-muted">No roles found.</td></tr>
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
export class AdminRolesComponent implements OnInit {
  readonly pg = new PageState();
  roles: RoleRow[] = [];
  search = '';
  message = '';
  error = '';
  readonly date = localDate;

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('ROLES', 'EDIT'); }

  /** Short summary of what the role may do, e.g. "Manage 7, view 1 screens · own chapter". */
  access(r: RoleRow): string {
    if (r.superAdmin) return 'Everything';
    const p = r.permissions ?? [];
    const manage = p.filter((k) => k.endsWith(':EDIT')).length;
    const view = p.filter((k) => k.endsWith(':VIEW')).length - manage;
    const parts = [manage ? 'Manage ' + manage : '', view ? 'view ' + view : ''].filter(Boolean);
    const scope = r.scope === 'CHAPTER' ? ' · own chapter' : r.scope === 'RCC' ? ' · own RCC center' : '';
    return parts.length ? parts.join(', ') + ' screen(s)' + scope : 'Own portal only';
  }

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
