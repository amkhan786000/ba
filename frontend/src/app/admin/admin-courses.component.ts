import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { Institution } from './admin-course-edit.component';
import { CardTableDirective } from '../shared/card-table.directive';

export interface CourseRow {
  course_id: number;
  institution_id: string;
  institution_name: string;
  course_name: string;
  course_description: string | null;
  fees_per_semester: number | null;
  number_of_semesters: number | null;
}

/** Port of templates/admin/manage_courses.html */
@Component({
  selector: 'app-admin-courses',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Courses</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a *ngIf="canEdit" [routerLink]="['/', section, 'courses', 'new']" class="btn btn-primary btn-responsive mr-1"><i class="mdi mdi-plus mr-1"></i>Add New Course</a>
        <a *ngIf="canEdit" [routerLink]="['/', section, 'institutions', 'new']" class="btn btn-success btn-responsive"><i class="mdi mdi-bank mr-1"></i>Add New Institution</a>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Course List</h4>
            <div class="row mb-3">
              <div class="col-12 col-md-4">
                <input type="text" class="form-control" placeholder="Search by Course or Institution..." [(ngModel)]="search" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>Course Name</th><th>Institution</th><th>Description</th><th>Fees/Sem</th><th>Semesters</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngFor="let c of visible | paginate: pg.page : pg.size">
                    <td>{{ c.course_name }}</td>
                    <td>{{ c.institution_name }}</td>
                    <td>{{ c.course_description }}</td>
                    <td>{{ c.fees_per_semester }}</td>
                    <td>{{ c.number_of_semesters }}</td>
                    <td class="text-nowrap">
                      <a *ngIf="canEdit" [routerLink]="['/', section, 'courses', c.course_id, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button *ngIf="canEdit" type="button" class="btn btn-sm btn-danger waves-effect ml-1" (click)="deleteCourse(c)">Delete</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="visible.length"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Institution List</h4>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>ID</th><th>Institution</th><th>Address</th><th>Contact</th><th>Courses</th><th *ngIf="canEdit">Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!institutions.length"><td colspan="6" class="text-center text-muted">No institutions yet.</td></tr>
                  <tr *ngFor="let i of institutions | paginate: ipg.page : ipg.size">
                    <td>{{ i.institutionId }}</td>
                    <td>{{ i.institutionName }}</td>
                    <td>{{ i.address }}</td>
                    <td>{{ i.contactNumber }}<div class="small text-muted">{{ i.email }}</div></td>
                    <td>{{ courseCount(i.institutionId) }}</td>
                    <td *ngIf="canEdit" class="text-nowrap">
                      <a [routerLink]="['/', section, 'institutions', i.institutionId, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button type="button" class="btn btn-sm btn-danger waves-effect ml-1" (click)="deleteInstitution(i)">Delete</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="ipg" [total]="institutions.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminCoursesComponent implements OnInit {
  readonly pg = new PageState();
  readonly ipg = new PageState();
  /** Area this page is shown in ('admin' or 'office'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';
  courses: CourseRow[] = [];
  institutions: Institution[] = [];
  search = '';
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('COURSES', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<CourseRow[]>('/admin/courses').subscribe({
      next: (c) => (this.courses = c),
      error: (e) => (this.error = errorText(e, 'Could not load courses.'))
    });
    this.api.get<Institution[]>('/admin/institutions').subscribe({
      next: (i) => (this.institutions = [...i].sort((a, b) => a.institutionName.localeCompare(b.institutionName))),
      error: (e) => (this.error = errorText(e, 'Could not load institutions.'))
    });
  }

  courseCount(institutionId: string): number { return this.courses.filter((c) => c.institution_id === institutionId).length; }

  /** Refused by the server while students are assigned to the course. */
  deleteCourse(c: CourseRow): void {
    if (!confirm(`Delete the course "${c.course_name}" (${c.institution_name})?`)) return;
    this.api.delete(`/admin/courses/${c.course_id}`).subscribe({
      next: () => { this.message = `Course "${c.course_name}" deleted.`; this.error = ''; this.load(); },
      error: (e) => { this.message = ''; this.error = errorText(e, 'Could not delete the course.'); }
    });
  }

  /** Refused by the server while the institution has courses or students. */
  deleteInstitution(i: Institution): void {
    if (!confirm(`Delete the institution "${i.institutionName}"?`)) return;
    this.api.delete<{ message: string }>(`/admin/institutions/${encodeURIComponent(i.institutionId)}`).subscribe({
      next: () => { this.message = `Institution "${i.institutionName}" deleted.`; this.error = ''; this.load(); },
      error: (e) => { this.message = ''; this.error = errorText(e, 'Could not delete the institution.'); }
    });
  }

  get visible(): CourseRow[] {
    const f = this.search.toLowerCase();
    return this.courses.filter((c) => [c.course_name, c.institution_name].some((v) => (v ?? '').toLowerCase().includes(f)));
  }
}
