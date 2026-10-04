import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

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
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Courses</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a routerLink="/admin/courses/new" class="btn btn-primary btn-responsive mr-1"><i class="mdi mdi-plus mr-1"></i>Add New Course</a>
        <a routerLink="/admin/institutions/new" class="btn btn-success btn-responsive"><i class="mdi mdi-bank mr-1"></i>Add New Institution</a>
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
                  <tr *ngFor="let c of visible">
                    <td>{{ c.course_name }}</td>
                    <td>{{ c.institution_name }}</td>
                    <td>{{ c.course_description }}</td>
                    <td>{{ c.fees_per_semester }}</td>
                    <td>{{ c.number_of_semesters }}</td>
                    <td><a [routerLink]="['/admin/courses', c.course_id, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminCoursesComponent implements OnInit {
  courses: CourseRow[] = [];
  search = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<CourseRow[]>('/admin/courses').subscribe({
      next: (c) => (this.courses = c),
      error: (e) => (this.error = errorText(e, 'Could not load courses.'))
    });
  }

  get visible(): CourseRow[] {
    const f = this.search.toLowerCase();
    return this.courses.filter((c) => [c.course_name, c.institution_name].some((v) => (v ?? '').toLowerCase().includes(f)));
  }
}
