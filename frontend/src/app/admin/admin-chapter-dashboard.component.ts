import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { studyStatusBadge, studyStatusLabel } from '../shared/study-status';

interface Counts {
  students: number; studying: number; withoutSponsor: number; sponsors: number; paymentsOverdue: number; openApplications: number;
}
interface StudentRow {
  id: number; user_id: string; name: string; status: string; study_status: string | null;
  sponsor_name: string | null; sponsor_code: string | null; institution_name: string | null; course_name: string | null;
}
interface SponsorRow { id: number; user_id: string; name: string; status: string; student_count: number }
interface DueRow {
  student_code: string; student_name: string; sponsor_code: string | null; sponsor_name: string | null;
  next_due_date: string | null; overdue: number; status: string;
}
interface ApplicationRow { grantee_detail_id: number; name: string; status: string; submitted_at: string | null }
interface Dashboard {
  scoped: boolean;
  chapters?: { chapter_id: number; chapter_name: string; active: boolean }[];
  chapter: { chapter_id: number; chapter_name: string; lead_name: string | null; lead_phone: string | null; lead_email: string | null } | null;
  counts: Counts | null;
  students?: StudentRow[]; sponsors?: SponsorRow[]; dues?: DueRow[]; applications?: ApplicationRow[];
}

type Tab = 'students' | 'sponsors' | 'dues' | 'applications';

/**
 * Admin > Chapter Dashboard: one chapter at a glance. Chapter-scoped users (e.g. a Chapter Lead) always see
 * their own chapter; everyone else picks one.
 */
