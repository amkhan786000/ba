import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { CourseRow } from './admin-courses.component';

export interface Institution { institutionId: string; institutionName: string }

interface Course {
  courseId?: number;
  institutionId: string;
  courseName: string;
  courseDescription: string;
  feesPerSemester: number | null;
  numberOfSemesters: number | null;
}

/** Port of templates/admin/edit_course.html (add + edit). */
@Component({
  selector: 'app-admin-course-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ id ? 'Edit' : 'Add' }} Course</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <app-alerts [(error)]="error"></app-alerts>
        <div class="card">
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="institution_id">Institution</label>
                <select class="form-control" id="institution_id" name="institutionId" [(ngModel)]="course.institutionId" required>
                  <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }}</option>
                </select>
              </div>
              <div class="form-group">
                <label for="course_name">Course Name</label>
                <input type="text" class="form-control" id="course_name" name="courseName" [(ngModel)]="course.courseName" required>
              </div>
              <div class="form-group">
                <label for="course_description">Course Description</label>
                <textarea class="form-control" id="course_description" name="courseDescription" rows="3" [(ngModel)]="course.courseDescription" required></textarea>
              </div>
              <div class="row">
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="fees_per_semester">Fees per Semester</label>
                    <input type="number" class="form-control" id="fees_per_semester" name="feesPerSemester" [(ngModel)]="course.feesPerSemester" required>
                  </div>
                </div>
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="number_of_semesters">Number of Semesters</label>
                    <input type="number" class="form-control" id="number_of_semesters" name="numberOfSemesters" [(ngModel)]="course.numberOfSemesters" required>
                  </div>
                </div>
              </div>
              <div class="d-flex justify-content-between">
                <a [routerLink]="['/', section, 'courses']" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving">Save Course</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminCourseEditComponent implements OnInit {
  /** Area this page is shown in ('admin' or 'office'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';
  @Input() id?: string;

  institutions: Institution[] = [];
  course: Course = { institutionId: '', courseName: '', courseDescription: '', feesPerSemester: null, numberOfSemesters: null };
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.api.get<Institution[]>('/admin/institutions').subscribe({
      next: (i) => {
        this.institutions = i;
        if (!this.id && !this.course.institutionId && i.length) this.course.institutionId = i[0].institutionId;
      }
    });
    if (!this.id) return;
    this.api.get<CourseRow[]>('/admin/courses').subscribe({
      next: (list) => {
        const c = list.find((x) => String(x.course_id) === this.id);
        if (!c) { this.error = 'Course not found.'; return; }
        this.course = {
          courseId: c.course_id,
          institutionId: c.institution_id,
          courseName: c.course_name,
          courseDescription: c.course_description ?? '',
          feesPerSemester: c.fees_per_semester,
          numberOfSemesters: c.number_of_semesters
        };
      },
      error: (e) => (this.error = errorText(e, 'Could not load the course.'))
    });
  }

  save(): void {
    this.saving = true;
    this.api.post('/admin/courses', this.course).subscribe({
      next: () => this.router.navigate(['/', this.section, 'courses']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the course.'); }
    });
  }
}
