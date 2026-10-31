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
              <div class="col-12 col-md-4 mb-2 mb-md-0">
                <input type="text" class="form-control" placeholder="Search by Course or Institution..." [(ngModel)]="search" (ngModelChange)="pg.page = 1" />
              </div>
              <div class="col-12 col-md-4">
                <select class="form-control" [(ngModel)]="institutionId" (ngModelChange)="pg.page = 1" aria-label="Institution">
                  <option value="">All institutions</option>
                  <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }} ({{ courseCount(i.institutionId) }})</option>
                </select>
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>Course Name</th><th>Institution</th><th>Description</th><th>Fees/Sem</th><th>Semesters</th><th>Students</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngFor="let c of visible | paginate: pg.page : pg.size">
                    <td>{{ c.course_name }}</td>
                    <td>{{ c.institution_name }}</td>
                    <td>{{ c.course_description }}</td>
                    <td>{{ c.fees_per_semester }}</td>
                    <td>{{ c.number_of_semesters }}</td>
                    <td><ng-container *ngTemplateOutlet="count; context: { n: courseStudents(c.course_id), open: openCourse.bind(this, c) }"></ng-container></td>
                    <td class="text-nowrap">
                      <a *ngIf="canEdit" [routerLink]="['/', section, 'courses', c.course_id, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button *ngIf="canEdit && courseStudents(c.course_id)" type="button" class="btn btn-sm btn-outline-primary waves-effect ml-1"
                              (click)="openMove(c)" title="Move all its students to another course (e.g. a duplicate spelling)">
                        <i class="mdi mdi-swap-horizontal"></i> Move students</button>
                      <!-- greyed out while students are on the course (the server refuses too); the wrapper shows why on hover -->
                      <span *ngIf="canEdit" class="d-inline-block ml-1" [title]="courseBlocker(c.course_id) || 'Delete this course'">
                        <button type="button" class="btn btn-sm btn-danger waves-effect" (click)="deleteCourse(c)" [disabled]="!!courseBlocker(c.course_id)"
                                [style.pointer-events]="courseBlocker(c.course_id) ? 'none' : null">Delete</button></span>
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
                <thead><tr><th>ID</th><th>Institution</th><th>Address</th><th>Contact</th><th>Courses</th><th>Students</th><th *ngIf="canEdit">Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!institutions.length"><td colspan="7" class="text-center text-muted">No institutions yet.</td></tr>
                  <tr *ngFor="let i of institutions | paginate: ipg.page : ipg.size">
                    <td>{{ i.institutionId }}</td>
                    <td>{{ i.institutionName }}</td>
                    <td>{{ i.address }}</td>
                    <td>{{ i.contactNumber }}<div class="small text-muted">{{ i.email }}</div></td>
                    <td>{{ courseCount(i.institutionId) }}</td>
                    <td><ng-container *ngTemplateOutlet="count; context: { n: institutionStudents(i.institutionId), open: openInstitution.bind(this, i) }"></ng-container></td>
                    <td *ngIf="canEdit" class="text-nowrap">
                      <a [routerLink]="['/', section, 'institutions', i.institutionId, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <!-- greyed out while the institution has courses or students (the server refuses too) -->
                      <span class="d-inline-block ml-1" [title]="institutionBlocker(i.institutionId) || 'Delete this institution'">
                        <button type="button" class="btn btn-sm btn-danger waves-effect" (click)="deleteInstitution(i)" [disabled]="!!institutionBlocker(i.institutionId)"
                                [style.pointer-events]="institutionBlocker(i.institutionId) ? 'none' : null">Delete</button></span>
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

    <!-- a student count: a button that lists the students (with Students view), else just the number -->
    <ng-template #count let-n="n" let-open="open">
      <button *ngIf="n && canSeeStudents; else plain" type="button" class="btn btn-xs btn-light border" (click)="open()"
              [title]="'See the ' + n + ' student(s)'"><i class="mdi mdi-account-multiple mr-1"></i>{{ n }}</button>
      <ng-template #plain><span [class.text-muted]="!n">{{ n }}</span></ng-template>
    </ng-template>

    <!-- Move students: every student on one course to another, in one click -->
    <div *ngIf="move" class="modal fade show d-block" tabindex="-1" (click)="move = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()"><div class="modal-content">
        <div class="modal-header"><h5 class="modal-title">Move students</h5>
          <button type="button" class="close" (click)="move = null" aria-label="Close">&times;</button></div>
        <div class="modal-body">
          <p>Move all <strong>{{ courseStudents(move.from.course_id) }}</strong> student(s) of
            <strong>{{ move.from.course_name }}</strong> <span class="text-muted">({{ move.from.institution_name }}, {{ move.from.number_of_semesters }} semesters)</span> to:</p>
          <select class="form-control mb-2" [(ngModel)]="move.toId" aria-label="Move to course">
            <option [ngValue]="null">-- Choose the course --</option>
            <option *ngFor="let c of moveTargets" [ngValue]="c.course_id">{{ c.course_name }} ({{ c.institution_name }}) · {{ c.number_of_semesters }} sem · {{ courseStudents(c.course_id) }} students</option>
          </select>
          <div *ngIf="moveTo && moveTo.number_of_semesters !== move.from.number_of_semesters" class="alert alert-warning small mb-2">
            <i class="mdi mdi-alert"></i> {{ moveTo.course_name }} has {{ moveTo.number_of_semesters }} semesters, not {{ move.from.number_of_semesters }}:
            the moved students' unpaid installments follow the new length (paid ones stay).</div>
          <div *ngIf="moveTo && moveTo.institution_id !== move.from.institution_id" class="alert alert-light border small mb-2">
            The students' institution changes to {{ moveTo.institution_name }} too.</div>
          <div class="custom-control custom-checkbox">
            <input type="checkbox" class="custom-control-input" id="moveDelete" [(ngModel)]="move.deleteFrom">
            <label class="custom-control-label" for="moveDelete">Then delete <strong>{{ move.from.course_name }}</strong> (it will have no students)</label>
          </div>
        </div>
        <div class="modal-footer"><button type="button" class="btn btn-light" (click)="move = null">Cancel</button>
          <button type="button" class="btn btn-primary" (click)="doMove()" [disabled]="busy || !move.toId">
            Move {{ courseStudents(move.from.course_id) }} student(s)</button></div>
      </div></div>
    </div>

    <div *ngIf="list" class="modal fade show d-block" tabindex="-1" (click)="list = null">
      <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()"><div class="modal-content">
        <div class="modal-header"><h5 class="modal-title">{{ list.title }} <small class="text-muted">· {{ list.rows?.length ?? '…' }} student(s)</small></h5>
          <button type="button" class="close" (click)="list = null" aria-label="Close">&times;</button></div>
        <div class="modal-body">
          <p *ngIf="!list.rows" class="text-muted mb-0">Loading…</p>
          <div class="table-responsive" *ngIf="list.rows"><table class="table table-sm mb-0">
            <thead class="thead-light"><tr><th>Student</th><th *ngIf="list.byInstitution">Course</th><th>Chapter</th><th>Session</th><th>Study status</th></tr></thead>
            <tbody><tr *ngFor="let s of list.rows">
              <td>{{ s['name'] }}<div class="small text-muted">{{ s['user_id'] }}</div></td>
              <td *ngIf="list.byInstitution">{{ s['course_name'] || '-' }}</td>
              <td>{{ s['chapter_name'] || '-' }}</td><td>{{ s['year'] || '-' }}</td><td>{{ s['study_status'] }}</td>
            </tr></tbody>
          </table></div>
        </div>
        <div class="modal-footer"><button type="button" class="btn btn-light" (click)="list = null">Close</button></div>
      </div></div>
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
  /** Course List: only this institution's courses ('' = all). */
  institutionId = '';
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('COURSES', 'EDIT'); }
  /** The names of the students need Students view; the counts don't. */
  get canSeeStudents(): boolean { return this.auth.can('STUDENTS', 'VIEW'); }

  /** How many students are on each course / at each institution (only those the user may see). */
  counts: { courses: Record<string, number>; institutions: Record<string, number> } = { courses: {}, institutions: {} };
  courseStudents(courseId: number): number { return this.counts.courses[String(courseId)] ?? 0; }
  institutionStudents(institutionId: string): number { return this.counts.institutions[institutionId] ?? 0; }

  /** Why the course can't be deleted yet ('' when it can): students are on it. */
  courseBlocker(courseId: number): string {
    const n = this.courseStudents(courseId);
    return n ? "Can't delete: " + n + ' student(s) are on this course. Move them to another course first.' : '';
  }

  /** Why the institution can't be deleted yet ('' when it can): it still has courses or students. */
  institutionBlocker(institutionId: string): string {
    const courses = this.courseCount(institutionId), students = this.institutionStudents(institutionId);
    if (!courses && !students) return '';
    return "Can't delete: it has " + courses + ' course(s)' + (students ? ' and ' + students + ' student(s)' : '') + '. Delete its courses first.';
  }

  /** Move students: from this course, to the chosen one, and whether to delete it afterwards. */
  move: { from: CourseRow; toId: number | null; deleteFrom: boolean } | null = null;
  busy = false;

  openMove(c: CourseRow): void { this.move = { from: c, toId: null, deleteFrom: true }; }

  /** Every other course, the same institution's first, then by name. */
  get moveTargets(): CourseRow[] {
    const from = this.move?.from;
    if (!from) return [];
    return this.courses.filter((c) => c.course_id !== from.course_id).sort((a, b) =>
      Number(b.institution_id === from.institution_id) - Number(a.institution_id === from.institution_id)
      || (a.institution_name ?? '').localeCompare(b.institution_name ?? '') || a.course_name.localeCompare(b.course_name));
  }

  get moveTo(): CourseRow | undefined { return this.courses.find((c) => c.course_id === this.move?.toId); }

  doMove(): void {
    const m = this.move, to = this.moveTo;
    if (!m || !to || !confirm(`Move all ${this.courseStudents(m.from.course_id)} student(s) of "${m.from.course_name}" to "${to.course_name}" (${to.institution_name})?`
        + (m.deleteFrom ? `\n"${m.from.course_name}" will then be deleted.` : ''))) return;
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/courses/${m.from.course_id}/move-students`, { toCourseId: to.course_id, deleteFrom: m.deleteFrom }).subscribe({
      next: (r) => { this.busy = false; this.move = null; this.message = r.message; this.error = ''; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not move the students.'); }
    });
  }

  /** The students list being shown. */
  list: { title: string; byInstitution: boolean; rows: Record<string, unknown>[] | null } | null = null;

  openCourse(c: CourseRow): void {
    const list = { title: `${c.course_name} (${c.institution_name})`, byInstitution: false, rows: null as Record<string, unknown>[] | null };
    this.list = list;
    this.api.get<Record<string, unknown>[]>(`/admin/courses/${c.course_id}/students`).subscribe({
      next: (r) => (list.rows = r), error: (e) => { this.list = null; this.error = errorText(e, 'Could not load the students.'); }
    });
  }

  openInstitution(i: Institution): void {
    const list = { title: i.institutionName, byInstitution: true, rows: null as Record<string, unknown>[] | null };
    this.list = list;
    this.api.get<Record<string, unknown>[]>(`/admin/institutions/${encodeURIComponent(i.institutionId)}/students`).subscribe({
      next: (r) => (list.rows = r), error: (e) => { this.list = null; this.error = errorText(e, 'Could not load the students.'); }
    });
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<CourseRow[]>('/admin/courses').subscribe({
      next: (c) => (this.courses = c),
      error: (e) => (this.error = errorText(e, 'Could not load courses.'))
    });
    this.api.get<typeof this.counts>('/admin/courses/student-counts').subscribe({ next: (c) => (this.counts = c) });
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
    return this.courses.filter((c) => !this.institutionId || c.institution_id === this.institutionId)
      .filter((c) => [c.course_name, c.institution_name].some((v) => (v ?? '').toLowerCase().includes(f)));
  }
}