@Component({
  selector: 'app-admin-chapter-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe],
  styles: [`
    .stat { cursor: pointer; }
    .stat.selected { box-shadow: 0 0 0 2px var(--primary, #3bafda) inset; }
    .stat h2 { margin-bottom: 0; }
  `],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-md-center">
          <h4 class="page-title mb-2 mb-md-0">Chapter Dashboard<ng-container *ngIf="data?.chapter"> · {{ data!.chapter!.chapter_name }}</ng-container></h4>
          <select *ngIf="data && !data.scoped" class="form-control" style="max-width: 280px" [(ngModel)]="chapterId" (ngModelChange)="load()">
            <option [ngValue]="null">Choose a chapter…</option>
            <option *ngFor="let c of data.chapters" [ngValue]="c.chapter_id">{{ c.chapter_name }}{{ c.active ? '' : ' (inactive)' }}</option>
          </select>
        </div>
      </div>
    </div>
    <app-alerts [(error)]="error"></app-alerts>

    <div *ngIf="loading" class="text-center my-5"><span class="spinner-border"></span></div>
    <p *ngIf="!loading && data && !data.chapter" class="text-muted">Choose a chapter to see its students, sponsors, payments and applications.</p>

    <ng-container *ngIf="!loading && data?.chapter && data!.counts as c">
      <p class="text-muted" *ngIf="data!.chapter!.lead_name || data!.chapter!.lead_email">
        <i class="mdi mdi-account-tie mr-1"></i>Chapter lead: {{ data!.chapter!.lead_name || '--' }}
        <span *ngIf="data!.chapter!.lead_phone"> · {{ data!.chapter!.lead_phone }}</span>
        <span *ngIf="data!.chapter!.lead_email"> · {{ data!.chapter!.lead_email }}</span>
      </p>
      <div class="row">
        <div class="col-6 col-md-4 col-xl-2">
          <div class="card-box stat" [class.selected]="tab === 'students' && !onlyUnsponsored" (click)="show('students')">
            <h4 class="header-title mt-0">Students</h4><h2>{{ c.students }}</h2>
            <small class="text-muted">{{ c.studying }} studying</small>
          </div>
        </div>
        <div class="col-6 col-md-4 col-xl-2">
          <div class="card-box stat" [class.selected]="tab === 'students' && onlyUnsponsored" (click)="show('students', true)">
            <h4 class="header-title mt-0">Without sponsor</h4><h2 [class.text-warning]="c.withoutSponsor">{{ c.withoutSponsor }}</h2>
            <small class="text-muted">current students</small>
          </div>
        </div>
        <div class="col-6 col-md-4 col-xl-2">
          <div class="card-box stat" [class.selected]="tab === 'sponsors'" (click)="show('sponsors')">
            <h4 class="header-title mt-0">Sponsors</h4><h2>{{ c.sponsors }}</h2>
            <small class="text-muted">in this chapter</small>
          </div>
        </div>
        <div class="col-6 col-md-4 col-xl-2">
          <div class="card-box stat" [class.selected]="tab === 'dues'" (click)="show('dues')">
            <h4 class="header-title mt-0">Payments overdue</h4><h2 [class.text-danger]="c.paymentsOverdue">{{ c.paymentsOverdue }}</h2>
            <small class="text-muted">{{ data!.dues?.length || 0 }} need attention</small>
          </div>
        </div>
        <div class="col-6 col-md-4 col-xl-2">
          <div class="card-box stat" [class.selected]="tab === 'applications'" (click)="show('applications')">
            <h4 class="header-title mt-0">Open applications</h4><h2>{{ c.openApplications }}</h2>
            <small class="text-muted">not decided yet</small>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
            <h4 class="header-title mb-2 mb-md-0">{{ tabTitle }}</h4>
            <input class="form-control" style="max-width: 260px" placeholder="Search" [(ngModel)]="q" (ngModelChange)="pg.reset()">
          </div>
          <div class="table-responsive">
            <table class="table table-sm table-striped mb-0" *ngIf="tab === 'students'">
              <thead><tr><th>Student</th><th>Study status</th><th>Sponsor</th><th>Institution / course</th></tr></thead>
              <tbody>
                <tr *ngIf="!students.length"><td colspan="4" class="text-center text-muted">No students.</td></tr>
                <tr *ngFor="let s of students | paginate: pg.page : pg.size">
                  <td><strong>{{ s.name }}</strong><div class="small text-muted">{{ s.user_id }}<span *ngIf="s.status === 'Inactive'"> · inactive account</span></div></td>
                  <td><span class="badge" [ngClass]="badge(s.study_status)">{{ label(s.study_status) }}</span></td>
                  <td>{{ s.sponsor_name || '--' }}<div class="small text-muted">{{ s.sponsor_code }}</div></td>
                  <td>{{ s.institution_name || '--' }}<div class="small text-muted">{{ s.course_name }}</div></td>
                </tr>
              </tbody>
            </table>

            <table class="table table-sm table-striped mb-0" *ngIf="tab === 'sponsors'">
              <thead><tr><th>Sponsor</th><th>Students</th></tr></thead>
              <tbody>
                <tr *ngIf="!sponsors.length"><td colspan="2" class="text-center text-muted">No sponsors.</td></tr>
                <tr *ngFor="let s of sponsors | paginate: pg.page : pg.size">
                  <td><strong>{{ s.name }}</strong><div class="small text-muted">{{ s.user_id }}</div></td>
                  <td>{{ s.student_count }}</td>
                </tr>
              </tbody>
            </table>

            <table class="table table-sm table-striped mb-0" *ngIf="tab === 'dues'">
              <thead><tr><th>Student</th><th>Sponsor</th><th>Next due</th><th>Status</th></tr></thead>
              <tbody>
                <tr *ngIf="!dues.length"><td colspan="4" class="text-center text-muted">No payments overdue or due soon.</td></tr>
                <tr *ngFor="let d of dues | paginate: pg.page : pg.size">
                  <td><strong>{{ d.student_name }}</strong><div class="small text-muted">{{ d.student_code }}</div></td>
                  <td>{{ d.sponsor_name || '--' }}<div class="small text-muted">{{ d.sponsor_code }}</div></td>
                  <td>{{ d.next_due_date ? (d.next_due_date | date: 'd MMM yyyy') : '--' }}</td>
                  <td>
                    <span class="badge" [ngClass]="d.status === 'Overdue' ? 'badge-danger' : 'badge-warning'">{{ d.status }}</span>
                    <div class="small text-danger" *ngIf="d.overdue">{{ d.overdue }} overdue</div>
                  </td>
                </tr>
              </tbody>
            </table>

            <table class="table table-sm table-striped mb-0" *ngIf="tab === 'applications'">
              <thead><tr><th>Applicant</th><th>Status</th><th>Submitted</th></tr></thead>
              <tbody>
                <tr *ngIf="!applications.length"><td colspan="3" class="text-center text-muted">No open applications.</td></tr>
                <tr *ngFor="let a of applications | paginate: pg.page : pg.size">
                  <td><strong>{{ a.name }}</strong><div class="small text-muted">#{{ a.grantee_detail_id }}</div></td>
                  <td><span class="badge badge-info text-capitalize">{{ a.status }}</span></td>
                  <td>{{ a.submitted_at ? (a.submitted_at | date: 'd MMM yyyy') : '--' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <app-pager [state]="pg" [total]="tabCount"></app-pager>
        </div>
      </div>
    </ng-container>
  `
})
export class AdminChapterDashboardComponent implements OnInit {
  readonly pg = new PageState();
  data: Dashboard | null = null;
  chapterId: number | null = null;
  tab: Tab = 'students';
  onlyUnsponsored = false;
  q = '';
  loading = false;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<Dashboard>('/admin/chapter-dashboard', this.chapterId ? { chapterId: this.chapterId } : {}).subscribe({
      next: (d) => {
        this.loading = false;
        // The chapter list only comes with a dashboard for unscoped users; keep it when switching chapters.
        this.data = { ...d, chapters: d.chapters ?? this.data?.chapters };
        this.chapterId = d.chapter?.chapter_id ?? this.chapterId;
        this.pg.reset();
      },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the chapter dashboard.'); }
    });
  }

  show(tab: Tab, unsponsored = false): void {
    this.tab = tab;
    this.onlyUnsponsored = unsponsored;
    this.q = '';
    this.pg.reset();
  }

  get tabTitle(): string {
    switch (this.tab) {
      case 'students': return this.onlyUnsponsored ? 'Current students without a sponsor' : 'Students';
      case 'sponsors': return 'Sponsors';
      case 'dues': return 'Payments overdue or due soon';
      default: return 'Open applications';
    }
  }

  get students(): StudentRow[] {
    let rows = this.data?.students ?? [];
    if (this.onlyUnsponsored) {
      rows = rows.filter((s) => !s.sponsor_code && s.status !== 'Inactive'
        && ['STUDYING', 'ON_HOLD'].includes(s.study_status ?? 'STUDYING'));
    }
    return this.match(rows, (s) => [s.name, s.user_id, s.sponsor_name, s.sponsor_code, s.institution_name, s.course_name]);
  }
  get sponsors(): SponsorRow[] { return this.match(this.data?.sponsors ?? [], (s) => [s.name, s.user_id]); }
  get dues(): DueRow[] {
    return this.match(this.data?.dues ?? [], (d) => [d.student_name, d.student_code, d.sponsor_name, d.sponsor_code, d.status]);
  }
  get applications(): ApplicationRow[] { return this.match(this.data?.applications ?? [], (a) => [a.name, a.status]); }

  get tabCount(): number {
    switch (this.tab) {
      case 'students': return this.students.length;
      case 'sponsors': return this.sponsors.length;
      case 'dues': return this.dues.length;
      default: return this.applications.length;
    }
  }

  label(status: string | null): string { return studyStatusLabel(status); }
  badge(status: string | null): string { return studyStatusBadge(status); }

  private match<T>(rows: T[], fields: (r: T) => (string | null | undefined)[]): T[] {
    const q = this.q.trim().toLowerCase();
    return q ? rows.filter((r) => fields(r).some((v) => (v ?? '').toLowerCase().includes(q))) : rows;
  }
}
