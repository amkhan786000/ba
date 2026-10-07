import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { RoleRow } from './admin-roles.component';

interface SectionOption { key: string; label: string }

/**
 * Admin > Roles: add a role (/admin/roles/new) or edit one (/admin/roles/:id/edit), including which admin
 * screens it may view or manage and whether it only covers the user's own chapter / RCC center.
 */
@Component({
  selector: 'app-admin-role-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  styles: [`
    .perm-table td, .perm-table th { vertical-align: middle; }
    .perm-table td.check, .perm-table th.check { width: 110px; text-align: center; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ isEdit ? 'Edit' : 'Add' }} Role</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-lg-10 offset-lg-1">
        <app-alerts [(error)]="error"></app-alerts>
        <form #f="ngForm" (ngSubmit)="save()">
          <div class="card">
            <div class="card-body">
              <div class="row">
                <div class="col-md-6 form-group">
                  <label for="role_name">Role Name</label>
                  <input type="text" class="form-control" id="role_name" name="roleName" [(ngModel)]="role.roleName" required maxlength="50" [disabled]="!canEdit" />
                </div>
                <div class="col-md-6 form-group">
                  <label for="description">Description</label>
                  <input type="text" class="form-control" id="description" name="description" [(ngModel)]="role.description" [disabled]="!canEdit" />
                </div>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-body">
              <h4 class="header-title mb-1">Permissions</h4>
              <p class="text-muted mb-3">Which admin screens users with this role can open (View) or change (Manage: add, edit, delete). Manage includes View.</p>

              <div *ngIf="role.superAdmin" class="alert alert-info mb-0">The Super Admin can always do everything; this can't be changed.</div>

              <ng-container *ngIf="!role.superAdmin">
                <div class="form-group">
                  <label for="scope">Records covered</label>
                  <select id="scope" name="scope" class="form-control" [(ngModel)]="scope" [disabled]="!canEdit">
                    <option value="ALL">All records</option>
                    <option value="CHAPTER">Only the user's own chapter</option>
                    <option value="RCC">Only the user's own RCC center</option>
                  </select>
                  <small class="text-muted" *ngIf="scope === 'CHAPTER'">Users edit only the chapter set on their account (Manage Users &gt; Chapter) and can't add or delete chapters.</small>
                  <small class="text-muted" *ngIf="scope === 'RCC'">Users see and edit only the RCC center set on their account (Manage Users &gt; RCC Center) and can't add or delete centers.</small>
                </div>

                <div class="table-responsive">
                  <table class="table table-sm table-striped perm-table mb-0">
                    <thead>
                      <tr>
                        <th>Screen</th>
                        <th class="check"><a href="" (click)="$event.preventDefault(); toggleColumn('VIEW')" title="Tick / untick all">View</a></th>
                        <th class="check"><a href="" (click)="$event.preventDefault(); toggleColumn('EDIT')" title="Tick / untick all">Manage</a></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr *ngFor="let s of sections">
                        <td>{{ s.label }}</td>
                        <td class="check">
                          <input type="checkbox" [checked]="has(s.key, 'VIEW')" [disabled]="!canEdit || has(s.key, 'EDIT')"
                                 (change)="toggle(s.key, 'VIEW')" [attr.aria-label]="'View ' + s.label">
                        </td>
                        <td class="check">
                          <input type="checkbox" [checked]="has(s.key, 'EDIT')" [disabled]="!canEdit"
                                 (change)="toggle(s.key, 'EDIT')" [attr.aria-label]="'Manage ' + s.label">
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </ng-container>
            </div>
          </div>

          <div class="d-flex justify-content-between mb-4">
            <a routerLink="/admin/roles" class="btn btn-light btn-lg">Back</a>
            <button *ngIf="canEdit" type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving">
              {{ isEdit ? 'Update Role' : 'Save Role' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class AdminRoleEditComponent implements OnInit {
  /** From the route parameter :id (absent on /admin/roles/new). */
  @Input() id?: string;

  role: RoleRow = { roleName: '', description: '' };
  sections: SectionOption[] = [];
  /** Granted keys, e.g. "USERS:EDIT"; EDIT always comes with VIEW. */
  granted = new Set<string>();
  scope = 'ALL';
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router, private auth: AuthService) {}

  get isEdit(): boolean { return !!this.id; }
  get canEdit(): boolean { return this.auth.can('ROLES', 'EDIT'); }

  ngOnInit(): void {
    this.api.get<SectionOption[]>('/admin/roles/sections').subscribe({
      next: (s) => (this.sections = s),
      error: (e) => (this.error = errorText(e, 'Could not load the list of screens.'))
    });
    if (!this.id) return;
    this.api.get<RoleRow>(`/admin/roles/${this.id}`).subscribe({
      next: (r) => {
        this.role = { ...r };
        this.granted = new Set(r.permissions ?? []);
        this.scope = r.scope ?? 'ALL';
      },
      error: (e) => (this.error = errorText(e, 'Could not load the role.'))
    });
  }

  has(section: string, level: 'VIEW' | 'EDIT'): boolean {
    return this.granted.has(section + ':' + level);
  }

  /** Manage includes View; removing View also removes Manage. */
  toggle(section: string, level: 'VIEW' | 'EDIT'): void {
    const view = section + ':VIEW', edit = section + ':EDIT';
    if (level === 'EDIT') {
      if (this.granted.has(edit)) this.granted.delete(edit);
      else { this.granted.add(edit); this.granted.add(view); }
    } else if (this.granted.has(view)) {
      this.granted.delete(view);
      this.granted.delete(edit);
    } else {
      this.granted.add(view);
    }
  }

  /** Header click: tick the whole column, or untick it when every row is already ticked. */
  toggleColumn(level: 'VIEW' | 'EDIT'): void {
    const all = this.sections.every((s) => this.has(s.key, level));
    for (const s of this.sections) {
      if (all === this.has(s.key, level)) this.toggle(s.key, level);
    }
  }

  save(): void {
    this.saving = true;
    const body = { roleName: this.role.roleName, description: this.role.description };
    const access = { permissions: [...this.granted], scope: this.scope };
    const saved$ = this.isEdit
      ? this.api.put<RoleRow>(`/admin/roles/${this.id}`, body)
      : this.api.post<RoleRow>('/admin/roles', body);
    saved$.pipe(
      switchMap((r) => this.role.superAdmin ? [r] : this.api.put<RoleRow>(`/admin/roles/${r.roleId}/access`, access))
    ).subscribe({
      next: () => {
        // The signed-in user's own role may have changed: pick up the new menu right away.
        this.auth.refreshAccess().subscribe({ error: () => {} });
        this.router.navigate(['/admin/roles']);
      },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the role.'); }
    });
  }
}
