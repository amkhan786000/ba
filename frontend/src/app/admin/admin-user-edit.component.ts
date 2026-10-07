import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { RoleOption, UserRow } from './admin-users.component';
import { AuthService } from '../core/services/auth.service';
import { ChapterService } from '../core/services/chapter.service';
import { ROLE } from '../core/models/user.model';
import { Chapter } from './admin-chapters.component';
import { RccCenter } from './admin-rcc-centers.component';

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
                <input type="email" class="form-control" id="email" name="email" [(ngModel)]="form.email" [placeholder]="masked ? 'Hidden' : 'Optional'" [disabled]="masked">
              </div>
              <div *ngIf="masked" class="alert alert-light border small py-2">
                <i class="mdi mdi-lock-outline"></i> This is a sponsor. Their contact and personal details are hidden from you, so they can't be changed here.
              </div>
              <div class="row">
                <div class="col-md-6 form-group">
                  <label for="chapter">Chapter</label>
                  <select class="form-control" id="chapter" name="chapterId" [(ngModel)]="form.chapterId" [disabled]="masked">
                    <option [ngValue]="null">No chapter</option>
                    <option *ngFor="let c of chapterOptions()" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                  </select>
                  <small class="text-muted">A Chapter Lead edits this chapter.</small>
                </div>
                <div class="col-md-6 form-group">
                  <label for="rcc">RCC Center</label>
                  <select class="form-control" id="rcc" name="rccCenterId" [(ngModel)]="form.rccCenterId" [disabled]="masked">
                    <option [ngValue]="null">No RCC center</option>
                    <option *ngFor="let r of rccCenters" [ngValue]="r.rccCenterId">{{ r.centerName }}</option>
                  </select>
                  <small class="text-muted">An RCC Coordinator edits this center.</small>
                </div>
              </div>
              <div class="row">
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="role_id">Role</label>
                    <select class="form-control" id="role_id" name="roleId" [(ngModel)]="form.roleId" required>
                      <option *ngFor="let r of assignableRoles" [ngValue]="r.roleId">{{ r.roleName }}</option>
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
  /** users.id, bound from the route parameter :id (withComponentInputBinding). */
  @Input() id = '';

  roles: RoleOption[] = [];
  form: { name: string; email: string; roleId: number; status: string; chapterId: number | null; rccCenterId: number | null } | null = null;
  chapters: Chapter[] = [];
  rccCenters: RccCenter[] = [];
  private originalChapterId: number | null = null;
  /** Sponsor whose details are hidden from this user: those fields can't be edited. */
  masked = false;
  error = '';
  saving = false;

  constructor(private api: ApiService, private router: Router, private auth: AuthService, private chapterList: ChapterService) {}

  /** Only a Super Admin may give someone the Super Admin role. */
  get assignableRoles(): RoleOption[] {
    const own = this.form?.roleId;
    return this.auth.currentUser()?.roleId === ROLE.SUPER_ADMIN
      ? this.roles : this.roles.filter((r) => r.roleId !== ROLE.SUPER_ADMIN || r.roleId === own);
  }

  chapterOptions(): Chapter[] {
    return ChapterService.options(this.chapters, this.originalChapterId);
  }

  ngOnInit(): void {
    this.api.get<RoleOption[]>('/admin/roles').subscribe({ next: (r) => (this.roles = r) });
    this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
    this.api.get<RccCenter[]>('/admin/rcc-centers').subscribe({ next: (r) => (this.rccCenters = r) });
    this.api.get<UserRow>(`/admin/users/${encodeURIComponent(this.id)}`).subscribe({
      next: (u) => {
        this.originalChapterId = u.chapter_id ?? null;
        this.masked = !!u.masked;
        this.form = { name: u.name, email: u.email ?? '', roleId: u.role_id, status: u.status,
                      chapterId: u.chapter_id ?? null, rccCenterId: u.rcc_center_id ?? null };
      },
      error: (err) => (this.error = err?.error?.error ?? 'Could not load user.')
    });
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    this.api.put<{ message: string }>(`/admin/users/${encodeURIComponent(this.id)}`, this.form).subscribe({
      next: () => this.router.navigate(['/admin/users']),
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.error ?? 'Could not save changes.';
      }
    });
  }
}
