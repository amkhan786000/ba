import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';
import { PagerComponent, pageOf } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

interface Grantee {
  user: { id: number; user_id: string; name: string } | null;
  paymentStatus: string;
}

interface SponsorDashboard {
  sponsor: { name: string | null; email: string | null; phone: string | null; chapter_name: string | null };
  grantees: Grantee[];
  performanceByYear: BarDatum[];
  /** Students this sponsor sponsored before (now with another sponsor or finished). */
  pastStudents: { id: number; user_id: string; name: string; sponsored_from: string | null; sponsored_until: string | null; total_paid: number }[];
}

/** One student's study picture and latest news (GET /sponsor/overview). */
interface StudentNews {
  id: number; user_id: string; name: string; study_status: string; course: string | null; institution: string | null;
  semesters: number | null; semester_now: number | null; course_start: string | null; expected_completion: string | null;
  latest_progress: { session: string; year: string; marks: string; review_status: string; uploaded_at: string } | null;
  last_payment: { date: string; amount: number; spent_proof: boolean } | null;
  last_activity: string | null; quiet: boolean;
  /** Past students only. */
  sponsored_from?: string | null; sponsored_until?: string | null;
  now?: { status: string; organisation: string; role: string; city: string } | null;
}
interface Overview {
  impact: { total_given: number; payments: number; students_supported: number; studying_now: number; graduated: number; since_year: number | null };
  students: StudentNews[];
  past: StudentNews[];
}

