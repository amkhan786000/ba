import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CourseRow } from './admin-courses.component';
import { Institution } from './admin-course-edit.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../shared/pager/server-table';
import { CardTableDirective } from '../shared/card-table.directive';

interface ManagedStudent {
  id: number; user_id: string; student_name: string; student_email: string | null; student_phone: string | null; chapter_name: string | null;
  sponsor_name: string | null; institution_name: string | null; course_name: string | null;
  institution_id: string | null; course_id: number | null;
}

/** Port of templates/admin/manage_students.html (filters + assign institution/course). */
@Component({
  selector: 'app-admin-manage-students',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, CardTableDirective],
  styles: [`.btn-xs { padding: .15rem .45rem; font-size: .75rem; } .filter-btns { margin-right: 4px; }`],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Students</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Filters &amp; Search</h4>
            <div class="row">
              <div class="col-12 col-md-3">
                <div class="form-group"><label>Search (Any Field)</label>
                  <input type="text" class="form-control" placeholder="Name, Email, Phone..." [(ngModel)]="search" (ngModelChange)="table.search()">
                </div>
              </div>
              <div class="col-12 col-md-3">
                <div class="form-group"><label>Institution</label>
                  <select class="form-control" [(ngModel)]="fInst">
                    <option value="">All Institutions</option>
                    <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }}</option>
                  </select>
                </div>
              </div>
              <div class="col-12 col-md-3">
                <div class="form-group"><label>Course</label>
                  <select class="form-control" [(ngModel)]="fCourse">
                    <option value="">All Courses</option>
                    <option *ngFor="let c of courses" [value]="'' + c.course_id">{{ c.course_name }}</option>
                  </select>
                </div>
              </div>
              <div class="col-12 col-md-3">
                <div class="form-group" style="margin-top: 28px;">
                  <button class="btn btn-primary filter-btns" (click)="applyFilters()">Apply Filters</button>
                  <button class="btn btn-secondary filter-btns" (click)="reset()">Reset</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <div class="table-responsive">
              <table class="table table-centered table-striped mb-0">
                <thead><tr><th>Student Name</th><th>Email</th><th>Student Phone</th><th>Chapter</th><th>Sponsor</th><th>Institution</th><th>Course</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!table.loading && !table.rows.length"><td colspan="8" class="text-center text-muted">No students found.</td></tr>
                  <tr *ngFor="let s of table.rows">
                    <td><strong>{{ s.student_name }}</strong></td>
                    <td>{{ s.student_email }}</td>
                    <td>{{ s.student_phone }}</td>
                    <td>{{ s.chapter_name }}</td>
                    <td>{{ s.sponsor_name || 'Unassigned' }}</td>
                    <td>{{ s.institution_name || 'Unassigned' }}</td>
                    <td>{{ s.course_name || 'Unassigned' }}</td>
                    <td><button type="button" class="btn btn-xs btn-primary waves-effect waves-light" (click)="openAssign(s)"><i class="mdi mdi-pencil"></i> Assign</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="assign" class="modal fade show d-block" tabindex="-1" (click)="assign = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h4 class="modal-title">Assign Institution &amp; Course</h4>
            <button type="button" class="close" (click)="assign = null">&times;</button>
          </div>
          <form (ngSubmit)="saveAssign()">
            <div class="modal-body">
              <div class="form-group"><label>Institution</label>
                <select name="institutionId" class="form-control" [(ngModel)]="assign.institutionId" (ngModelChange)="assign.courseId = ''">
                  <option value="">Select Institution</option>
                  <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }}</option>
                </select>
              </div>
              <div class="form-group"><label>Course</label>
                <select name="courseId" class="form-control" [(ngModel)]="assign.courseId">
                  <option value="">Select Course</option>
                  <option *ngFor="let c of coursesFor(assign.institutionId)" [value]="'' + c.course_id">{{ c.course_name }}</option>
                </select>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-light" (click)="assign = null">Close</button>
              <button type="submit" class="btn btn-primary" [disabled]="!assign.institutionId || !assign.courseId || saving">Save Changes</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class AdminManageStudentsComponent implements OnInit, OnDestroy {
  institutions: Institution[] = [];
  courses: CourseRow[] = [];
  search = '';
  fInst = '';
  fCourse = '';
  readonly table = new ServerTable<ManagedStudent>(
    (p) => this.api.get<PagedResult<ManagedStudent>>('/admin/manage-students', { q: this.search, institutionId: this.fInst, courseId: this.fCourse, ...p }),
    (e) => (this.error = errorText(e, 'Could not load students.')), 10);
  /** id: the student's users.id. */
  assign: { id: number; institutionId: string; courseId: string } | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<Institution[]>('/admin/institutions').subscribe({ next: (i) => (this.institutions = i) });
    this.api.get<CourseRow[]>('/admin/courses').subscribe({ next: (c) => (this.courses = c) });
    this.load();
  }

  ngOnDestroy(): void { this.table.destroy(); }

  load(): void { this.table.load(); }

  /** Institution + course dropdowns, plus text search across the row (on the server). */
  applyFilters(): void { this.table.filter(); }

  reset(): void { this.search = ''; this.fInst = ''; this.fCourse = ''; this.table.filter(); }

  coursesFor(instId: string): CourseRow[] {
    return instId ? this.courses.filter((c) => String(c.institution_id) === String(instId)) : [];
  }

  openAssign(s: ManagedStudent): void {
    this.assign = { id: s.id, institutionId: s.institution_id ?? '', courseId: s.course_id !== null ? String(s.course_id) : '' };
  }

  saveAssign(): void {
    if (!this.assign) return;
    this.saving = true;
    this.api.post<{ message: string }>('/admin/manage-students/assign', this.assign).subscribe({
      next: (r) => { this.saving = false; this.assign = null; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the assignment.'); }
    });
  }
}
