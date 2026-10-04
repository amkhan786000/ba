import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { RoleRow } from './admin-roles.component';

/** Admin > Roles: add a role (/admin/roles/new) or edit one (/admin/roles/:id/edit). */
@Component({
  selector: 'app-admin-role-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ isEdit ? 'Edit' : 'Add' }} Role</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <app-alerts [(error)]="error"></app-alerts>
        <div *ngIf="role.builtIn" class="alert alert-info">
          This is a built-in role (ID {{ role.roleId }}). You can change its name and description, but its access rules stay the same.
        </div>
        <div class="card">
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="role_name">Role Name</label>
                <input type="text" class="form-control" id="role_name" name="roleName" [(ngModel)]="role.roleName" required maxlength="50" />
              </div>
              <div class="form-group">
                <label for="description">Description</label>
                <textarea class="form-control" id="description" name="description" rows="3" [(ngModel)]="role.description"></textarea>
              </div>
              <div class="d-flex justify-content-between">
                <a routerLink="/admin/roles" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving">
                  {{ isEdit ? 'Update Role' : 'Save Role' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminRoleEditComponent implements OnInit {
  /** From the route parameter :id (absent on /admin/roles/new). */
  @Input() id?: string;

  role: RoleRow = { roleName: '', description: '' };
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  get isEdit(): boolean { return !!this.id; }

  ngOnInit(): void {
    if (!this.id) return;
    this.api.get<RoleRow>(`/admin/roles/${this.id}`).subscribe({
      next: (r) => (this.role = { ...r }),
      error: (e) => (this.error = errorText(e, 'Could not load the role.'))
    });
  }

  save(): void {
    this.saving = true;
    const body = { roleName: this.role.roleName, description: this.role.description };
    const request = this.isEdit
      ? this.api.put<RoleRow>(`/admin/roles/${this.id}`, body)
      : this.api.post<RoleRow>('/admin/roles', body);
    request.subscribe({
      next: () => this.router.navigate(['/admin/roles']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the role.'); }
    });
  }
}