/** Port of templates/sponsor/dashboard.html (charts use real data instead of the template's sample numbers). */
@Component({
  selector: 'app-sponsor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, BarChartComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box mt-2 d-flex flex-column flex-md-row justify-content-between align-items-md-center">
          <h4 class="page-title mb-2 mb-md-0">Sponsor Overview</h4>
          <div class="d-flex align-items-center">
            <label for="stmtYear" class="mb-0 mr-2 small text-muted">Yearly statement</label>
            <select id="stmtYear" class="form-control form-control-sm mr-2" style="width: auto" [(ngModel)]="statementYear">
              <option *ngFor="let y of statementYears" [ngValue]="y">{{ y }}</option>
            </select>
            <button type="button" class="btn btn-sm btn-outline-primary text-nowrap" (click)="downloadStatement()" [disabled]="downloading">
              <i class="mdi mdi-file-pdf-box mr-1"></i>{{ downloading ? 'Preparing…' : 'Download PDF' }}
            </button>
          </div>
        </div>
      </div>
    </div>
    <app-alerts [(error)]="error"></app-alerts>

    <!-- My impact -->
    <div class="row" *ngIf="overview?.impact as im">
      <div class="col-6 col-xl-3"><div class="card-box">
        <h4 class="header-title mt-0">Given so far</h4><h2>₹{{ im.total_given | number: '1.0-0' }}</h2>
        <small class="text-muted">{{ im.payments }} payment{{ im.payments === 1 ? '' : 's' }}{{ im.since_year ? ' since ' + im.since_year : '' }}</small></div></div>
      <div class="col-6 col-xl-3"><div class="card-box">
        <h4 class="header-title mt-0">Students supported</h4><h2>{{ im.students_supported }}</h2>
        <small class="text-muted">all the years together</small></div></div>
      <div class="col-6 col-xl-3"><div class="card-box">
        <h4 class="header-title mt-0">Studying now</h4><h2>{{ im.studying_now }}</h2>
        <small class="text-muted">currently sponsored by you</small></div></div>
      <div class="col-6 col-xl-3"><div class="card-box">
        <h4 class="header-title mt-0">Graduated</h4><h2>{{ im.graduated }}</h2>
        <small class="text-muted">of the students you supported</small></div></div>
    </div>

    <!-- My students: study picture and latest news -->
    <div class="row" *ngIf="overview?.students?.length">
      <div class="col-12"><h5 class="mt-1 mb-2">My students</h5></div>
      <div class="col-12 col-md-6 col-xl-4" *ngFor="let s of overview!.students">
        <div class="card"><div class="card-body">
          <div class="d-flex justify-content-between align-items-start">
            <div><h5 class="mb-0">{{ s.name }}</h5><small class="text-muted">ID {{ s.user_id }}</small></div>
            <span class="badge" [ngClass]="studyBadge(s.study_status)">{{ s.study_status }}</span>
          </div>
          <p class="mb-1 mt-2"><i class="mdi mdi-school-outline mr-1"></i>{{ s.course || 'Course not set yet' }}<span *ngIf="s.institution" class="text-muted">, {{ s.institution }}</span></p>
          <p class="mb-1 small text-muted" *ngIf="s.semesters">
            <ng-container *ngIf="s.semester_now">Semester {{ s.semester_now }} of {{ s.semesters }} · </ng-container>
            <ng-container *ngIf="s.expected_completion">expected to finish {{ s.expected_completion | date: 'MMM yyyy' }}</ng-container></p>
          <hr class="my-2">
          <p class="mb-1 small"><strong>Latest progress:</strong>
            <ng-container *ngIf="s.latest_progress as lp; else noProgress">{{ lp.year ? 'Year ' + lp.year : '' }}{{ lp.session && lp.session !== '0' ? ', semester ' + lp.session : '' }}
              · marks {{ lp.marks || '—' }} · {{ lp.uploaded_at | date: 'd MMM yyyy' }}</ng-container>
            <ng-template #noProgress><span class="text-muted">none uploaded yet</span></ng-template></p>
          <p class="mb-1 small"><strong>Your last payment:</strong>
            <ng-container *ngIf="s.last_payment as lpay; else noPay">₹{{ lpay.amount | number: '1.0-0' }} on {{ lpay.date | date: 'd MMM yyyy' }} ·
              <span [class.text-success]="lpay.spent_proof" [class.text-muted]="!lpay.spent_proof">{{ lpay.spent_proof ? 'spent proof received' : 'spent proof pending' }}</span></ng-container>
            <ng-template #noPay><span class="text-muted">none yet</span></ng-template></p>
          <p *ngIf="s.quiet" class="mb-0 small text-warning"><i class="mdi mdi-clock-alert-outline mr-1"></i>No news for a while{{ s.last_activity ? ' (last: ' + (s.last_activity | date: 'MMM yyyy') + ')' : '' }}. The office can follow up.</p>
        </div></div>
      </div>
    </div>

    <div class="row" *ngIf="data">
      <div class="col-12 col-lg-4">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title card-header-title">Master Profile</h4>
            <div class="profile-img-container profile-icon" aria-hidden="true"><i class="mdi mdi-account"></i></div>
            <div class="text-center">
              <h5 class="mb-1">{{ data.sponsor.name || 'Name Not Found' }}</h5>
              <p class="text-muted mb-3">{{ data.sponsor.email || 'N/A' }}</p>
            </div>
            <hr>
            <p class="mb-1"><strong>Phone:</strong> {{ data.sponsor.phone || 'N/A' }}</p>
            <p class="mb-1"><strong>Chapter:</strong> {{ data.sponsor.chapter_name || 'N/A' }}</p>
            <div class="alert alert-light border mt-3 mb-0 small">All students sponsored by this account are listed in the beneficiaries table.</div>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title card-header-title">Assigned Beneficiaries</h4>
            <div class="row mb-3">
              <div class="col-12">
                <input type="text" class="form-control" placeholder="Search by student name or ID..." [(ngModel)]="search" (ngModelChange)="page = 1" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-hover table-centered mb-0">
                <thead><tr><th>Student Details</th><th>Status</th><th class="text-right">Action</th></tr></thead>
                <tbody>
                  <tr *ngIf="!data.grantees.length"><td colspan="3" class="text-center py-4 text-muted">No students assigned yet.</td></tr>
                  <tr *ngFor="let g of rows">
                    <td><span class="font-weight-bold d-block text-dark">{{ g.user?.name }}</span><small class="text-muted">ID: {{ g.user?.user_id }}</small></td>
                    <td><span class="badge px-2 py-1" [ngClass]="badge(g.paymentStatus)">{{ g.paymentStatus }}</span></td>
                    <td class="text-right">
                      <a routerLink="/sponsor/payments" [queryParams]="{ granteeId: g.user?.id }" class="btn btn-xs btn-outline-success font-weight-bold">Pay Now</a>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager *ngIf="filtered.length > 5" [total]="filtered.length" [page]="page" [pageSize]="5" (pageChange)="page = $event"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <div class="row" *ngIf="data?.pastStudents?.length">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title card-header-title">Past Students</h4>
            <p class="text-muted small">Students you sponsored before. Their payments and progress reports from your period stay visible to you.</p>
            <div class="table-responsive">
              <table class="table table-sm table-centered mb-0">
                <thead><tr><th>Student</th><th>Sponsored</th><th>Now</th><th class="text-right">You paid</th></tr></thead>
                <tbody>
                  <tr *ngFor="let p of data!.pastStudents">
                    <td><span class="font-weight-bold d-block text-dark">{{ p.name }}</span><small class="text-muted">ID: {{ p.user_id }}</small></td>
                    <td>{{ p.sponsored_from ? (p.sponsored_from | date: 'MMM yyyy') : '…' }} – {{ p.sponsored_until | date: 'MMM yyyy' }}</td>
                    <td><ng-container *ngIf="pastNow(p.id) as n">
                      <span class="badge" [ngClass]="studyBadge(n.study_status)">{{ n.study_status }}</span>
                      <div *ngIf="n.now as w" class="small">{{ w.status }}{{ w.role ? ' · ' + w.role : '' }}{{ w.organisation ? ', ' + w.organisation : '' }}{{ w.city ? ' (' + w.city + ')' : '' }}</div>
                    </ng-container></td>
                    <td class="text-right">₹{{ p.total_paid | number: '1.0-2' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row mt-2" *ngIf="data">
      <div class="col-12 col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-3">Student Performance Trend</h4>
          <app-bar-chart [data]="data.performanceByYear"></app-bar-chart>
          <p class="text-muted mb-0 mt-3 small">Average marks of your students, by academic year</p>
        </div>
      </div>
      <div class="col-12 col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-3">Sponsorship Distribution</h4>
          <app-bar-chart [data]="distribution"></app-bar-chart>
          <p class="text-muted mb-0 mt-3 small">Your students by payment status</p>
        </div>
      </div>
    </div>
  `
})
export class SponsorDashboardComponent implements OnInit {
  data: SponsorDashboard | null = null;
  overview: Overview | null = null;
  distribution: BarDatum[] = [];
  search = '';
  page = 1;
  error = '';

  /** Calendar years for the statement: this year back to five years ago (default: last year). */
  readonly statementYears = Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i);
  statementYear = new Date().getFullYear() - 1;
  downloading = false;

  constructor(private api: ApiService) {}

  downloadStatement(): void {
    this.downloading = true;
    this.api.download('/sponsor/statement', { year: this.statementYear }, `Rahbar_statement_${this.statementYear}.pdf`).subscribe({
      next: () => (this.downloading = false),
      error: (e) => { this.downloading = false; this.error = errorText(e, 'Could not create the statement.'); }
    });
  }

  ngOnInit(): void {
    this.api.get<Overview>('/sponsor/overview').subscribe({ next: (o) => (this.overview = o), error: () => (this.overview = null) });
    this.api.get<SponsorDashboard>('/sponsor/dashboard').subscribe({
      next: (d) => {
        this.data = d;
        const counts = new Map<string, number>();
        for (const g of d.grantees) counts.set(g.paymentStatus, (counts.get(g.paymentStatus) ?? 0) + 1);
        this.distribution = [...counts.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
      },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }

  /** A past student's study status and, for graduates who agreed, what they do now. */
  pastNow(id: number): StudentNews | undefined { return this.overview?.past.find((p) => p.id === id); }

  studyBadge(label: string): string {
    switch (label) {
      case 'Studying': return 'badge-success';
      case 'On hold': return 'badge-warning';
      case 'Graduated': return 'badge-primary';
      default: return 'badge-secondary';
    }
  }

  get filtered(): Grantee[] {
    const t = this.search.toLowerCase();
    return (this.data?.grantees ?? []).filter((g) =>
      [g.user?.name, g.user?.user_id, g.paymentStatus].some((v) => String(v ?? '').toLowerCase().includes(t)));
  }
  get rows(): Grantee[] { return pageOf(this.filtered, this.page, 5); }

  badge(status: string): string {
    if (status === 'Overdue') return 'badge-danger';
    if (status === 'On Schedule' || status === 'Completed') return 'badge-success';
    return 'badge-warning';
  }
}
